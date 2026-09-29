package org.villageastra.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** QUEST-003 (AD-106): a companion follows its player on its own legs — never a catch-up teleport. It waits, saying why, when the player is
 *  away, in another world, too far, beyond any way it can walk or across water too wide to swim; it takes a free seat in the player's boat
 *  or an empty boat it can reach and rows, lands on a bank that leads to the player, walks into the portal the player went through, and says
 *  when it is hurt or attacked. Every decision comes from the route itself and from real movement, not from whether a path object came back. */
public final class EscortGoal extends Goal {
    private static final double NEAR=3,NEAR_DANGER=1.5,START_EXTRA=1.5,FAR=48,RESUME=40,LAND=4,BOARD_OWNER=3,BOARD_EMPTY=2.5,BOAT_SEARCH=16;
    /** More water than this in a row on the way is a crossing for a boat, not a swim. */
    public static final int SWIM_LIMIT=8;
    /** How long after the player went through a portal the companion may still follow them through it. */
    public static final int CROSS_TIME=600;
    /** At or below this share of its health a companion is wounded: it slows down and its player is told. */
    public static final float WOUNDED=0.5F;
    private static final int REPLAN=20,RETRY=100,STRIKES=3,STALLS=3,CHECK=20,OWNER_CHECK=200,FLEE_EVERY=40,WARN_GAP=200,BOAT_RETRY=200,NOTICE_HOLD=20,NOTICE_GAP=40;
    private static final double SPEED=1.0,WOUNDED_SPEED=0.7,DANGER_SPEED=1.2;
    /** Keeps the ground around a companion ticking while it walks to a portal its player already went through; it expires on its own. */
    private static final TicketType<UUID> TICKET=TicketType.create("villageastra_escort",UUID::compareTo,60);
    /** GameTest seam: a FakePlayer is never in the PlayerList, so a test names its owner here. Always empty in play. */
    public static final Map<UUID,ServerPlayer> TEST_OWNERS=new ConcurrentHashMap<>();

    private final ResidentEntity r;
    private int strikes,stall,stuckStrikes,boatStall,crossStrikes,boatWalkStrikes,nearSamples;
    private long nextPlan,deflectUntil,boatWaitUntil,lastFlee=-FLEE_EVERY,warnedAt=-WARN_GAP,pendingSince,lastNotice=-NOTICE_GAP,boardFailSince=-1,landedAt=-BOAT_RETRY;
    private float deflect;
    private double lastBoatDist=Double.MAX_VALUE;
    private boolean boatNeeded,settledNear;
    private Vec3 lastPos,boatLast,releaseTarget;
    private BlockPos waitAnchor,lastLanding;
    private Boat targetBoat;
    /** Boats it found it could not reach or board, and when: not tried again for a while. */
    private final Map<UUID,Long> givenUp=new HashMap<>();
    private final Set<UUID> named=new HashSet<>();
    private String shown="",pending="";

    public EscortGoal(ResidentEntity resident) { this.r = resident; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    /** Following somebody, or still bringing a boat to land after it stopped following in the middle of the water. */
    @Override public boolean canUse() { return r.escortPlayer() != null || releasing(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { reset(); shown = r.escortState() + "/" + r.escortReason(); }
    @Override public void stop() { r.getNavigation().stop(); reset(); }
    private void reset() {
        strikes = stall = stuckStrikes = boatStall = crossStrikes = boatWalkStrikes = nearSamples = 0; nextPlan = deflectUntil = boatWaitUntil = 0; boardFailSince = -1;
        lastPos = boatLast = null; waitAnchor = null; targetBoat = null; pending = ""; boatNeeded = false; settledNear=false; lastBoatDist = Double.MAX_VALUE;
    }
    private boolean releasing() { return r.releasing() && r.getVehicle() instanceof Boat b && b.isInWater() && b.getFirstPassenger() == r; }

    /** The player a companion follows: from the server's players, or the one a GameTest named. */
    public static ServerPlayer owner(MinecraftServer s, UUID id) {
        if (id == null) return null;
        var p = s.getPlayerList().getPlayer(id);
        return p != null ? p : TEST_OWNERS.get(id);
    }
    public static void hold(ResidentEntity r) { if (r.level() instanceof ServerLevel l) l.getChunkSource().addRegionTicket(TICKET, r.chunkPosition(), 3, r.getUUID()); }
    /** The nearest empty boat afloat within this range, or null. */
    public static Boat freeBoat(ResidentEntity r, double range) { return freeBoat(r, range, Set.of()); }
    private static Boat freeBoat(ResidentEntity r, double range, Set<UUID> skip) {
        Boat best = null; double bestDistance = range * range;
        for (var b : r.level().getEntitiesOfClass(Boat.class, r.getBoundingBox().inflate(range), b -> b.isAlive() && b.getPassengers().isEmpty() && !skip.contains(b.getUUID())
                && (b.isInWater() || r.level().getFluidState(b.blockPosition()).is(FluidTags.WATER) || r.level().getFluidState(b.blockPosition().below()).is(FluidTags.WATER)))) {
            double d = b.distanceToSqr(r); if (d < bestDistance) { bestDistance = d; best = b; }
        }
        return best;
    }
    /** On the water: in a boat, swimming, or standing in water (a player the server does not tick keeps its old flags). */
    public static boolean onWater(Player o) { return o.getVehicle() instanceof Boat || o.isInWater() || o.level().getFluidState(o.blockPosition()).is(FluidTags.WATER); }
    private static boolean ownerAfloat(Player o) { return o.getVehicle() instanceof Boat ob && ob.isInWater(); }

    @Override public void tick() {
        if (!(r.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime(); var nav = r.getNavigation();
        // Nobody followed any more but still in a boat on the water: it rows to the nearest land and steps out there, never left mid-lake.
        if (r.escortPlayer() == null) { if (releasing()) bringAshore(now); return; }
        // The quest decides whom its people follow: a player who gave the quest up is not followed any more (AD-105), online or not.
        if (r.tickCount % OWNER_CHECK == 0 && r.getPersistentData().hasUUID(Camps.QUEST_TAG)
                && !Objects.equals(Quests.ownerOf(l, r.getPersistentData().getUUID(Camps.QUEST_TAG)), r.escortPlayer())) { r.escort(null); return; }
        var o = owner(l.getServer(), r.escortPlayer());
        // A player leaving through the End's exit fountain is removed while the credits roll: that is being away, not being here.
        boolean here = o != null && !o.isRemoved() && o.level() == l;
        boolean wounded = r.getHealth() <= r.getMaxHealth() * WOUNDED;
        if (wounded && !r.escortWarned() && o != null) {
            o.displayClientMessage(Component.translatable("quest.villageastra.escort.wounded_warn", r.getDisplayName(), (int) Math.ceil(r.getHealth()), (int) r.getMaxHealth()), false);
            r.escortWarned(true);
        }
        if (!wounded && r.escortWarned()) r.escortWarned(false);
        LivingEntity attacker = r.getLastHurtByMob();
        if (attacker != null && (!attacker.isAlive() || attacker == o)) attacker = null;
        if (attacker == null) named.clear();
        else if (here && (now - warnedAt >= WARN_GAP || !named.contains(attacker.getUUID()) && now - warnedAt >= WARN_GAP / 4)) {
            o.displayClientMessage(Component.translatable("quest.villageastra.escort.attacked", r.getDisplayName(), attacker.getDisplayName(),
                    Adventures.away(o.blockPosition(), r.blockPosition()), Component.translatable("quest.villageastra.side." + Adventures.bearing(o.blockPosition(), r.blockPosition()))), false);
            named.add(attacker.getUUID()); warnedAt = now;
        }
        // Told to wait: it stays put until called, only stepping out of the way of a blow.
        if (r.ordered()) { if (attacker == null) nav.stop(); flee(attacker, now); notice(o, now); return; }
        // Only a boat is the companion's own way to travel: from anything else it gets down, unless its player rides the same thing.
        if (r.getVehicle() != null && !(r.getVehicle() instanceof Boat) && (o == null || o.getVehicle() != r.getVehicle())) r.stopRiding();
        // The portal its player went through: it walks in while its ground is kept ticking behind the player.
        var cell = r.portalCell();
        if (cell != null) {
            if (!cell.dimension().equals(l.dimension()) || here || now >= r.portalUntil()) r.clearPortal();
            else {
                if (r.tickCount % 10 == 0) { hold(r); l.resetEmptyTime(); }
                // Back through a portal it came by a moment ago: the vanilla cooldown of the last crossing must not hold it.
                r.setPortalCooldown(0);
                r.setEscort("dimension", "");
                if (r.getVehicle() instanceof Boat b && !dryDismount(r, b, Vec3.atCenterOf(cell.pos()))) r.stopRiding();
                if (now >= nextPlan && canPlan()) {
                    var p = nav.createPath(cell.pos(), 0);
                    if (p != null && p.canReach()) { nav.moveTo(p, SPEED); crossStrikes = 0; }
                    else if (++crossStrikes >= 3) { r.clearPortal(); crossStrikes = 0; }
                    nextPlan = now + REPLAN;
                }
                notice(o, now); return;
            }
        }
        if (o == null || o.isRemoved()) { waitFor("offline", attacker, now, o); return; }
        if (!here) { waitFor("other_dimension", attacker, now, o); return; }
        double dist = r.distanceTo(o);
        if (dist > FAR || "too_far".equals(r.escortReason()) && dist > RESUME) { waitFor("too_far", attacker, now, o); return; }
        r.getLookControl().setLookAt(o, 30, 30);
        if (r.getVehicle() instanceof Boat b) { inBoat(o, b, now, dist); notice(o, now); return; }
        if (o.getVehicle() instanceof Boat b && b.getPassengers().size() < (b instanceof ChestBoat ? 1 : 2) && r.distanceTo(b) <= BOARD_OWNER && r.startRiding(b)) {
            nav.stop(); r.setEscort("boat", ""); boatNeeded = false; notice(o, now); return;
        }
        double speed = wounded ? (attacker != null ? 0.9 : WOUNDED_SPEED) : (attacker != null ? DANGER_SPEED : SPEED);
        double stopAt = attacker != null ? NEAR_DANGER : NEAR;
        // Hysteresis belongs to a companion that already arrived, not an unfinished descent or initial approach.
        if (dist <= stopAt || settledNear && !nav.isInProgress() && dist < stopAt + START_EXTRA) {
            // Movement can carry a first near sample back outside the radius before the tick ends.
            // Only a sustained arrival earns hysteresis; a transient crossing must resume its approach.
            if(dist<=stopAt){if(++nearSamples>=2)settledNear=true;}else nearSamples=0;
            nav.stop(); strikes = 0; stall = 0; stuckStrikes = 0; targetBoat = null; r.setEscort(wounded ? "wounded" : "following", ""); notice(o, now); return;
        }
        settledNear=false;nearSamples=0;
        // A boat is looked for only when one is really needed: the player rows away, or the way to them is water too wide to swim.
        boolean needBoat = ownerAfloat(o) || boatNeeded;
        if (!needBoat) targetBoat = null;
        else if (targetBoat == null && r.tickCount % CHECK == 0) pickBoat(now);
        if (targetBoat != null && goToBoat(o, now, speed, wounded)) { notice(o, now); return; }
        String reason = r.escortReason();
        boolean anchorMoved = ("no_path".equals(reason) || "needs_boat".equals(reason)) && waitAnchor != null && waitAnchor.distManhattan(o.blockPosition()) > 3;
        if (anchorMoved) waitAnchor = o.blockPosition();
        boolean due = now >= nextPlan || anchorMoved;
        var current = nav.getPath();
        boolean stale = current == null || nav.isDone() || current.getTarget().distManhattan(o.blockPosition()) > 2;
        if (due && stale && canPlan()) plan(o, now, speed, wounded);
        progress(dist, stopAt, now, o);
        if (nav.isInProgress()) nav.setSpeedModifier(speed);
        notice(o, now);
    }

    /** A route is planned only with the feet on something: in the air the navigation returns nothing, which is no reason to wait. */
    private boolean canPlan() { return r.onGround() || r.isInWaterOrBubble() || r.isInLava() || r.getVehicle() instanceof Boat; }

    /** The nearest empty boat it can really walk to and has not given up on; none when none is. */
    private void pickBoat(long now) {
        givenUp.values().removeIf(at -> now - at > BOAT_RETRY);
        var boat = freeBoat(r, BOAT_SEARCH, givenUp.keySet());
        if (boat == null) return;
        Path p = r.routeTo(boat.blockPosition(), 1);
        boolean reach = p != null && (p.canReach() || p.getEndNode() != null && Vec3.atBottomCenterOf(p.getEndNode().asBlockPos()).distanceTo(boat.position()) <= BOARD_EMPTY);
        if (reach) { targetBoat = boat; lastBoatDist = Double.MAX_VALUE; boatWalkStrikes = 0; boardFailSince = -1; }
        else givenUp.put(boat.getUUID(), now);
    }
    /** Walks to the chosen boat and gets in; a boat it gets no nearer to, or that will not take it, is given up for a while. */
    private boolean goToBoat(ServerPlayer o, long now, double speed, boolean wounded) {
        var nav = r.getNavigation(); var boat = targetBoat;
        if (!boat.isAlive() || !boat.getPassengers().isEmpty()) { targetBoat = null; return false; }
        double d = r.distanceTo(boat);
        if (d <= BOARD_EMPTY) {
            if (r.startRiding(boat)) { nav.stop(); r.setEscort("boat", ""); targetBoat = null; boatNeeded = false; return true; }
            // Still on the cooldown of its last landing, or the boat will not take it: it waits beside it, and gives it up after a while.
            if (boardFailSince < 0) boardFailSince = now;
            if (now - boardFailSince > BOAT_RETRY) { givenUp.put(boat.getUUID(), now); targetBoat = null; return false; }
            nav.stop(); r.setEscort(wounded ? "wounded" : "following", ""); return true;
        }
        if (now >= nextPlan && canPlan()) { nav.moveTo(boat, speed); nextPlan = now + REPLAN; }
        if (r.tickCount % CHECK == 0) {
            boatWalkStrikes = d > lastBoatDist - 0.5 ? boatWalkStrikes + 1 : 0; lastBoatDist = d;
            if (boatWalkStrikes >= STRIKES) { givenUp.put(boat.getUUID(), now); targetBoat = null; boatWalkStrikes = 0; return false; }
        }
        r.setEscort(wounded ? "wounded" : "following", ""); return true;
    }

    private void plan(ServerPlayer o, long now, double speed, boolean wounded) {
        var nav = r.getNavigation(); var path = nav.createPath(o, 1);
        if (path == null) { strike(o, now); return; }
        int run = 0, longest = 0;
        for (int i = 0; i < path.getNodeCount(); i++) { run = r.level().getFluidState(path.getNode(i).asBlockPos()).is(FluidTags.WATER) ? run + 1 : 0; longest = Math.max(longest, run); }
        var end = path.getEndNode();
        boolean wetEnd = !path.canReach() && end != null && r.level().getFluidState(end.asBlockPos()).is(FluidTags.WATER);
        if (!r.isPassenger() && (longest > SWIM_LIMIT || wetEnd)) {
            boatNeeded = true; pickBoat(now);
            if (targetBoat != null) { nextPlan = now + REPLAN; return; }
            nav.stop(); r.setEscort("waiting", "needs_boat"); waitAnchor = o.blockPosition(); nextPlan = now + RETRY; return;
        }
        boatNeeded = false;
        double mine = r.distanceTo(o), left = end == null ? mine : Math.sqrt(end.asBlockPos().distToCenterSqr(o.position()));
        if (path.canReach() || left <= NEAR + 1 || left <= mine - 2) {
            nav.moveTo(path, speed); strikes = 0; r.setEscort(wounded ? "wounded" : "following", ""); nextPlan = now + REPLAN;
        } else strike(o, now);
    }
    /** Three plans in a row that bring it no nearer are a wait with no way, retried now and then and at once when the player moves. */
    private void strike(ServerPlayer o, long now) {
        strikes++; nextPlan = now + REPLAN;
        if (strikes >= STRIKES) { noWay(o, now); strikes = 0; }
    }
    private void noWay(ServerPlayer o, long now) { r.getNavigation().stop(); r.setEscort("waiting", "no_path"); waitAnchor = o.blockPosition(); nextPlan = now + RETRY; }
    /** Real movement, measured every CHECK ticks: a companion meant to walk that does not move is not following, whatever the path says —
     *  and a new path that looks fine does not wipe that out. */
    private void progress(double dist, double stopAt, long now, ServerPlayer o) {
        if (r.tickCount % CHECK != 0) return;
        var nav = r.getNavigation();
        if (nav.isInProgress() && dist > stopAt && lastPos != null) {
            double moved = r.position().distanceTo(lastPos), need = r.isInWaterOrBubble() ? 0.3 : 1.0;
            if (moved >= need) { stall = 0; stuckStrikes = 0; }
            else if (++stall >= STALLS || nav.isStuck()) {
                nav.stop(); stall = 0;
                if (++stuckStrikes >= STRIKES) { noWay(o, now); stuckStrikes = 0; } else nextPlan = now + REPLAN;
            }
        }
        lastPos = r.position();
    }
    private void waitFor(String reason, LivingEntity attacker, long now, ServerPlayer o) {
        if (attacker == null) r.getNavigation().stop();
        r.setEscort("waiting", reason); flee(attacker, now); notice(o, now);
    }
    private void flee(LivingEntity attacker, long now) {
        if (attacker == null || now - lastFlee < FLEE_EVERY) return;
        lastFlee = now; var away = DefaultRandomPos.getPosAway(r, 8, 4, attacker.position());
        if (away != null) r.getNavigation().moveTo(away.x, away.y, away.z, DANGER_SPEED);
    }

    private void inBoat(ServerPlayer o, Boat b, long now, double dist) {
        r.getNavigation().stop();
        // Never steers a boat its player is in.
        if (o.getVehicle() == b) { r.setEscort("boat", ""); return; }
        if (!b.isInWater()) { if (!dryDismount(r, b, o.position())) r.stopRiding(); return; }
        // The player is ashore: near them, or at a bank that really leads to them, it steps out on the land side.
        if (!onWater(o) && (b.distanceTo(o) <= LAND || b.horizontalCollision && shoreLeadsTo(o, b, now)) && dryDismount(r, b, o.position())) {
            lastLanding = r.blockPosition(); landedAt = now; return;
        }
        if (b.getFirstPassenger() != r || b.getPassengers().stream().anyMatch(x -> x instanceof Player)) { r.setEscort("boat", ""); return; }
        if (r.tickCount % CHECK == 0) {
            double moved = boatLast == null ? 1 : b.position().distanceTo(boatLast);
            if (moved < 0.3 && dist > LAND) {
                boatStall++;
                if (boatStall == 2) { deflect = 60; deflectUntil = now + 40; }
                else if (boatStall == 4) { deflect = -60; deflectUntil = now + 40; }
                else if (boatStall >= 6 && !"boat_stuck".equals(r.escortReason())) { r.setEscort("waiting", "boat_stuck"); waitAnchor = o.blockPosition(); boatWaitUntil = now + BOAT_RETRY; }
            } else boatStall = 0;
            boatLast = b.position();
        }
        if ("boat_stuck".equals(r.escortReason())) {
            if (now >= boatWaitUntil || waitAnchor != null && waitAnchor.distManhattan(o.blockPosition()) > 4) { boatStall = 0; r.setEscort("boat", ""); }
            else return;
        }
        row(b, o.position(), now, dist > NEAR);
        r.setEscort("boat", "");
    }
    /** A bank it bumps into is a place to land only if the way on from there leads to the player without another long stretch of water —
     *  not an island or a spit of its own shore — and not the same bank it just left. Read along the straight line to the player, at the
     *  height of the water and of the land beside it. */
    private boolean shoreLeadsTo(ServerPlayer o, Boat b, long now) {
        if (lastLanding != null && now - landedAt < BOAT_RETRY && lastLanding.distManhattan(b.blockPosition()) <= 4) return false;
        var level = r.level(); Vec3 from = b.position(), to = o.position(); double length = from.distanceTo(to);
        boolean ashore = false; int run = 0;
        for (double step = 0; step <= length; step += 1) {
            var at = BlockPos.containing(from.add(to.subtract(from).scale(step / Math.max(1.0E-6, length))));
            boolean water = level.getFluidState(at).is(FluidTags.WATER) || level.getFluidState(at.below()).is(FluidTags.WATER);
            if (!ashore) { ashore = !water; continue; }
            run = water ? run + 1 : 0;
            if (run > SWIM_LIMIT) return false;
        }
        return ashore;
    }
    private void row(Boat b, Vec3 to, long now, boolean push) {
        float want = yawTo(b, to) + (now < deflectUntil ? deflect : 0);
        float yaw = Mth.approachDegrees(b.getYRot(), want, 8F); b.setYRot(yaw);
        double force = push ? (Math.abs(Mth.degreesDifference(yaw, want)) < 45 ? 0.04 : 0.005) : 0, rad = Math.toRadians(yaw);
        b.setDeltaMovement(b.getDeltaMovement().add(-Math.sin(rad) * force, 0, Math.cos(rad) * force));
        b.setPaddleState(force > 0.02, force > 0.02);
    }
    /** Nobody to follow, still afloat: rows to the nearest land it can find and steps out on the first dry bank. */
    private void bringAshore(long now) {
        var b = (Boat) r.getVehicle();
        if (dryDismount(r, b, releaseTarget != null ? releaseTarget : b.position().add(1, 0, 0))) { r.releasing(false); releaseTarget = null; return; }
        if (releaseTarget == null || r.tickCount % BOAT_RETRY == 0) releaseTarget = LandRandomPos.getPos(r, 32, 7);
        if (releaseTarget != null) row(b, releaseTarget, now, true);
    }
    private static float yawTo(Entity from, Vec3 to) { double dx = to.x - from.getX(), dz = to.z - from.getZ(); return (float) (Math.atan2(-dx, dz) * 180 / Math.PI); }
    /** Steps out of a boat on dry ground, turning toward a place and then round until the boat offers a spot on land; false when there is none. */
    public static boolean dryDismount(ResidentEntity r, Boat b, Vec3 toward) {
        float base = yawTo(b, toward);
        for (float off : new float[]{0, 45, -45, 90, -90, 135, -135, 180}) {
            r.setYRot(base + off); var v = b.getDismountLocationForPassenger(r);
            double h = Math.sqrt((v.x - b.getX()) * (v.x - b.getX()) + (v.z - b.getZ()) * (v.z - b.getZ()));
            if (h > 0.5 && !r.level().getFluidState(BlockPos.containing(v)).is(FluidTags.WATER)) {
                r.setYBodyRot(base + off); r.setYHeadRot(base + off); r.stopRiding(); return true;
            }
        }
        return false;
    }
    /** Tells its player what it does now and why, once the new state has held for a moment — never a flicker every tick. */
    private void notice(ServerPlayer o, long now) {
        String cur = r.escortState() + "/" + r.escortReason();
        if (cur.equals(shown)) { pending = ""; return; }
        if (!cur.equals(pending)) { pending = cur; pendingSince = now; return; }
        if (now - pendingSince < NOTICE_HOLD || now - lastNotice < NOTICE_GAP || o == null || o.isRemoved()) return;
        o.displayClientMessage(Component.translatable("quest.villageastra.escort.notice", r.getDisplayName(), Component.translatable("quest.villageastra.escort.state." + r.escortState()),
                r.escortReason().isEmpty() ? Component.empty() : Component.translatable("quest.villageastra.escort.reason." + r.escortReason())), true);
        shown = cur; lastNotice = now;
    }
}

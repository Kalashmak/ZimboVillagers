package org.villageastra.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.villageastra.domain.Resident;
import org.villageastra.server.SettlementData;
import java.util.UUID;

public final class ResidentEntity extends PathfinderMob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN=net.minecraft.network.syncher.SynchedEntityData.defineId(ResidentEntity.class,net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CHILD=net.minecraft.network.syncher.SynchedEntityData.defineId(ResidentEntity.class,net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.item.ItemStack> WORK_ITEM=net.minecraft.network.syncher.SynchedEntityData.defineId(ResidentEntity.class,net.minecraft.network.syncher.EntityDataSerializers.ITEM_STACK);
    @Override protected void defineSynchedData() { super.defineSynchedData();entityData.define(SKIN,0);entityData.define(CHILD,false);entityData.define(WORK_ITEM,net.minecraft.world.item.ItemStack.EMPTY); }
    /** Rendering only: canonical tool/cargo stays in the work journal, never in equipment/drop slots. The armour slots hold gifted armour
     *  only (AD-128): GiftLedger keeps the canonical pieces, the body mirrors them and never drops them. */
    public net.minecraft.world.item.ItemStack displayedWorkItem(){return entityData.get(WORK_ITEM);}
    public void displayWorkItem(net.minecraft.world.item.ItemStack item){
        var display=item.isEmpty()?net.minecraft.world.item.ItemStack.EMPTY:item.copyWithCount(1);
        if(!net.minecraft.world.item.ItemStack.matches(entityData.get(WORK_ITEM),display))entityData.set(WORK_ITEM,display);
    }
    public int skinVariant() { return entityData.get(SKIN); }
    /** Rendering flag mirrored from the registry life state (AD-031). */
    public boolean child() { return entityData.get(CHILD); }
    public void refreshLife(Resident resident) { entityData.set(CHILD,resident.life()==Resident.Life.CHILD); }
    private void profile(org.villageastra.domain.ResidentProfile profile) {
        entityData.set(SKIN,profile.skin());setCustomName(Component.literal(profile.name()));setCustomNameVisible(true);
    }
    private String workStatus="";
    public void workStatus(String status){workStatus=status;}
    /** AD-054: every goal this resident can ever run — the matrix test checks that each profession has exactly one executor. */
    // Made in registerGoals, which the Mob constructor calls before this class initialises its fields: no initialiser here.
    private DoorwayGoal doorway;
    public DoorwayGoal doorway(){return doorway;}
    /** AD-060: residents do not shove each other. Two of them meeting in a one-block doorway used to push each other into the frame and stick;
     *  now they slip past, and the doorway goal still has the second one make way. A player still pushes a resident aside. */
    @Override protected void doPush(net.minecraft.world.entity.Entity other){if(other instanceof ResidentEntity)return;super.doPush(other);}
    /** AD-135/AD-139 (merge probes 2026-09-24: a builder fell off the scaffold of the tower mill at step 218 and died, the site stood still
     *  without its cargo): a village's builder works roped to the scaffold and takes no fall damage; every other resident falls as usual. */
    @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){
        if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke")&&distance>3)com.mojang.logging.LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH fall id={} pos={} distance={} health={} goals={} builder={}",getUUID(),position(),distance,getHealth(),runningGoals(),builder());
        if(builder())return false;return super.causeFallDamage(distance,multiplier,source);}
    /** This resident is its village's builder (server side; false without a village). */
    public boolean builder(){
        if(level().isClientSide||settlementId==null||getServer()==null)return false;
        var entry=org.villageastra.server.SettlementData.get(getServer()).entry(settlementId);var r=entry==null?null:entry.settlement().resident(getUUID());
        return r!=null&&r.profession()==org.villageastra.domain.Profession.BUILDER;}
    public java.util.List<String> goalNames(){return goalSelector.getAvailableGoals().stream().map(g->g.getGoal().getClass().getSimpleName()).toList();}
    /** Diagnostics for harness logs only: names of currently running AI goals. */
    public String runningGoals() { return goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(",")); }
    public String workStatus(){return workStatus;}
    /** GameTests: this resident keeps only the goals {@code keep} accepts and takes {@code add} on top (a real builder without village life). */
    public void onlyGoals(java.util.function.Predicate<net.minecraft.world.entity.ai.goal.Goal> keep,int priority,net.minecraft.world.entity.ai.goal.Goal add){
        // removeAllGoals drops entries without stopping a running goal; its MOVE/LOOK locks can survive forever.
        for(var wrapped:goalSelector.getAvailableGoals().stream().filter(g->!keep.test(g.getGoal())).toList())goalSelector.removeGoal(wrapped.getGoal());
        goalSelector.addGoal(priority,add);}
    private UUID settlementId;
    private UUID escortPlayer;
    private String escortState = "none";
    private net.minecraft.core.BlockPos bootstrapOrigin;
    private BlockWork blockWork;
    private boolean bootstrapNatural;
    private int bootstrapLayoutVersion=1;
    private long[] bootstrapRoads=new long[0];
    public long[] bootstrapRoads(){return bootstrapRoads.clone();}
    public void bootstrapRoads(long[] values){if(values.length>4096)throw new IllegalArgumentException("Oversized initial road network");bootstrapRoads=values.clone();}
    public int bootstrapLayoutVersion(){return bootstrapLayoutVersion;}
    public void bootstrapLayoutVersion(int value){bootstrapLayoutVersion=value;}
    private int[] bootstrapElevations=new int[7];
    public int[] bootstrapElevations(){return bootstrapElevations.clone();}
    public void bootstrapElevations(int[] values){if(values.length!=7)throw new IllegalArgumentException("Invalid lot heights");bootstrapElevations=values.clone();}
    public boolean bootstrapNatural(){return bootstrapNatural;}
    public void bootstrapNatural(boolean value){bootstrapNatural=value;}
    public ResidentEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        moveControl=new ResidentMovement();
        setPersistenceRequired();
        setCanPickUpLoot(false);
        configure(getNavigation());
        // AD-046: a scaffold column is a poor road — routes go around it — but never a dead end: a column may stand in the only doorway.
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER,24F);
    }
    private static final float PATH_BUDGET=4F;
    private final class ResidentMovement extends net.minecraft.world.entity.ai.control.MoveControl {
        ResidentMovement(){super(ResidentEntity.this);}
        private boolean paddling;
        @Override public void tick(){
            if(paddling){setXxa(0);paddling=false;}
            boolean steering=operation==Operation.MOVE_TO||operation==Operation.JUMPING;
            super.tick();
            // Paddle toward the waypoint while compensating a sideways current. These are
            // ordinary bounded movement inputs; collisions, buoyancy and fluid physics remain active.
            if(steering&&isInWaterOrBubble()&&speedModifier>0){
                paddling=true;
                var delta=new net.minecraft.world.phys.Vec3(wantedX-getX(),0,wantedZ-getZ());
                double distance=delta.length();
                var desired=distance<1E-6?net.minecraft.world.phys.Vec3.ZERO:delta.scale(Math.min(speedModifier*.75,distance*2)/distance);
                var flow=level().getFluidState(blockPosition()).getFlow(level(),blockPosition());
                var input=desired.subtract(flow.x*.7,0,flow.z*.7);
                if(input.lengthSqr()>1)input=input.normalize();
                double yaw=Math.toRadians(getYRot());
                setXxa((float)(input.x*Math.cos(yaw)+input.z*Math.sin(yaw)));
                setZza((float)(input.z*Math.cos(yaw)-input.x*Math.sin(yaw)));
            }
        }
        void swimTo(net.minecraft.world.phys.Vec3 target,double speed){
            // A failed bank jump never lands under a low roof. The verified
            // horizontal escape must be allowed to steer before onGround.
            if(isInWaterOrBubble()&&operation==Operation.JUMPING)operation=Operation.WAIT;
            setWantedPosition(target.x,target.y,target.z,speed);
        }
    }
    void swimToward(net.minecraft.world.phys.Vec3 target,double speed){((ResidentMovement)moveControl).swimTo(target,speed);}
    private static void configure(net.minecraft.world.entity.ai.navigation.PathNavigation nav) {
        if (nav instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
            navigation.setCanPassDoors(true);
            // AD-080: like a vanilla villager, a resident can swim a stretch of water — without it an escorted companion
            // bobs at the bank while the player waits on the other side, and the way home ends at the first pond.
            navigation.setCanFloat(true);
        }
        // AD-046: a way around a building site is a long detour; the default budget of 128 nodes gives up halfway.
        nav.setMaxVisitedNodesMultiplier(PATH_BUDGET);
    }
    /** AD-106: the route this resident would walk to a place, planned on a navigation of its own so the one it walks by is left alone.
     *  Null while it is in the air. */
    public net.minecraft.world.level.pathfinder.Path routeTo(net.minecraft.core.BlockPos target, int accuracy) {
        var nav = createNavigation(level()); configure(nav); return ResidentPlannedRoute.keep(nav.createPath(target, accuracy),accuracy,0,PATH_BUDGET);
    }
    private boolean reversibleRoute,recoveryTransit;
    /** A verified climb may cross remembered floor to its anchor; its exit is checked separately. */
    net.minecraft.world.level.pathfinder.Path routeToRecoveryAnchor(net.minecraft.core.BlockPos target){
        boolean previous=recoveryTransit;recoveryTransit=true;
        try{return ResidentPlannedRoute.recovery(routeTo(target,0,64));}finally{recoveryTransit=previous;}
    }
    @Override public int getMaxFallDistance(){return reversibleRoute?1:super.getMaxFallDistance();}
    /** Plan a bounded expedition along steps it can climb back, rather than rejecting a shorter cliff route afterwards. */
    public net.minecraft.world.level.pathfinder.Path routeTo(net.minecraft.core.BlockPos target,int accuracy,int range){return routeTo(target,accuracy,range,PATH_BUDGET*Math.max(1F,range/(float)Math.max(1D,getAttributeValue(Attributes.FOLLOW_RANGE))));}
    /** A separate planner with an explicit, bounded exploration budget; does not change the active navigation. */
    public net.minecraft.world.level.pathfinder.Path routeTo(net.minecraft.core.BlockPos target,int accuracy,int range,float exploration){
        boolean previous=reversibleRoute;reversibleRoute=true;
        try{var nav=createNavigation(level());configure(nav);nav.setMaxVisitedNodesMultiplier(exploration);return ResidentPlannedRoute.keep(nav.createPath(target,accuracy,range),accuracy,range,exploration);}
        finally{reversibleRoute=previous;}
    }
    /** Several verified work platforms share one bounded native expedition search. */
    public net.minecraft.world.level.pathfinder.Path routeToAny(java.util.Set<net.minecraft.core.BlockPos> targets,int range){
        boolean previous=reversibleRoute;reversibleRoute=true;
        try{var nav=(ResidentNavigation)createNavigation(level());configure(nav);float exploration=PATH_BUDGET*Math.max(1F,range/(float)Math.max(1D,getAttributeValue(Attributes.FOLLOW_RANGE)));nav.setMaxVisitedNodesMultiplier(exploration);return ResidentPlannedRoute.keep(nav.toAny(targets,range),0,range,exploration);}
        finally{reversibleRoute=previous;}
    }
    private class ResidentNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
        ResidentNavigation(Level level){super(ResidentEntity.this,level);}
        net.minecraft.world.level.pathfinder.Path toAny(java.util.Set<net.minecraft.core.BlockPos> targets,int range){return createPath(targets,8,false,0,(float)range);}
    }
    private boolean longStride;
    /** AD-079: walking its own mine shaft, a miner steps up a whole tread instead of hopping at it — vanilla routes plan such a step,
     *  but with the villager stride of 0.6 the worker stuck at the step itself. A tread and a half is the tallest step the shaft asks for:
     *  out of the face it has just worked out the miner climbs that much. Everywhere else the ordinary stride is kept, so a builder on a
     *  crowded site still cannot climb its own scaffolds (AD-030). */
    public void longStride(boolean value){longStride=value;}
    @Override public float maxUpStep(){return longStride?1.6F:super.maxUpStep();}
    /** AD-069: routes never cut the corner of a scaffold column diagonally — a corner cut carries the worker into the column cell, where it is
     *  stepped out again and the same cut is planned once more. Around a column the route takes straight steps. */
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new ResidentNavigation(level) {
            private double requestedSpeed;
            @Override protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<net.minecraft.core.BlockPos> targets,int padding,boolean above,int accuracy,float range){
                // The vanilla cache belongs to this navigator's own last request. An imported
                // route must not be returned as a cached path to an earlier bedroom target.
                var active=path;
                if(!(active instanceof ResidentPlannedRoute)&&!(active instanceof ResourceReturnRoute.ReturnPath))return super.createPath(targets,padding,above,accuracy,range);
                path=null;
                try{return super.createPath(targets,padding,above,accuracy,range);}finally{path=active;}
            }
            @Override public void tick(){speedModifier=ResidentTravel.speed(ResidentEntity.this,path,requestedSpeed);super.tick();}
            @Override public boolean moveTo(net.minecraft.world.level.pathfinder.Path incoming,double speed){
                requestedSpeed=speed;
                // Vanilla compares only nodes; an identical ordinary path must not erase the return policy (or keep it for another goal).
                if(!ResidentPlannedRoute.samePolicy(incoming,path)){path=null;hasDelayedRecomputation=false;}
                return super.moveTo(incoming,ResidentTravel.speed(ResidentEntity.this,incoming,speed));
            }
            @Override public net.minecraft.core.BlockPos getTargetPos(){return path instanceof ResourceReturnRoute.ReturnPath||path instanceof ResidentPlannedRoute?path.getTarget():super.getTargetPos();}
            @Override public void recomputePath(){
                if(!(path instanceof ResourceReturnRoute.ReturnPath)&&!(path instanceof ResidentPlannedRoute)){super.recomputePath();return;}
                if(!canUpdatePath()||level.getGameTime()-timeLastRecompute<=20L){hasDelayedRecomputation=true;return;}
                path=path instanceof ResidentPlannedRoute request?request.refresh(ResidentEntity.this):ResourceReturnRoute.plan(ResidentEntity.this,path.getTarget());
                timeLastRecompute=level.getGameTime();hasDelayedRecomputation=false;
            }
            @Override public void stop(){if(path instanceof ResourceReturnRoute.ReturnPath||path instanceof ResidentPlannedRoute)hasDelayedRecomputation=false;super.stop();}
            @Override protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxVisitedNodes) {
                nodeEvaluator = new net.minecraft.world.level.pathfinder.WalkNodeEvaluator() {
                    // Long expeditions can alias vanilla's packed int key, particularly at negative coordinates.
                    private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<net.minecraft.world.level.pathfinder.Node> coordinateNodes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
                    // A synchronous search may reach the same cell from several neighbours.
                    // Body size and terrain stay fixed during that search; the next search checks them afresh.
                    private final it.unimi.dsi.fastutil.longs.Long2ByteMap waterClearance = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
                    private boolean clearWater(net.minecraft.core.BlockPos p){
                        long key=p.asLong();byte known=waterClearance.get(key);
                        if(known!=0)return known==1;
                        boolean clear=ShoreEscapeGoal.clearWaterNode(ResidentEntity.this,p);
                        waterClearance.put(key,(byte)(clear?1:2));return clear;
                    }
                    @Override protected net.minecraft.world.level.pathfinder.Node getNode(int x,int y,int z){
                        return coordinateNodes.computeIfAbsent(net.minecraft.core.BlockPos.asLong(x,y,z),key -> new net.minecraft.world.level.pathfinder.Node(x,y,z));
                    }
                    @Override public void prepare(net.minecraft.world.level.PathNavigationRegion region,net.minecraft.world.entity.Mob mob){coordinateNodes.clear();waterClearance.clear();super.prepare(region,mob);}
                    @Override public void done(){super.done();coordinateNodes.clear();waterClearance.clear();}
                    @Override public int getNeighbors(net.minecraft.world.level.pathfinder.Node[] neighbors,net.minecraft.world.level.pathfinder.Node from){
                        int count=super.getNeighbors(neighbors,from);int kept=0;
                        // The vanilla step-up recursion can still emit a two-block descent under an overhang.
                        // A one-way cave step can lead straight back onto the isolated
                        // ledge just left by controlled descent. Recovery memory must
                        // guide the expedition planner as well as wall climbing.
                        // Exclude unsafe floating headroom during search so a longer safe detour can still be found.
                        for(int i=0;i<count;i++)if(ResidentStepClearance.cropLanding(level,from,neighbors[i])&&(!reversibleRoute||Math.abs(neighbors[i].y-from.y)<=1&&(recoveryTransit||!RecoveryLedges.deadEnd(ResidentEntity.this,neighbors[i].asBlockPos()))&&clearWater(neighbors[i].asBlockPos())))neighbors[kept++]=neighbors[i];return kept;
                    }
                    @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(net.minecraft.world.level.BlockGetter blocks,int x,int y,int z,net.minecraft.world.entity.Mob mob){
                        var state=blocks.getBlockState(new net.minecraft.core.BlockPos(x,y,z));
                        // Vanilla labels pointed dripstone as open despite its solid tapered collision shape.
                        // A return route must go around the column instead of entering it or cutting between two.
                        if(state.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE))return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
                        if(state.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)&&state.getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN))return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
                        var below=blocks.getBlockState(new net.minecraft.core.BlockPos(x,y-1,z));
                        if(below.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE))return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
                        if(state.isAir()&&below.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN)&&below.getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN))return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
                        return super.getBlockPathType(blocks,x,y,z,mob);
                    }
                    @Override protected boolean isDiagonalValid(net.minecraft.world.level.pathfinder.Node from, net.minecraft.world.level.pathfinder.Node sideA,
                            net.minecraft.world.level.pathfinder.Node sideB, net.minecraft.world.level.pathfinder.Node diagonal) {
                        if (sideA != null && sideA.type == net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER
                                || sideB != null && sideB.type == net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER) return false;
                        return super.isDiagonalValid(from, sideA, sideB, diagonal)
                            && ResidentStepClearance.diagonalAscent(ResidentEntity.this,level,from,diagonal);
                    }
                };
                nodeEvaluator.setCanPassDoors(true);
                return new net.minecraft.world.level.pathfinder.PathFinder(nodeEvaluator, maxVisitedNodes);
            }
        };
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 96);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        // AD-065: guards, archers and soldiers stand their ground when struck; only fire or frost sends them running like everybody else.
        goalSelector.addGoal(1, new PanicGoal(this, 1.2){@Override protected boolean shouldPanic(){return panicsWhenHurt()?super.shouldPanic():mob.isFreezing()||mob.isOnFire();}});
        goalSelector.addGoal(2, new EscortGoal(this));
        // AD-060: meeting in a doorway, one makes way; at night everybody but the watch goes to bed.
        doorway=new DoorwayGoal(this);goalSelector.addGoal(1, doorway);
        // AD-064: in a raid those who do not fight run into the town hall.
        goalSelector.addGoal(1, new RaidShelterGoal(this));
        goalSelector.addGoal(2, new SleepGoal(this));
        goalSelector.addGoal(1, new BedExitGoal(this));
        goalSelector.addGoal(1, new SafeDescentGoal(this));
        goalSelector.addGoal(0, new SolidEscapeGoal(this));
        // Verified shallow-pit recovery may interrupt floating; submerged water still uses FloatGoal.
        goalSelector.addGoal(-1, new CaveEscapeGoal(this));
        goalSelector.addGoal(0, new PitEscapeGoal(this));
        goalSelector.addGoal(0, new FoliageEscapeGoal(this));
        goalSelector.addGoal(0, new ShoreEscapeGoal(this));
        goalSelector.addGoal(3, new ResidentDoorGoal(this));
        goalSelector.addGoal(3, new GuardGoal(this));
        goalSelector.addGoal(5, new DoctorGoal(this));
        goalSelector.addGoal(4, new ReturnCargoGoal(this));
        goalSelector.addGoal(4, new BlockWorkGoal(this));
        // AD-139: a due meal at the restaurant's table comes before the work of the day; the restaurant's couriers work with the others.
        goalSelector.addGoal(4, new DineGoal(this));
        goalSelector.addGoal(5, new HallUpgradeGoal(this));
        goalSelector.addGoal(5, new MayorSiteGoal(this));
        goalSelector.addGoal(5, new ShelterGoal(this));
        goalSelector.addGoal(5, new NaturalSupplyGoal(this));
        goalSelector.addGoal(5, new WorkerSupplyGoal(this));
        goalSelector.addGoal(6, new ResourceWorkGoal(this));
        goalSelector.addGoal(6, new ResearchGoal(this));
        goalSelector.addGoal(6, new PorterGoal(this));
        goalSelector.addGoal(6, new WorkshopGoal(this));
        goalSelector.addGoal(6, new SchoolGoal(this));
        // AD-152: the sick go to the hospital.
        goalSelector.addGoal(5, new PatientGoal(this));
        goalSelector.addGoal(5, new MedicinePickupGoal(this));
        goalSelector.addGoal(6, new DrillGoal(this));
        goalSelector.addGoal(6, new LivestockGoal(this));
        goalSelector.addGoal(6, new CartographerGoal(this));
        // AD-158 II: with the whole village on the map, the same cartographer goes round lighting its dark corners.
        goalSelector.addGoal(6, new CartographerLightGoal(this));
        goalSelector.addGoal(6, new ExpeditionGoal(this));
        goalSelector.addGoal(6, new QuarryGoal(this));
        goalSelector.addGoal(6, new RoadWorkGoal(this));
        goalSelector.addGoal(6, new HandBreadGoal(this));
        goalSelector.addGoal(6, new CourierGoal(this));
        goalSelector.addGoal(1, new CaravanGoal(this));
        goalSelector.addGoal(2, new SoldierGoal(this));
        // AD-143: an idle resident rests on a chair of its home now and then (before the stroll of the same priority).
        goalSelector.addGoal(7, new HomeRestGoal(this));
        goalSelector.addGoal(5, new HomeNeighborhood(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.65));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }
    public UUID settlementId() { return settlementId; }
    /** Everybody runs from a blow except the fighters of a settlement and those walking out with a player.
     *  AD-075: a captive in a cage and a survivor of a wreck have nowhere to run; under fire they keep to their rescuer instead of bolting. */
    public boolean panicsWhenHurt(){
        if(escortPlayer!=null||getPersistentData().hasUUID(Camps.QUEST_TAG))return false;
        if(settlementId==null||!(level() instanceof net.minecraft.server.level.ServerLevel l))return true;
        var entry=org.villageastra.server.SettlementData.get(l.getServer()).entry(settlementId);
        return entry==null||!Battle.defender(entry,getUUID());
    }
    public BlockWork blockWork() { return blockWork; }
    public void blockWork(BlockWork work) {
        if (blockWork != null && (blockWork.active() || !blockWork.carried().isEmpty())) throw new IllegalStateException("Worker has unfinished cargo");
        blockWork = work;
    }
    public net.minecraft.core.BlockPos bootstrapOrigin() { return bootstrapOrigin; }
    public void bootstrapOrigin(net.minecraft.core.BlockPos origin) { bootstrapOrigin = origin.immutable(); }
    public UUID escortPlayer() { return escortPlayer; }
    public String escortState() { return escortState; }
    /** QUEST-003 (AD-106): while a companion waits, why — the player is away, in another world, too far, beyond any way, across water too
     *  wide to swim, the boat is stuck, or it was told to wait. */
    private String escortReason = "";
    /** The portal its player went through: where, from which world into which, and until when it may still follow. */
    private CompoundTag escortPortal;
    private boolean escortWarned;
    /** Let go in the middle of the water: still seated, rowing itself to the nearest land (AD-106). */
    private boolean escortReleasing;
    public boolean releasing() { return escortReleasing; }
    public void releasing(boolean value) { escortReleasing = value; }
    public String escortReason() { return escortReason; }
    public void setEscort(String state, String reason) { escortState = state; escortReason = reason; }
    /** Told to wait: it keeps its player and stays until called. */
    public boolean ordered() { return "waiting".equals(escortState) && "ordered".equals(escortReason); }
    public void order(boolean wait) { if (wait) setEscort("waiting", "ordered"); else setEscort("following", ""); }
    public void escort(UUID player) {
        escortPlayer = player; escortState = player == null ? "none" : "following"; escortReason = ""; escortPortal = null; escortWarned = false; escortReleasing = false;
        // Let go in a boat: out onto dry land at once if there is some, otherwise it rows itself ashore first — never left standing mid-lake.
        if (player == null && getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat && !EscortGoal.dryDismount(this, boat, position()))
            escortReleasing = boat.isInWater() && boat.getFirstPassenger() == this;
    }
    /** Home: nobody is followed any more, and the board can say it arrived. */
    public void arrive() { escort(null); escortState = "arrived"; }
    public void recordPortal(net.minecraft.core.BlockPos cell, net.minecraft.resources.ResourceKey<Level> from, net.minecraft.resources.ResourceKey<Level> to, long until) {
        var t = new CompoundTag(); t.putLong("pos", cell.asLong()); t.putString("from", from.location().toString()); t.putString("to", to.location().toString()); t.putLong("until", until);
        escortPortal = t;
    }
    public net.minecraft.core.GlobalPos portalCell() {
        if (escortPortal == null) return null;
        var from = net.minecraft.resources.ResourceLocation.tryParse(escortPortal.getString("from")); if (from == null) return null;
        return net.minecraft.core.GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, from), net.minecraft.core.BlockPos.of(escortPortal.getLong("pos")));
    }
    public long portalUntil() { return escortPortal == null ? 0 : escortPortal.getLong("until"); }
    public void clearPortal() { escortPortal = null; }
    /** May it go through a portal into this world now: only behind its player, through the world the note says, while the note holds. */
    public boolean crossingTo(net.minecraft.resources.ResourceKey<Level> to, long now) {
        return escortPortal != null && escortPortal.getString("from").equals(level().dimension().location().toString())
            && escortPortal.getString("to").equals(to.location().toString()) && now < escortPortal.getLong("until");
    }
    public boolean escortWarned() { return escortWarned; }
    public void escortWarned(boolean value) { escortWarned = value; }
    public void bind(UUID settlement, Resident resident) {
        settlementId = settlement;
        setUUID(resident.id());
        profile(resident.profile());
        refreshLife(resident);
        setCustomNameVisible(true);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AstraEntitySchema",3);
        tag.putInt("AstraSkin",skinVariant());tag.putBoolean("AstraChild",child());tag.putBoolean("AstraNatural",bootstrapNatural);tag.putIntArray("AstraElevations",bootstrapElevations);tag.putInt("AstraLayoutVersion",bootstrapLayoutVersion);
        tag.putLongArray("AstraInitialRoads",bootstrapRoads);
        if (settlementId != null) tag.putUUID("AstraSettlement", settlementId);
        if (escortPlayer != null) tag.putUUID("AstraEscortPlayer", escortPlayer);
        tag.putString("AstraEscortState", escortState);
        tag.putString("AstraEscortReason", escortReason);
        if (escortPortal != null) tag.put("AstraEscortPortal", escortPortal.copy());
        tag.putBoolean("AstraEscortWarned", escortWarned);
        tag.putBoolean("AstraEscortReleasing", escortReleasing);
        if (bootstrapOrigin != null) tag.putLong("AstraOrigin", bootstrapOrigin.asLong());
        if (blockWork != null) tag.put("AstraBlockWork",blockWork.save());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getInt("AstraEntitySchema") < 2) getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        settlementId = tag.hasUUID("AstraSettlement") ? tag.getUUID("AstraSettlement") : null;
        escortPlayer = tag.hasUUID("AstraEscortPlayer") ? tag.getUUID("AstraEscortPlayer") : null;
        escortState = org.villageastra.domain.EscortStates.state(tag.getString("AstraEscortState"), escortPlayer != null);
        escortReason = org.villageastra.domain.EscortStates.reason(tag.getString("AstraEscortReason"));
        escortPortal = tag.contains("AstraEscortPortal", net.minecraft.nbt.Tag.TAG_COMPOUND) ? tag.getCompound("AstraEscortPortal").copy() : null;
        escortWarned = tag.getBoolean("AstraEscortWarned");
        escortReleasing = tag.getBoolean("AstraEscortReleasing");
        bootstrapNatural=tag.getBoolean("AstraNatural");bootstrapLayoutVersion=tag.contains("AstraLayoutVersion")?tag.getInt("AstraLayoutVersion"):1;
        bootstrapRoads(tag.getLongArray("AstraInitialRoads"));
        if(tag.contains("AstraElevations"))bootstrapElevations(tag.getIntArray("AstraElevations"));
        bootstrapOrigin = tag.contains("AstraOrigin") ? net.minecraft.core.BlockPos.of(tag.getLong("AstraOrigin")) : null;
        blockWork = tag.contains("AstraBlockWork") ? BlockWork.load(tag.getCompound("AstraBlockWork")) : null;
        entityData.set(CHILD,tag.getBoolean("AstraChild"));
        if(tag.getInt("AstraEntitySchema")<3) profile(org.villageastra.domain.ResidentProfile.generate(getUUID()));
        else {
            int skin=tag.getInt("AstraSkin");
            if(!tag.contains("AstraSkin",net.minecraft.nbt.Tag.TAG_INT) || skin<0 || skin>=org.villageastra.domain.ResidentProfile.SKINS.size())throw new IllegalArgumentException("Invalid saved skin");
            entityData.set(SKIN,skin);
        }
    }
    @Override public void aiStep(){
        if(!level().isClientSide&&tickCount%100==0)HomeNeighborhood.restrict(this);
        super.aiStep();
        if(!level().isClientSide&&(tickCount+getId())%40==0)org.villageastra.server.Gifts.mirror(this);
        if(!level().isClientSide&&isSleeping()&&(!SleepGoal.night(level())||getSleepingPos().isEmpty()||!(level().getBlockState(getSleepingPos().get()).getBlock() instanceof net.minecraft.world.level.block.BedBlock))){stopSleeping();setPose(net.minecraft.world.entity.Pose.STANDING);}
        else if(!level().isClientSide&&!isSleeping()&&getPose()==net.minecraft.world.entity.Pose.SLEEPING)setPose(net.minecraft.world.entity.Pose.STANDING);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // AD-106: a companion hurt on the way is bandaged, or fed while badly wounded, by the player it follows.
        if(!level().isClientSide&&hand==InteractionHand.MAIN_HAND&&escortPlayer!=null&&!player.isShiftKeyDown()&&player instanceof net.minecraft.server.level.ServerPlayer carer&&carer.getUUID().equals(escortPlayer)){
            var wound=Adventures.treatWound(carer,this,hand);
            if(!wound.isEmpty()){org.villageastra.dialog.Dialogs.say(carer,this,Component.translatable("quest.villageastra."+wound));return InteractionResult.SUCCESS;}
        }
        // AD-041: a camp companion joins or waits for the player who owns its quest.
        if(!level().isClientSide&&hand==InteractionHand.MAIN_HAND&&settlementId==null&&getPersistentData().hasUUID(Camps.QUEST_TAG)&&player instanceof net.minecraft.server.level.ServerPlayer owner){
            // AD-075: a wounded survivor is treated by hand first, and a caged captive waits for the bars and for the chief.
            var care=Adventures.treat(owner,this,hand);
            if(!care.isEmpty()){org.villageastra.dialog.Dialogs.say(owner,this,Component.translatable("quest.villageastra."+care));return InteractionResult.SUCCESS;}
            var hold=Adventures.hold(this);
            if(!hold.isEmpty()){org.villageastra.dialog.Dialogs.say(owner,this,Component.translatable("quest.villageastra.hold."+hold));return InteractionResult.SUCCESS;}
            var quest=org.villageastra.world.Quests.owned(owner,getPersistentData().getUUID(Camps.QUEST_TAG));
            // AD-106: a click calls the companion along; a click with Shift tells it to wait here, still keeping to its player.
            if(quest){if(!player.getUUID().equals(escortPlayer))escort(player.getUUID());order(player.isShiftKeyDown());workStatus(ordered()?"companion_waiting":"companion_following");}
            // AD-146: the companion answers in the window, not in a line over the hotbar.
            org.villageastra.dialog.Dialogs.say(owner,this,Component.translatable("quest.villageastra."+(quest?ordered()?"companion_waits":"companion_follows":"companion_other")));
            return InteractionResult.SUCCESS;
        }
        // AD-091: a resident of the village a sealed letter is meant for takes it to their mayor and hands back the answer.
        if(!level().isClientSide&&hand==InteractionHand.MAIN_HAND&&settlementId!=null&&player instanceof net.minecraft.server.level.ServerPlayer envoy){
            var answer=Wilds.deliver(envoy,this,hand);
            if(!answer.isEmpty()){org.villageastra.dialog.Dialogs.say(envoy,this,Component.translatable("quest.villageastra."+answer));return InteractionResult.SUCCESS;}
        }
        // AD-146 (owner 2026-09-24: the fewest notes, the most talk): a right click opens the conversation — the greeting with trade and
        // gifts, «Как дела?» and what other work adds; with Shift the resident tells how things are, in the window as well.
        if(!level().isClientSide&&hand==InteractionHand.MAIN_HAND&&player instanceof net.minecraft.server.level.ServerPlayer sp){
            if(player.isShiftKeyDown())org.villageastra.dialog.Dialogs.open(sp,new org.villageastra.dialog.DialogPage(getUUID(),org.villageastra.dialog.ResidentTalk.news(sp,this),java.util.List.of(org.villageastra.dialog.DialogOption.of("resident/back",Component.translatable("dialog.villageastra.back")),org.villageastra.dialog.DialogOption.of("close/bye",Component.translatable("dialog.villageastra.bye"))),null));
            else org.villageastra.dialog.Dialogs.greet(sp,this);
            return InteractionResult.SUCCESS;}
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}

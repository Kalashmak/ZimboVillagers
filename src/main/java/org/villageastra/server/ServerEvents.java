package org.villageastra.server;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Resident;
import org.villageastra.world.ResidentEntity;

@Mod.EventBusSubscriber(modid = VillageAstra.ID)
public final class ServerEvents {
    private ServerEvents() {}
    @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent event){
        ConstructionNetwork.clear();
        org.villageastra.world.MayorPlanner.clear();org.villageastra.world.Walls.forget();org.villageastra.world.CartographerGoal.clear();org.villageastra.world.Roads.clear();org.villageastra.world.ResearchKnobs.clear();org.villageastra.world.Atlas.forgetMargins();org.villageastra.world.Trails.clear();ROADS_DISCOVERED.clear();org.villageastra.world.Caravans.clear();org.villageastra.world.Sieges.clear();org.villageastra.world.TouchLoad.clear();
        InitialRoads.clear();
        org.villageastra.world.CargoCustody.recover(event.getServer());
        var data=SettlementData.get(event.getServer());
        for(var entry:data.entries()){
            var researchLevel=event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(entry.dimension())));
            if(researchLevel!=null)BookResearch.recover(researchLevel,entry);
            if(org.villageastra.world.HallUpgradeGoal.recoverCompleted(event.getServer().overworld(),entry))data.setDirty();
            if(org.villageastra.world.ResourceWorkGoal.recoverAreas(event.getServer(),entry))data.setDirty();
        }
    }
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel && event.getEntity() instanceof ResidentEntity resident && org.villageastra.world.CargoCustody.dead(serverLevel.getServer(),resident.getUUID())){event.setCanceled(true);return;}
        // AD-039: a body saved before its resident left on a caravan or moved away never rejoins.
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel caravanLevel && event.getEntity() instanceof ResidentEntity traveller && org.villageastra.world.Caravans.stale(caravanLevel.getServer(),traveller)){event.setCanceled(true);return;}
        // AD-111: a body whose resident died as a record while its chunk was unloaded (a far village starved) never rejoins; cancelling a chunk-load join also drops it from the chunk.
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel deadLevel && event.getEntity() instanceof ResidentEntity body && body.settlementId()!=null){var home=SettlementData.get(deadLevel.getServer()).entry(body.settlementId());var record=home==null?null:home.settlement().resident(body.getUUID());if(record!=null&&!record.alive()){event.setCanceled(true);return;}}

        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) ||
                !(event.getEntity() instanceof ResidentEntity entity) || entity.bootstrapOrigin() == null || entity.settlementId() == null) return;
        // World generation stores entity NBT; this callback materializes the shared registry on the server thread.
        level.getServer().execute(() -> {
            var data = SettlementData.get(level.getServer());
            if (data.entry(entity.settlementId()) == null) data.add(new SettlementData.Entry(
                    entity.bootstrapNatural()?org.villageastra.domain.Settlement.natural(entity.settlementId(),entity.bootstrapElevations(),entity.bootstrapLayoutVersion()):org.villageastra.domain.Settlement.initial(entity.settlementId()), level.dimension().location().toString(), entity.bootstrapOrigin()));
            InitialRoads.queue(level,entity.settlementId(),entity.bootstrapRoads());
        });
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            org.villageastra.world.ResourceExpedition.tick(server);
            var data = SettlementData.get(server);
            data.activeTick(server.getPlayerCount() > 0);
            if(server.getPlayerCount()>0&&data.clock().ticks()%20==0){
                InitialRoads.tick(server);
                // Never inspect block entities recursively from a ChunkEvent.Load callback.
                for(var level:server.getAllLevels()){BuildingInteractions.ensureHallDesk(level);for(var entry:data.entries())if(entry.dimension().equals(level.dimension().location().toString())){org.villageastra.world.HallStorage.ensure(level,entry);org.villageastra.world.BuildingSigns.refresh(level,entry);org.villageastra.world.Warehouses.tick(level,entry,data.clock().ticks());}}
            }
            if(server.getPlayerCount()>0&&data.clock().ticks()%20==0)for(var settlement:java.util.List.copyOf(data.entries())){org.villageastra.world.Population.tick(server,data,settlement);org.villageastra.world.MayorPlanner.tick(server,data,settlement);if(data.clock().ticks()%100==0){var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(settlement.dimension())));if(level!=null){org.villageastra.world.Atlas.tick(level,settlement);org.villageastra.world.Walls.tick(level,settlement);org.villageastra.world.Quests.tick(level,settlement,data.clock().ticks());org.villageastra.world.Annexation.tick(server,settlement,data.clock().ticks());for(var guest:level.getEntitiesOfClass(org.villageastra.world.ResidentEntity.class,new net.minecraft.world.phys.AABB(settlement.center()).inflate(48),x->x.settlementId()==null))org.villageastra.world.Camps.tickGuest(level,guest,data.clock().ticks());if(level.isNight())org.villageastra.world.Quests.callDefence(level,settlement,data.clock().ticks());if(!ROADS_DISCOVERED.contains(settlement.settlement().id())&&org.villageastra.world.Roads.discover(level,settlement,48)>=0)ROADS_DISCOVERED.add(settlement.settlement().id());}}}
            if(data.clock().ticks()%600==0)org.villageastra.world.Roads.flush(server);
            // AD-055: driven stations keep turning while somebody is in the world to see it.
            if(server.getPlayerCount()>0)org.villageastra.world.Automation.tick(server,data.clock().ticks());
            // AD-136: scientific works by the village clock and the level-I research the mayor ordered paid in resources.
            if(server.getPlayerCount()>0&&data.clock().ticks()%40==0)org.villageastra.world.ScienceWorks.tick(server,data.clock().ticks());
            // AD-158 VI: the atlas of a village whose cartographer has reached the last level draws itself.
            if(server.getPlayerCount()>0)org.villageastra.world.CartographyLadder.tick(server,data.clock().ticks());
            if(server.getPlayerCount()>0&&data.clock().ticks()%20==0){org.villageastra.world.Caravans.tick(server,data.clock().ticks());org.villageastra.world.Trails.tick(server,data.clock().ticks());MayorSurvey.revalidate(server);
             for(var campaign:java.util.List.copyOf(org.villageastra.world.Sieges.armies(server))){var campaignLevel=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(campaign.getString("dimension"))));if(campaignLevel!=null)org.villageastra.world.Sieges.tick(campaignLevel,campaign,data.clock().ticks());}
             // AD-111: the 1200-tick caravan pass, with far villages that matter to the player drained under the touch budget.
             org.villageastra.world.Caravans.pass(server,data.clock().ticks());}
            if(server.getPlayerCount()>0&&data.clock().ticks()%20==0)org.villageastra.world.Raids.tick(server,data.clock().ticks());
            if(server.getPlayerCount()>0&&data.clock().ticks()%20==0){org.villageastra.world.CargoCustody.tick(server);Elections.tick(server);ConstructionNetwork.tick(server);org.villageastra.world.Relations.tick(server,data.clock().ticks());}
            if (server.getPlayerCount() > 0 && data.clock().ticks() % 20 == 0 && !data.entries().isEmpty()) {
                long index = (data.clock().ticks() / 20) % data.entries().size();
                var entry = data.entries().stream().skip(index).findFirst().orElseThrow();
                HousingMonitor.inspect(server,data,entry);
                // AD-059: a building that lost blocks is queued for repair by the builders.
                org.villageastra.world.BuildingRepairs.check(server,entry);
            }
        }
    }
    @SubscribeEvent public static void kills(LivingDeathEvent event){
        if(!event.getEntity().level().isClientSide&&event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer killer)org.villageastra.world.Quests.onKill(killer,event.getEntity());
    }
    /** AD-155: a player puts wolf armour on a tame wolf (a village's or their own). */
    /** AD-156 (Engineering IV): a player puts a mob trap only on the land of a village that has Engineering IV. */
    @SubscribeEvent public static void placeMobTrap(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event){
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)||!event.getPlacedBlock().is(VillageAstra.MOB_TRAP.get()))return;
        if(!org.villageastra.world.MobTrapBlock.allowed(level,event.getPos())){event.setCanceled(true);player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.villageastra.mob_trap_land"),true);}
    }
    @SubscribeEvent public static void armourWolf(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event){
        if(event.getLevel().isClientSide||!(event.getTarget() instanceof net.minecraft.world.entity.animal.Wolf wolf))return;var stack=event.getItemStack();
        if(!stack.is(org.villageastra.VillageAstra.WOLF_ARMOR.get())||!(wolf.isOwnedBy(event.getEntity())||org.villageastra.world.VillageWolves.village(wolf)!=null))return;
        if(org.villageastra.world.VillageWolves.armour(wolf)){if(!event.getEntity().getAbilities().instabuild)stack.shrink(1);event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);}
    }
    /** AD-155: an armoured wolf takes half the damage. */
    @SubscribeEvent public static void armouredWolf(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        if(event.getEntity() instanceof net.minecraft.world.entity.animal.Wolf wolf&&!wolf.level().isClientSide)event.setAmount(org.villageastra.world.VillageWolves.armoured(wolf,event.getAmount()));
        // AD-159 IV: a defender's shield.
        if(event.getEntity() instanceof org.villageastra.world.ResidentEntity npc&&!npc.level().isClientSide)event.setAmount(org.villageastra.world.Army.taken(npc,event.getAmount()));
    }
    /** AD-138 IV: a village's wolf, loaded again, takes up its kennel life. */
    @SubscribeEvent public static void wolfJoins(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel&&event.getEntity() instanceof net.minecraft.world.entity.animal.Wolf wolf){if(org.villageastra.world.CaravanDogs.stale(wolf)){event.setCanceled(true);return;}org.villageastra.world.VillageWolves.attach(wolf);}
        // AD-138 V: a pen beast, loaded again, takes up its grazing life.
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel&&event.getEntity() instanceof net.minecraft.world.entity.animal.Animal beast)org.villageastra.world.LivestockGrazing.attach(beast);
    }
    /** AD-138 V (spec §6): a village's beast out grazing never tramples a field's soil. */
    @SubscribeEvent public static void trample(net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent event){
        if(event.getEntity()!=null&&event.getEntity().getPersistentData().hasUUID(org.villageastra.world.LivestockGoal.OWNER))event.setCanceled(true);
    }
    /** AD-138 IV: a village's wolf that dies leaves its kennel's registry. */
    @SubscribeEvent public static void wolves(LivingDeathEvent event){
        if(event.getEntity() instanceof net.minecraft.world.entity.animal.Wolf wolf&&wolf.level() instanceof net.minecraft.server.level.ServerLevel l){org.villageastra.world.CaravanDogs.died(wolf,SettlementData.get(l.getServer()).clock().ticks());org.villageastra.world.VillageWolves.died(l,wolf);}
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ResidentEntity entity) || entity.level().isClientSide || entity.settlementId() == null) return;
        var data = SettlementData.get(entity.getServer());
        org.villageastra.world.CargoCustody.onDeath(entity);
        org.villageastra.world.Caravans.died(entity,data.clock().ticks());
        var entry = data.entry(entity.settlementId());
        if (entry != null) {
            Resident r = entry.settlement().resident(entity.getUUID());
            if (r != null) { r.die(); data.setDirty(); }
        }
    }
    /** Offspring of settlement livestock belongs to the same settlement (AD-035). */
    private static final java.util.Set<java.util.UUID> ROADS_DISCOVERED=new java.util.HashSet<>();
    // AD-038: surface speed and traffic for every living user, sampled every 10 ticks.
    @SubscribeEvent public static void living(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){var entity=event.getEntity();if(!entity.level().isClientSide&&entity.tickCount%10==0)org.villageastra.world.Roads.living(entity);}
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof net.minecraft.world.entity.animal.Wolf dog&&dog.getRemovalReason()!=null&&!dog.getRemovalReason().shouldDestroy())org.villageastra.world.CaravanDogs.unload(dog);
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof ResidentEntity npc&&npc.getRemovalReason()!=null&&!npc.getRemovalReason().shouldDestroy())org.villageastra.world.Caravans.dematerialize(npc);
    }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){org.villageastra.world.Roads.flush(event.getServer());org.villageastra.world.ResourceExpedition.clear();}
    @SubscribeEvent public static void babies(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent event){
        var parent=event.getParentA().getPersistentData();
        if(event.getChild()!=null&&parent.hasUUID(org.villageastra.world.LivestockGoal.OWNER)){event.getChild().getPersistentData().putUUID(org.villageastra.world.LivestockGoal.OWNER,parent.getUUID(org.villageastra.world.LivestockGoal.OWNER));event.getChild().setPersistenceRequired();}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { ZimboVillagersCommands.register(event.getDispatcher()); }
}

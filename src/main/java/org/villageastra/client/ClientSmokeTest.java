package org.villageastra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
import org.villageastra.world.StarterVillage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in local smoke harness. Never runs in a regular client or opens an existing world. */
@Mod.EventBusSubscriber(modid = VillageAstra.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static int phase;
    private static int galleryIndex;
    private static volatile boolean resourcesVerified;
    private static volatile boolean researchVerified;
    private static volatile boolean deepMineVerified;
    private static boolean workerFramed,stairsFramed;private static volatile boolean stairsCameraReady;
    private static volatile boolean workerCameraReady;
    private static volatile boolean hallVerified;
    private static int hallLastIndex=-1,hallStalled;
    private static int wait;
    private static volatile boolean prepared;
    private static volatile boolean verificationDone;
    private static volatile String failure;
    private static long startedTicks;
    private static String worldName;
    private static volatile boolean workVerified;
    private static boolean workRequested;
    private static java.util.UUID workResident;
    private static BlockPos workSource,workTarget;
    private static int initialStone;
    private static boolean diagnosed;
    private ClientSmokeTest() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("villageastra.smoke") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if(!SmokeWindow.ready(mc))return;
        mc.options.pauseOnLostFocus = false;
        if(Boolean.getBoolean("villageastra.designGallery"))mc.options.hideGui=true;
        if (prepared && mc.player != null) mc.player.getAbilities().flying = true;
        if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            phase = 1;
            if (Boolean.getBoolean("villageastra.reloadSmoke")) {
                try {
                    worldName = Files.readString(mc.gameDirectory.toPath().resolve("astra-smoke-world.txt")).trim();
                    if (!worldName.matches("astra-smoke-[0-9]+")) throw new IllegalStateException("Not a generated smoke world");
                    if(CaravanRestartProbe.reloading()||ScienceProbe.enabled()||MedicineReloadProbe.enabled()||LiveUpgradeReloadProbe.enabled()||LiveProductionProbe.reloading()||FirstHouseProbe.enabled())Files.deleteIfExists(mc.gameDirectory.toPath().resolve("saves").resolve(worldName).resolve("icon.png"));
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(),worldName);
                } catch (Exception ex) { failure = ex.toString(); }
                return;
            }
            boolean natural = Boolean.getBoolean("villageastra.naturalSmoke");
            LogUtils.getLogger().info("ASTRA_SMOKE creating isolated test world, natural={}", natural);
            worldName = "astra-smoke-" + System.currentTimeMillis();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("astra-smoke-world.txt"),worldName); }
            catch (Exception ex) { failure = ex.toString(); return; }
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings("Astra renderer smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(Long.getLong("villageastra.smokeSeed",6102026L), natural, false), access -> natural ? WorldPresets.createNormalWorldDimensions(access)
                            : access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        } else if (phase == 1 && mc.player != null && mc.getSingleplayerServer() != null) {
            phase = 2;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    BlockPos viewpoint;
                    if (Boolean.getBoolean("villageastra.reloadSmoke")) {
                        var data = SettlementData.get(server);
                        if (data.entries().isEmpty()) throw new IllegalStateException("Reload lost settlements");
                        var entry = data.entries().iterator().next();
                        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++)
                            level.getChunk((entry.center().getX() >> 4)+x,(entry.center().getZ() >> 4)+z);
                        viewpoint = entry.center().offset(18,8,-8);
                    } else if (Boolean.getBoolean("villageastra.naturalSmoke")) {
                        BlockPos found = level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE,
                                level.getSharedSpawnPos(), 64, false);
                        if (found == null) throw new IllegalStateException("No natural settlement found");
                        LogUtils.getLogger().info("ASTRA_NATURAL found candidate {}; loading terrain chunks",found);
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                            level.getChunk((found.getX() >> 4) + x, (found.getZ() >> 4) + z);
                        viewpoint = new BlockPos(found.getX()+45,
                                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,found.getX()+18,found.getZ())+30,
                                found.getZ()-35);
                        LogUtils.getLogger().info("ASTRA_NATURAL located actual structure at {}", found);
                    } else if(Boolean.getBoolean("villageastra.designGallery")) {
                        for(int x=-2;x<=16;x++)for(int z=-4;z<=13;z++)level.getChunk(x,z);
                        StarterVillage.create(level,new BlockPos(0,-60,-40));
                        int index=0;
                        for(var design:org.villageastra.world.BuildingBlueprints.designs()) {
                            org.villageastra.world.BuildingBlueprints.preview(level,design.id(),new BlockPos((index%6)*48,-60,(index/6)*48));index++;
                        }
                        LogUtils.getLogger().info("ASTRA_GALLERY created {} actual building previews",index);
                        var materialReport = new com.google.gson.JsonObject();
                        for(var design:org.villageastra.world.BuildingBlueprints.designs()) {
                            var counts=new com.google.gson.JsonObject();
                            org.villageastra.world.BlueprintMaterials.count(design.id()).forEach(counts::addProperty);
                            materialReport.add(design.id(),counts);
                        }
                        Files.writeString(mc.gameDirectory.toPath().resolve("astra-building-materials.json"),
                                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(materialReport));
                        viewpoint=new BlockPos(52,5,-75);
                    } else {
                        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 2; z++) level.getChunk(x,z);
                        if(DeepMineProbe.enabled())DeepMineProbe.ground(level);
                        StarterVillage.create(level,DeepMineProbe.enabled()?DeepMineProbe.ORIGIN:new BlockPos(0,-60,0));
                        if(DeepMineProbe.enabled())DeepMineProbe.seed(level);
                        if(CargoSmokeProbe.enabled())CargoSmokeProbe.setup(server);
                        if(FarmSmokeProbe.enabled())FarmSmokeProbe.setup(server);
                        if(ConstructionProbe.enabled())ConstructionProbe.setup(server);
                        if(OfficeProbe.enabled())OfficeProbe.setup(server);
                        if(ElectionProbe.enabled())ElectionProbe.setup(server);
                        if(DraftProbe.enabled())DraftProbe.setup(server);
                        if(CropsProbe.enabled())CropsProbe.setup(server);
                        if(ScienceProbe.enabled())ScienceProbe.setup(server);
                        if(LogisticsProbe.enabled())LogisticsProbe.setup(server);
                        if(ResearchGraphProbe.enabled())ResearchGraphProbe.setup(server);
                        if(ResearchFactsProbe.enabled())ResearchFactsProbe.setup(server);
                        if(StarterVillageProbe.enabled())StarterVillageProbe.setup(server);
                        if(OfficeUiProbe.enabled())OfficeUiProbe.setup(server);
                        if(ResidentMarkerProbe.enabled())ResidentMarkerProbe.setup(server);
                        if(GrowthPlotProbe.enabled())GrowthPlotProbe.setup(server);
                        if(NaturalSupplyProbe.enabled())NaturalSupplyProbe.setup(server);
                        if(RemoteVillageProbe.enabled())RemoteVillageProbe.setup(server);
                        if(SchematicProbe.enabled())SchematicProbe.setup(server);
                        if(Boolean.getBoolean("villageastra.resourceSmoke")) {
                            // A grown oak on the nursery's first spot: a trunk of five logs and a branch. The forester fells it whole from its foot,
                            // brings the logs home and plants a sapling on the spot again. (The miner's shaft goes six blocks under the lot since
                            // AD-079, below this flat world's bedrock: mining is the deep-mine probe's.)
                            var entry=SettlementData.get(server).entries().iterator().next();var lodge=entry.settlement().buildings().stream().filter(b->b.type().equals("forester")).findFirst().orElseThrow();
                            var spot=org.villageastra.world.ForestWork.probeTree(level,entry,lodge);resourceSpot=spot;
                        }
                        if(Boolean.getBoolean("villageastra.researchSmoke"))setupResearch(server);
                        if(Boolean.getBoolean("villageastra.hallSmoke")){
                            fundHall(server);
                        }
                        viewpoint = DeepMineProbe.enabled()?DeepMineProbe.ORIGIN.offset(18,12,-8):new BlockPos(18,-52,-8);
                    }
                    if(Boolean.getBoolean("villageastra.hallThreeSmoke")){
                        var entry=SettlementData.get(server).entries().iterator().next();
                        if(entry.settlement().civilization().level()!=2)throw new IllegalStateException("Tier-three harness requires completed tier-two world");
                        fundHall(server);
                    }
                    if(!FirstHouseProbe.enabled()||!Boolean.getBoolean("villageastra.reloadSmoke"))level.setDayTime(6000);
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(level, viewpoint.getX(),viewpoint.getY(),viewpoint.getZ(),Boolean.getBoolean("villageastra.naturalSmoke")?45:0,Boolean.getBoolean("villageastra.naturalSmoke")?35:25);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    startedTicks = SettlementData.get(server).clock().ticks();
                    prepared = true;
                } catch (Exception ex) { failure = ex.toString(); }
            });
        } else if (phase == 2 && prepared && ++wait > 100) {
            if(ArchitectureMigrationProbe.enabled()){ArchitectureMigrationProbe.tick(mc);return;}
            if(SchematicProbe.enabled()){SchematicProbe.tick(mc);return;}
            if(ResidentMarkerProbe.enabled()){ResidentMarkerProbe.tick(mc);return;}
            if(GrowthPlotProbe.enabled()){GrowthPlotProbe.tick(mc);return;}
            if(NaturalSupplyProbe.enabled()){NaturalSupplyProbe.tick(mc);return;}
            if(AutonomyProbe.enabled()){AutonomyProbe.tick(mc);return;}
            if(CreativeTradeProbe.enabled()){CreativeTradeProbe.tick(mc);return;}
            if(TradeProbe.enabled()){TradeProbe.tick(mc);return;}
            if(FirstHouseProbe.enabled()){FirstHouseProbe.tick(mc);return;}
            if(AutonomyGrowthProbe.enabled()){AutonomyGrowthProbe.tick(mc);return;}
            if(GiftProbe.enabled()){GiftProbe.tick(mc);return;}
            if(AtlasProbe.enabled()){AtlasProbe.tick(mc);return;}
            if(PlanProbe.enabled()){PlanProbe.tick(mc);return;}
            if(WarProbe.enabled()){WarProbe.tick(mc);return;}
            if(BattleProbe.enabled()){BattleProbe.tick(mc);return;}
            if(NightProbe.enabled()){NightProbe.tick(mc);return;}
            if(GalleryProbe.enabled()){GalleryProbe.tick(mc);return;}
            if(LoadProbe.enabled()){LoadProbe.tick(mc);return;}
            if(MapProbe.enabled()){MapProbe.tick(mc);return;}
            if(MapToolsProbe.enabled()){MapToolsProbe.tick(mc);return;}
            if(RaidProbe.enabled()){RaidProbe.tick(mc);return;}
            if(LevelProbe.enabled()){LevelProbe.tick(mc);return;}
            if(CoreProbe.enabled()){CoreProbe.tick(mc);return;}
            if(FarmLevelsProbe.enabled()){FarmLevelsProbe.tick(mc);return;}
            if(ForesterProbe.enabled()){ForesterProbe.tick(mc);return;}
            if(LivestockProbe.enabled()){LivestockProbe.tick(mc);return;}
            if(SchoolProbe.enabled()){SchoolProbe.tick(mc);return;}
            if(MedicineProbe.enabled()){MedicineProbe.tick(mc);return;}
            if(ConstructionLadderProbe.enabled()){ConstructionLadderProbe.tick(mc);return;}
            if(WolfKennelProbe.enabled()){WolfKennelProbe.tick(mc);return;}
            if(GrazingProbe.enabled()){GrazingProbe.tick(mc);return;}
            if(AutoYardProbe.enabled()){AutoYardProbe.tick(mc);return;}
            if(DialogProbe.enabled()){DialogProbe.tick(mc);return;}
            if(QuestTalkProbe.enabled()){QuestTalkProbe.tick(mc);return;}
            if(WolfRescueProbe.enabled()){WolfRescueProbe.tick(mc);return;}
            if(HousingBedsProbe.enabled()){HousingBedsProbe.tick(mc);return;}
            if(FramedWindowProbe.enabled()){FramedWindowProbe.tick(mc);return;}
            if(FurnitureProbe.enabled()){FurnitureProbe.tick(mc);return;}
            if(ResearchTreeV2Probe.enabled()){ResearchTreeV2Probe.tick(mc);return;}
            if(AnnexProbe.enabled()){AnnexProbe.tick(mc);return;}
            if(RestaurantProbe.enabled()){RestaurantProbe.tick(mc);return;}
            if(WarehouseProbe.enabled()){WarehouseProbe.tick(mc);return;}
            if(WallProbe.enabled()){WallProbe.tick(mc);return;}
            if(WallRingProbe.enabled()){WallRingProbe.tick(mc);return;}
            if(RelocationProbe.enabled()){RelocationProbe.tick(mc);return;}
            if(DrillProbe.enabled()){DrillProbe.tick(mc);return;}
            if(CartRecoveryProbe.enabled()){CartRecoveryProbe.tick(mc);return;}
            if(QuarryProbe.enabled()){QuarryProbe.tick(mc);return;}
            if(RoadProbe.enabled()){RoadProbe.tick(mc);return;}
            if(EngineeringTrapProbe.enabled()){EngineeringTrapProbe.tick(mc);return;}
            if(LabDeskProbe.enabled()){LabDeskProbe.tick(mc);return;}
            if(PatrolProbe.enabled()){PatrolProbe.tick(mc);return;}
            if(SmithyWolfProbe.enabled()){SmithyWolfProbe.tick(mc);return;}
            if(MedicineWolfProbe.enabled()){MedicineWolfProbe.tick(mc);return;}
            if(MedicineReloadProbe.enabled()){MedicineReloadProbe.tick(mc);return;}
            if(MedicinePickupProbe.enabled()){MedicinePickupProbe.tick(mc);return;}
            if(BuildingSignsProbe.enabled()){BuildingSignsProbe.tick(mc);return;}
            if(BallistaProbe.enabled()){BallistaProbe.tick(mc);return;}
            if(TowerStagesProbe.enabled()){TowerStagesProbe.tick(mc);return;}
            if(LiveUpgradeProbe.enabled()){LiveUpgradeProbe.tick(mc);return;}
            if(LiveProductionProbe.enabled()){LiveProductionProbe.tick(mc);return;}
            if(LiveUpgradeReloadProbe.enabled()){LiveUpgradeReloadProbe.tick(mc);return;}
            if(SmithyCourierProbe.enabled()){SmithyCourierProbe.tick(mc);return;}
            if(RaidUnloadProbe.enabled()){RaidUnloadProbe.tick(mc);return;}
            if(CaravanProbe.enabled()){CaravanProbe.tick(mc);return;}
            if(QuestProbe.enabled()){QuestProbe.tick(mc);return;}
            if(AnimalQuestProbe.enabled()){AnimalQuestProbe.tick(mc);return;}
            if(CampProbe.enabled()){CampProbe.tick(mc);return;}
            if(AdventureProbe.enabled()){AdventureProbe.tick(mc);return;}
            if(WildProbe.enabled()){WildProbe.tick(mc);return;}
            if(ChainProbe.enabled()){ChainProbe.tick(mc);return;}
            if(EscortProbe.enabled()){EscortProbe.tick(mc);return;}
            if(BuildingOrderProbe.enabled()){BuildingOrderProbe.tick(mc);return;}
            if(MayorToolProbe.enabled()){MayorToolProbe.tick(mc);return;}
            if(WorldInteractionProbe.enabled()){WorldInteractionProbe.tick(mc);return;}
            if(OfficeProbe.enabled()){OfficeProbe.tick(mc);return;}
            if(ElectionProbe.enabled()){ElectionProbe.tick(mc);return;}
            if(DraftProbe.enabled()){DraftProbe.tick(mc);return;}
            if(CropsProbe.enabled()){CropsProbe.tick(mc);return;}
            if(ScienceProbe.enabled()){ScienceProbe.tick(mc);return;}
            if(LogisticsProbe.enabled()){LogisticsProbe.tick(mc);return;}
            if(ResearchGraphProbe.enabled()){ResearchGraphProbe.tick(mc);return;}
            if(ResearchFactsProbe.enabled()){ResearchFactsProbe.tick(mc);return;}
            if(ConstructionProbe.enabled()){ConstructionProbe.tick(mc);return;}
            if(StarterVillageProbe.enabled()){StarterVillageProbe.tick(mc);return;}
            if(OfficeUiProbe.enabled()){OfficeUiProbe.tick(mc);return;}
            if(RemoteVillageProbe.enabled()){RemoteVillageProbe.tick(mc);return;}
            if(FarmSmokeProbe.enabled()){FarmSmokeProbe.tick(mc);return;}
            if(CargoSmokeProbe.enabled()){CargoSmokeProbe.tick(mc);return;}
            if(JournalCrashProbe.enabled()) { JournalCrashProbe.run(mc);return; }
            if(!diagnosed) {
                diagnosed=true;
                var server=mc.getSingleplayerServer();
                server.execute(() -> {
                    var entry=SettlementData.get(server).entries().iterator().next();
                    var resident=entry.settlement().residents().stream().filter(r -> r.profession()==org.villageastra.domain.Profession.BUILDER).findFirst().orElseThrow();
                    var worker=(org.villageastra.world.ResidentEntity)server.overworld().getEntity(resident.id());
                    var service=entry.center().offset(2,1,4);
                    var path=worker.getNavigation().createPath(service,0);
                    LogUtils.getLogger().info("ASTRA_PATH source={} service={} state={} reachable={} end={}",worker.blockPosition(),service,
                            server.overworld().getBlockState(service),path!=null&&path.canReach(),path==null?"none":path.getEndNode());
                    var range=worker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
                    double oldRange=range.getBaseValue(); range.setBaseValue(64);
                    var widePath=worker.getNavigation().createPath(service,0);
                    range.setBaseValue(oldRange);
                    LogUtils.getLogger().info("ASTRA_PATH range64 reachable={} nodes={} door={}",widePath!=null&&widePath.canReach(),
                            widePath==null?0:widePath.getNodeCount(),server.overworld().getBlockState(entry.center().offset(3,1,0)));
                });
            }
            if(DeepMineProbe.enabled()&&!deepMineVerified){
                if(wait%20==0)mc.getSingleplayerServer().execute(()->{
                    try{var server=mc.getSingleplayerServer();if(wait%200==0)DeepMineProbe.follow(server);
                        deepMineVerified=DeepMineProbe.verify(server.overworld(),SettlementData.get(server).entries().iterator().next(),wait%200==0);if(deepMineVerified)org.villageastra.server.ProbeWarp.end("deep mine verified");
                        // Forty-eight blocks, one round trip down the shaft for each: the shift is long, and the probe waits it out.
                        if(!deepMineVerified&&wait>90000)failure="Four-step mine timeout";
                    }catch(Exception ex){failure="Deep mine: "+ex;}
                });
            }else if(Boolean.getBoolean("villageastra.researchSmoke")&&!researchVerified){
                if(wait%20==0)mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();var entry=SettlementData.get(server).entries().iterator().next();var civ=entry.settlement().civilization();
                    var lab=entry.settlement().buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElseThrow();
                    var desk=entry.center().offset(lab.x()+2,lab.y()+1,lab.z()+4);
                    var scientist=entry.settlement().residents().stream().filter(r->r.profession()==org.villageastra.domain.Profession.SCIENTIST).findFirst().orElseThrow();
                    var worker=server.overworld().getEntity(scientist.id());var chest=(net.minecraft.world.Container)server.overworld().getBlockEntity(desk.west());
                    if(civ.completed().contains("cartography")&&civ.active().equals("ironworking")&&civ.progress()==0){
                        if(chest.countItem(net.minecraft.world.item.Items.PAPER)!=0||worker.distanceToSqr(desk.getX()+.5,desk.getY(),desk.getZ()+.5)>6.25){failure="Research did not use physical desk and exact paper";return;}
                        researchVerified=true;LogUtils.getLogger().info("ASTRA_RESEARCH VERIFIED final 80 labor ticks completed at desk, one paper consumed, next topic paused without paper; reload={}",Boolean.getBoolean("villageastra.reloadSmoke"));
                    }else if(wait>1200)failure="Physical research timeout: "+civ.active()+" progress="+civ.progress()+" worker="+worker.position();
                });
            }else if(Boolean.getBoolean("villageastra.hallSmoke")&&!hallVerified){
                if(wait%20==0)mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();var entry=SettlementData.get(server).entries().iterator().next();
                    var project=org.villageastra.world.HallUpgradeGoal.inspect(server.overworld(),entry.settlement().id());
                    int progressKey=project.getBoolean("funded")?project.getInt("index"):-1-project.getInt("withdrawals");
                    if(progressKey==hallLastIndex)hallStalled+=20;else {hallLastIndex=progressKey;hallStalled=0;}
                    if(hallStalled>600)failure="Hall made no progress for 30 seconds at operation "+hallLastIndex;
                    if(entry.settlement().civilization().level()==(Boolean.getBoolean("villageastra.hallThreeSmoke")?3:2)&&project.getBoolean("complete")){
                        for(var tag:project.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND))if(!net.minecraft.world.item.ItemStack.of((net.minecraft.nbt.CompoundTag)tag).isEmpty()){
                            failure="Completed hall retained funded cargo";return;
                        }
                        var chest=(net.minecraft.world.Container)server.overworld().getBlockEntity(entry.center().offset(1,1,4));
                        for(int slot=12;slot<chest.getContainerSize();slot++)if(!chest.getItem(slot).isEmpty()){
                            failure="Completed hall did not debit exact finite fixture";return;
                        }
                        hallVerified=true;LogUtils.getLogger().info("ASTRA_HALL VERIFIED builder completed {} paid block changes; civilization={}",project.getInt("index"),entry.settlement().civilization().level());
                    }else if(wait%200==0){
                        var r=entry.settlement().residents().stream().filter(x->x.profession()==org.villageastra.domain.Profession.BUILDER).findFirst().orElseThrow();
                        LogUtils.getLogger().info("ASTRA_HALL progress funded={} index={} position={}",project.getBoolean("funded"),project.getInt("index"),server.overworld().getEntity(r.id()).blockPosition());
                        var ops=project.getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND);if(project.getInt("index")<ops.size())LogUtils.getLogger().info("ASTRA_HALL target {} actual={} worker={} status={}",ops.getCompound(project.getInt("index")),server.overworld().getBlockState(BlockPos.of(ops.getCompound(project.getInt("index")).getLong("pos"))),server.overworld().getEntity(r.id()).position(),((org.villageastra.world.ResidentEntity)server.overworld().getEntity(r.id())).workStatus());
                    }
                    if(wait>9000)failure="Hall construction timeout";
                });
            } else if(Boolean.getBoolean("villageastra.resourceSmoke")&&!resourcesVerified) {
                if(wait%20==0)mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();
                    if(wait%200==0)for(var entry:SettlementData.get(level.getServer()).entries())for(var r:entry.settlement().residents())if(r.profession()==org.villageastra.domain.Profession.MINER||r.profession()==org.villageastra.domain.Profession.FORESTER){var entity=level.getEntity(r.id());LogUtils.getLogger().info("ASTRA_RESOURCE progress {} position={}",r.profession(),entity==null?"missing":entity.blockPosition());}
                    var timber=(net.minecraft.world.Container)level.getBlockEntity(new BlockPos(13,-59,16));var spot=resourceSpot;
                    boolean down=spot!=null&&java.util.stream.IntStream.range(1,5).allMatch(y->level.getBlockState(spot.above(y)).isAir())&&level.getBlockState(spot.offset(1,3,0)).isAir();
                    if(wait%200==0)LogUtils.getLogger().info("ASTRA_RESOURCE quantities wood={} felled={} planted={}",timber==null?-1:timber.countItem(net.minecraft.world.item.Items.OAK_LOG),down,spot==null?"none":level.getBlockState(spot));
                    // The logs reached the lodge's chest once (the porter may carry them on to the hall afterwards).
                    if(timber!=null&&timber.countItem(net.minecraft.world.item.Items.OAK_LOG)>=6)resourceDelivered=true;
                    if(down&&resourceDelivered&&level.getBlockState(spot).is(net.minecraft.world.level.block.Blocks.OAK_SAPLING)){
                        resourcesVerified=true;LogUtils.getLogger().info("ASTRA_RESOURCE VERIFIED a whole oak felled from its foot, its six logs delivered, a sapling replanted on its spot");
                    }else if(wait>6000)failure="Resource work timeout";
                });
            } else if (Boolean.getBoolean("villageastra.blockWorkSmoke") && !workVerified) {
                var server=mc.getSingleplayerServer();
                if (!workRequested) {
                    workRequested=true;
                    server.execute(() -> {
                        try {
                            var entry=SettlementData.get(server).entries().iterator().next();
                            var builder=entry.settlement().residents().stream().filter(r -> r.profession()==org.villageastra.domain.Profession.BUILDER).findFirst().orElseThrow();
                            var worker=(org.villageastra.world.ResidentEntity)server.overworld().getEntity(builder.id());
                            workResident=builder.id();
                            workSource=entry.center().offset(1,1,4); workTarget=entry.center().offset(8,1,4);
                            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)server.overworld().getBlockEntity(workSource);
                            initialStone=chest.getItem(2).getCount();
                            worker.blockWork(new org.villageastra.world.BlockWork("minecraft:overworld",workSource,workTarget,
                                    server.overworld().getBlockState(workTarget),net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState()));
                            LogUtils.getLogger().info("ASTRA_BLOCKWORK started worker={} position={} source={} target={} stone={}",
                                    workResident,worker.blockPosition(),workSource,workTarget,initialStone);
                        } catch(Exception ex) { failure=ex.toString(); }
                    });
                } else if (wait%20==0) server.execute(() -> {
                    var worker=(org.villageastra.world.ResidentEntity)server.overworld().getEntity(workResident);
                    if (worker!=null && worker.blockWork()!=null && worker.blockWork().stage()==org.villageastra.world.BlockWork.Stage.COMPLETE) {
                        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)server.overworld().getBlockEntity(workSource);
                        if (chest.getItem(2).getCount()!=initialStone-1 || !server.overworld().getBlockState(workTarget).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) {
                            failure="Paid physical work has inconsistent result"; return;
                        }
                        LogUtils.getLogger().info("ASTRA_BLOCKWORK VERIFIED real navigation, chest debit {} -> {}, placed one block",initialStone,chest.getItem(2).getCount());
                        workVerified=true;
                    } else if(wait>1500) failure="Physical builder timeout; position="+(worker==null?"missing":worker.blockPosition())+
                            " stage="+(worker==null||worker.blockWork()==null?"missing":worker.blockWork().stage());
                });
            } else { org.villageastra.server.ProbeWarp.end("waiting done, frames at normal speed"); phase = 3; wait = 0; }
        }
        if (failure != null) {
            LogUtils.getLogger().error("ASTRA_SMOKE FAILED {}", failure);
            phase = 99; mc.stop(); failure = null;
        }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (!Boolean.getBoolean("villageastra.smoke") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if(phase==3&&DeepMineProbe.enabled()&&!workerFramed){
                workerFramed=true;mc.options.hideGui=true;
                var server=mc.getSingleplayerServer();server.execute(()->{
                    try {DeepMineProbe.frameWorker(server);workerCameraReady=true;}catch(Exception ex){failure="Miner camera: "+ex;}
                });phase=8;wait=0;
            }else if(phase==8&&workerCameraReady&&++wait>100){
                capture(mc,"working-miner.png");phase=3;wait=0;
            }else if(phase==3&&DeepMineProbe.enabled()&&!stairsFramed){
                // AD-122: the owner sees the dug flight from the built stair — stairs, headroom, beam and lantern.
                stairsFramed=true;var server=mc.getSingleplayerServer();server.execute(()->{
                    try{DeepMineProbe.frameStairs(server);stairsCameraReady=true;}catch(Exception ex){failure="Stairs camera: "+ex;}
                });phase=10;wait=0;
            }else if(phase==10&&stairsCameraReady&&++wait>100){
                capture(mc,"shaft-stairs.png");phase=3;wait=0;
            }else if(phase==3 && Boolean.getBoolean("villageastra.designGallery") && galleryIndex<org.villageastra.world.BuildingBlueprints.designs().size()) {
                int index=galleryIndex;var design=org.villageastra.world.BuildingBlueprints.designs().stream().skip(index).findFirst().orElseThrow();
                var server=mc.getSingleplayerServer();
                int height=org.villageastra.world.BuildingBlueprints.layout(design.id(),BlockPos.ZERO).entrySet().stream().filter(e->!e.getValue().isAir()).mapToInt(e->e.getKey().getY()).max().orElse(8);
                double distance=Math.max(height,Math.max(design.width(),design.depth()))+5;
                server.execute(()->server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                        (index%6)*48+design.width()/2.0+distance,-60+height/2.0+distance*Math.sqrt(2)*Math.tan(Math.toRadians(25)),
                        (index/6)*48+design.depth()/2.0-distance,45,25));
                phase=7;wait=0;
            } else if(phase==7 && ++wait>80) {
                var design=org.villageastra.world.BuildingBlueprints.designs().stream().skip(galleryIndex).findFirst().orElseThrow();
                capture(mc,"design-"+design.id()+".png");galleryIndex++;phase=3;wait=0;
            } else if (phase == 3 && mc.screen == null && ++wait > 60) {
                capture(mc, "astra-world.png");
                mc.setScreen(new LocalSurveyScreen());
                phase = 4; wait = 0;
            } else if (phase == 4 && ++wait > 100) {
                capture(mc, "astra-isometry.png");
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                    var data = SettlementData.get(server);
                    long elapsed = data.clock().ticks() - startedTicks;
                    if (data.entries().isEmpty() || elapsed <= 0) {
                        failure = "No registered settlement or stopped simulation clock";
                    } else {
                        LogUtils.getLogger().info("ASTRA_SMOKE rendered world/isometry; active ticks advanced {}; settlements {}", elapsed, data.entries().size());
                        for (var entry : data.entries()) {
                            LogUtils.getLogger().info("ASTRA_SETTLEMENT {} residents={} homes={}",
                                entry.settlement().id(), entry.settlement().residents().size(), entry.settlement().homes().size());
                            long present = entry.settlement().residents().stream().filter(r -> server.overworld().getEntity(r.id()) != null).count();
                            int expectedResidents=Boolean.getBoolean("villageastra.researchSmoke")?7:6;
                            if (present != expectedResidents) { failure = "Expected "+expectedResidents+" physical residents; found " + present; return; }
                            for (var resident : entry.settlement().residents()) {
                                var entity = (org.villageastra.world.ResidentEntity) server.overworld().getEntity(resident.id());
                                if (entity.getCustomName() == null || !entity.getCustomName().getString().equals(resident.profile().name())
                                        || entity.skinVariant() != resident.profile().skin()) {
                                    failure = "Resident profile differs between registry and physical entity: " + resident.id(); return;
                                }
                            }
                            LogUtils.getLogger().info("ASTRA_PROFILES VERIFIED all saved personal names and skin variants match physical NPCs; reload={}",
                                    Boolean.getBoolean("villageastra.reloadSmoke"));
                            try { verifyNaturalLayout(server,entry); } catch(Exception ex){failure="Natural layout: "+ex;LogUtils.getLogger().error("ASTRA_SMOKE FAILED {}",failure);return;}
                            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) server.overworld().getBlockEntity(entry.center().offset(1,1,4));
                            int expectedStock = Boolean.getBoolean("villageastra.reloadSmoke") ? 41 : 48;
                            // AD-079: a deep-mine shift really spends hall stock on its support timber and counts every log of it itself,
                            // so this general "nothing spent" check does not apply to that scenario.
                            if (chest == null || !DeepMineProbe.enabled() && chest.getItem(0).getCount() != expectedStock) {
                                failure = "Initial stock lost or replenished"; return;
                            }
                            if (!Boolean.getBoolean("villageastra.reloadSmoke")) chest.removeItem(0,7);
                            if(Boolean.getBoolean("villageastra.reloadSmoke")) {
                                if(org.villageastra.world.HallUpgradeGoal.exists(server.overworld(),entry.settlement().id())){
                                    var project=org.villageastra.world.HallUpgradeGoal.inspect(server.overworld(),entry.settlement().id());
                                    if(project.getBoolean("complete")){
                                        int tier=project.getInt("level");
                                        if(entry.settlement().civilization().level()!=tier){failure="Completed hall lost civilization level on reload";return;}
                                        for(var cell:org.villageastra.world.BuildingBlueprints.layout("town_hall_"+tier,entry.center()).entrySet())
                                            if(!cell.getValue().isAir()&&server.overworld().getBlockState(cell.getKey()).getBlock()!=cell.getValue().getBlock()){failure="Completed hall geometry changed on reload";return;}
                                        LogUtils.getLogger().info("ASTRA_RELOAD_HALL VERIFIED completed geometry and civilization={}",tier);
                                    }
                                }
                                var builder=entry.settlement().residents().stream().filter(r -> r.profession()==org.villageastra.domain.Profession.BUILDER).findFirst().orElseThrow();
                                var worker=(org.villageastra.world.ResidentEntity)server.overworld().getEntity(builder.id());
                                if(worker.blockWork()!=null) {
                                    if(worker.blockWork().stage()!=org.villageastra.world.BlockWork.Stage.COMPLETE || chest.getItem(2).getCount()!=63 ||
                                            !server.overworld().getBlockState(entry.center().offset(8,1,4)).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) {
                                        failure="Completed block work changed after full restart"; return;
                                    }
                                    LogUtils.getLogger().info("ASTRA_RELOAD_BLOCKWORK VERIFIED completed operation persisted; stone remains 63");
                                }
                            }
                            LogUtils.getLogger().info("ASTRA_SMOKE VERIFIED {} physical NPCs; bread={}; reload={}",present,
                                    chest.getItem(0).getCount(),Boolean.getBoolean("villageastra.reloadSmoke"));
                        }
                    }
                    } finally { verificationDone=true; }
                });
                phase = 5; wait = 0;
            } else if (phase == 5 && verificationDone && ++wait > 30) { phase = 6; mc.stop(); }
        } catch (Exception ex) {
            LogUtils.getLogger().error("ASTRA_SMOKE FAILED", ex); phase = 99; mc.stop();
        }
    }
    private static void capture(Minecraft mc, String name) throws Exception {
        if (Boolean.getBoolean("villageastra.naturalSmoke")) name = "natural-" + name;
        if (Boolean.getBoolean("villageastra.reloadSmoke")) name = "reload-" + name;
        Path destination = mc.gameDirectory.toPath().resolve("screenshots").resolve(worldName).resolve(name);
        Files.createDirectories(destination.getParent());
        try (NativeImage frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { frame.writeToFile(destination); }
        LogUtils.getLogger().info("ASTRA_SMOKE screenshot {}", destination.toAbsolutePath());
    }

    /** The nursery spot the resource probe grows its oak on. */
    private static volatile BlockPos resourceSpot;private static volatile boolean resourceDelivered;

    private static void verifyNaturalLayout(net.minecraft.server.MinecraftServer server,SettlementData.Entry entry) throws Exception {
        var level=server.overworld();var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("astra-natural-check.json");
        if(!Boolean.getBoolean("villageastra.naturalSmoke")&&!Files.exists(file))return;
        var actual=new com.google.gson.JsonObject();var lots=new com.google.gson.JsonArray();
        for(var b:entry.settlement().buildings()){
            lots.add(b.id()+":"+b.type()+":"+b.x()+":"+b.y()+":"+b.z());
            if(!(level.getBlockEntity(entry.center().offset(b.x()+1,b.y()+1,b.z()+4)) instanceof net.minecraft.world.Container))throw new IllegalStateException("Missing physical building chest "+b.type());
        }actual.add("lots",lots);
        var roads=org.villageastra.world.NaturalVillage.conformRoads(org.villageastra.world.NaturalVillage.roads(entry.center(),entry.settlement()),entry.center(),entry.settlement(),
            p->level.getChunkSource().getGenerator().getFirstOccupiedHeight(p.getX(),p.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState()));
        boolean organic=org.villageastra.domain.OrganicLots.buildings(entry.settlement().id()).stream().allMatch(b->entry.settlement().buildings().stream().anyMatch(a->a.id().equals(b.id())&&a.x()==b.x()&&a.z()==b.z()));
        if(organic){java.util.function.ToIntFunction<BlockPos> terrain=p->level.getChunkSource().getGenerator().getFirstOccupiedHeight(p.getX(),p.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState());int version=entry.settlement().residents().stream().map(r->level.getEntity(r.id())).filter(entity->entity instanceof org.villageastra.world.ResidentEntity).mapToInt(entity->((org.villageastra.world.ResidentEntity)entity).bootstrapLayoutVersion()).max().orElse(2);roads=org.villageastra.world.NaturalVillage.generatedRoads(version,entry.center(),entry.settlement(),terrain);}
        var cells=new com.google.gson.JsonArray();
        for(var pos:roads.keySet()){
            if(!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.DIRT_PATH))throw new IllegalStateException("Missing initial road at "+pos+" actual "+level.getBlockState(pos)+" above="+level.getBlockState(pos.above())+" above2="+level.getBlockState(pos.above(2)));
            cells.add(pos.asLong());
        }actual.add("roads",cells);
        if(Boolean.getBoolean("villageastra.reloadSmoke")){
            if(!com.google.gson.JsonParser.parseString(Files.readString(file)).equals(actual))throw new IllegalStateException("Lot or road coordinates changed after restart");
        }else Files.writeString(file,new com.google.gson.Gson().toJson(actual));
        LogUtils.getLogger().info("ASTRA_NATURAL_LAYOUT VERIFIED seven physical buildings and {} graded road cells; reload={}",roads.size(),Boolean.getBoolean("villageastra.reloadSmoke"));
        if(!Boolean.getBoolean("villageastra.reloadSmoke"))FarmFieldCheck.verify(level,entry);
    }

    private static void setupResearch(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var data=SettlementData.get(server);var entry=data.entries().iterator().next();var s=entry.settlement();
        var home=org.villageastra.domain.Settlement.childId(s.id(),"harness/research-home");var lab=org.villageastra.domain.Settlement.childId(s.id(),"harness/laboratory");
        org.villageastra.world.BuildingBlueprints.preview(level,"home",entry.center().offset(-12,0,0));
        org.villageastra.world.BuildingBlueprints.preview(level,"laboratory",entry.center().offset(-16,0,12));
        s.addHome(new org.villageastra.domain.Settlement.Home(home,1,2,true));
        s.addBuilding(new org.villageastra.domain.Settlement.Building(home,"home",-12,0,0));s.addBuilding(new org.villageastra.domain.Settlement.Building(lab,"laboratory",-16,0,12));
        var student=new org.villageastra.domain.Resident(org.villageastra.domain.Settlement.childId(s.id(),"harness/scientist"),org.villageastra.domain.Resident.Life.CHILD,false,null,null,-1);
        s.admit(student,home);student.educate();student.growUp();s.assign(student.id(),org.villageastra.domain.Profession.SCIENTIST,lab);
        var entity=VillageAstra.RESIDENT.get().create(level);entity.bind(s.id(),student);entity.moveTo(entry.center().getX()-9.5,entry.center().getY()+1,entry.center().getZ()+2.5,0,0);level.addFreshEntity(entity);
        // Known near-complete fixture tests physical completion, not the full school or 12,000-tick duration.
        s.restoreCivilization(org.villageastra.domain.Civilization.restore(1,java.util.List.of(),"cartography",11920));
        ((net.minecraft.world.Container)level.getBlockEntity(entry.center().offset(-15,1,16))).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));data.setDirty();
        LogUtils.getLogger().info("ASTRA_RESEARCH fixture: educated adult, laboratory, one paper, cartography labor 11920/12000");
    }

    private static void fundHall(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();
                            var entry=SettlementData.get(server).entries().iterator().next();
                            if(org.villageastra.world.HallUpgradeGoal.pending(level,entry.settlement().id())){
                                LogUtils.getLogger().info("ASTRA_HALL resuming existing funded fixture without adding materials");return;
                            }
                            org.villageastra.world.HallUpgradeGoal.request(level,entry);
                            var cost=org.villageastra.world.HallUpgradeGoal.inspect(level,entry.settlement().id()).getCompound("cost");
                            var chest=(net.minecraft.world.Container)level.getBlockEntity(entry.center().offset(1,1,4));int slot=12;
                            for(String key:cost.getAllKeys()){
                                var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key));int left=cost.getInt(key);
                                while(left>0){if(slot>=chest.getContainerSize())throw new IllegalStateException("Harness funding chest too small");int count=Math.min(64,left);chest.setItem(slot++,new net.minecraft.world.item.ItemStack(item,count));left-=count;}
                            }
                            LogUtils.getLogger().info("ASTRA_HALL queued funded upgrade with {}",cost);
    }
}

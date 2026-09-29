package org.villageastra.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.StarterVillage;

@GameTestHolder(VillageAstra.ID)
@PrefixGameTestTemplate(false)
public final class SettlementGameTests {
    private SettlementGameTests() {}
    @GameTest(template="empty",timeoutTicks=100)
    public static void residentOpensDoorFromLateralApproach(GameTestHelper helper){
        var level=helper.getLevel();var base=helper.absolutePos(new BlockPos(2,3,2));
        for(int x=0;x<9;x++)for(int z=0;z<12;z++)level.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        for(int x=0;x<9;x++)for(int y=1;y<=2;y++)level.setBlock(base.offset(x,y,5),Blocks.STONE.defaultBlockState(),2);
        var door=base.offset(4,1,5);var state=Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,net.minecraft.core.Direction.SOUTH);
        level.setBlock(door,state,2);level.setBlock(door.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        var npc=VillageAstra.RESIDENT.get().create(level);npc.moveTo(base.getX()+4.81,base.getY()+1,base.getZ()+6.3);npc.setOnGround(true);npc.setNoAi(true);level.addFreshEntity(npc);
        var path=npc.getNavigation().createPath(base.offset(4,1,2),0);helper.assertTrue(path!=null&&path.canReach(),"Real route crosses doorway");npc.getNavigation().moveTo(path,.8);
        var goal=new org.villageastra.world.ResidentDoorGoal(npc);helper.assertTrue(goal.canUse(),"Door detected before frontal collision from lateral approach");goal.start();
        helper.assertTrue(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Door physically opened");goal.stop();
        helper.assertTrue(!level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Door closes after passage");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void undergroundMineProtectionPersistsAndMigrates(GameTestHelper helper){
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(2,15,2));var s=StarterVillage.create(level,origin);
        var mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();s.noteMine(mine.id(),10,3);
        var base=origin.offset(mine.x(),mine.y(),mine.z());
        helper.assertTrue(org.villageastra.server.OwnershipEvents.disallowedPlacement(level,base.offset(8,-10,17)),"Underground wall plus three blocks protected");
        helper.assertTrue(!org.villageastra.server.OwnershipEvents.disallowedPlacement(level,base.offset(9,-10,17)),"Fourth block beyond underground wall remains outside");
        var data=new SettlementData();data.add(new SettlementData.Entry(s,"minecraft:overworld",origin));var saved=data.save(new CompoundTag());
        var copy=SettlementData.load(saved).entry(s.id()).settlement();helper.assertTrue(copy.mineAreas().equals(s.mineAreas()),"Mine extent survives a save round trip");
        saved.putInt("schema",4);for(var tag:saved.getList("settlements",net.minecraft.nbt.Tag.TAG_COMPOUND))((CompoundTag)tag).remove("mines");
        helper.assertTrue(SettlementData.load(saved).entry(s.id()).settlement().mineAreas().isEmpty(),"Schema four migrates without inventing excavations");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void displayedWorkCargoCannotDuplicateEquipment(GameTestHelper helper){
        var entity=VillageAstra.RESIDENT.get().create(helper.getLevel());
        var cargo=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG,32);
        entity.displayWorkItem(cargo);
        helper.assertTrue(cargo.getCount()==32&&entity.displayedWorkItem().getCount()==1,"Display does not mutate canonical cargo");
        helper.assertTrue(entity.getMainHandItem().isEmpty()&&entity.getOffhandItem().isEmpty(),"Display does not become lootable equipment");
        var nbt=entity.saveWithoutId(new CompoundTag());
        for(var tag:nbt.getList("HandItems",net.minecraft.nbt.Tag.TAG_COMPOUND))
            helper.assertTrue(net.minecraft.world.item.ItemStack.of((CompoundTag)tag).isEmpty(),"No second inventory copy in entity save");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void gradedRoadRemainsAPathAfterCuttingTheGround(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(2,2,2));
        for(int y=0;y<=4;y++)level.setBlock(pos.above(y),Blocks.DIRT.defaultBlockState(),3);
        org.villageastra.world.NaturalVillage.placeInitialRoad(level,pos,Blocks.DIRT_PATH.defaultBlockState(),pos.getY()+4);
        helper.runAfterDelay(5,()->{
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT_PATH),"Path must not schedule conversion to dirt during grading");
            helper.assertTrue(level.getBlockState(pos.above(4)).isAir(),"Cut hillside clears headroom through the old surface");helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void initialRoadsFollowGroundAndRemainWalkable(GameTestHelper helper){
        var s=Settlement.natural(java.util.UUID.randomUUID());var origin=new BlockPos(0,70,0);
        var flat=org.villageastra.world.NaturalVillage.roads(origin,s);
        var graded=org.villageastra.world.NaturalVillage.conformRoads(flat,origin,s,p->66);
        var heights=new java.util.HashMap<BlockPos,Integer>();graded.keySet().forEach(p->heights.put(new BlockPos(p.getX(),0,p.getZ()),p.getY()));
        helper.assertTrue(graded.size()==flat.size(),"Grading preserves every road cell");
        helper.assertTrue(graded.keySet().stream().anyMatch(p->p.getY()==66),"Long roads reach actual ground instead of elevated walls");
        for(var b:s.buildings()){
            var d=org.villageastra.world.BuildingBlueprints.design(b.type());
            helper.assertTrue(heights.get(new BlockPos(b.x()+org.villageastra.world.BuildingBlueprints.doorX(b.type()),0,b.z()-1))==70,"Entrances meet building floor");
        }
        for(var p:heights.keySet())for(var q:java.util.List.of(p.north(),p.east(),p.south(),p.west()))
            if(heights.containsKey(q))helper.assertTrue(Math.abs(heights.get(p)-heights.get(q))<=1,"Walkable height transition");
        var hillside=org.villageastra.world.NaturalVillage.conformRoads(flat,origin,s,p->74);
        for(var b:s.buildings()){
            var d=org.villageastra.world.BuildingBlueprints.design(b.type());
            helper.assertTrue(hillside.containsKey(origin.offset(b.x()+org.villageastra.world.BuildingBlueprints.doorX(b.type()),b.y(),b.z()-1)),"Higher nearby terrain cannot lift the road above an entrance");
        }
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void naturalRoadsConnectEveryEntranceAndPersistLots(GameTestHelper helper){
        var s=Settlement.natural(java.util.UUID.randomUUID());var roads=org.villageastra.world.NaturalVillage.roads(BlockPos.ZERO,s);
        java.util.Set<BlockPos> reached=new java.util.HashSet<>();java.util.Queue<BlockPos> queue=new java.util.ArrayDeque<>();queue.add(new BlockPos(3,0,-1));
        while(!queue.isEmpty()){var p=queue.remove();if(!roads.containsKey(p)||!reached.add(p))continue;queue.add(p.north());queue.add(p.south());queue.add(p.east());queue.add(p.west());}
        for(var b:s.buildings()){
            var d=org.villageastra.world.BuildingBlueprints.design(b.type());helper.assertTrue(reached.contains(new BlockPos(b.x()+org.villageastra.world.BuildingBlueprints.doorX(b.type()),0,b.z()-1)),"Every entrance connected by actual planned path cells");
            for(int x=0;x<d.width();x++)for(int z=0;z<d.depth();z++)helper.assertTrue(!roads.containsKey(new BlockPos(b.x()+x,0,b.z()+z)),"Road does not cut through a building");
        }
        var data=new SettlementData();data.add(new SettlementData.Entry(s,"minecraft:overworld",BlockPos.ZERO));var copy=SettlementData.load(data.save(new CompoundTag())).entry(s.id()).settlement();
        helper.assertTrue(java.util.List.copyOf(copy.buildings()).equals(java.util.List.copyOf(s.buildings())),"Irregular locations persist exactly");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void placementBufferIncludesInteriorAndThreeBlocks(GameTestHelper helper){
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(4,5,4));StarterVillage.create(level,origin);
        helper.assertTrue(org.villageastra.server.OwnershipEvents.disallowedPlacement(level,origin.offset(3,2,3)),"Interior protected");
        helper.assertTrue(org.villageastra.server.OwnershipEvents.disallowedPlacement(level,origin.west(3)),"Three block border included");
        helper.assertTrue(!org.villageastra.server.OwnershipEvents.disallowedPlacement(level,origin.west(4)),"Fourth block outside border allowed");
        helper.assertTrue(org.villageastra.server.OwnershipEvents.disallowedPlacement(level,origin.below(3)),"Vertical buffer included");
        helper.assertTrue(!org.villageastra.server.OwnershipEvents.disallowedPlacement(level,origin.below(4)),"No infinite vertical column");
        var event=new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,origin.west(3)),Blocks.AIR.defaultBlockState(),helper.makeMockPlayer());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);helper.assertTrue(event.isCanceled(),"Forge player placement event cancelled");helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void finiteHarvestAndDeliveryReplay(GameTestHelper helper){
        var level=helper.getLevel();var rock=helper.absolutePos(new BlockPos(2,2,2));var chestPos=rock.east(3);
        level.setBlock(rock,Blocks.STONE.defaultBlockState(),3);level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
        var id=java.util.UUID.randomUUID();var pick=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE);
        var loot=org.villageastra.persistence.WorldJournal.harvest(level,id,rock,Blocks.STONE.defaultBlockState(),pick);
        helper.assertTrue(level.getBlockState(rock).isAir()&&loot.size()==1&&loot.get(0).is(net.minecraft.world.item.Items.COBBLESTONE),"Finite stone mined with correct loot");
        var replay=org.villageastra.persistence.WorldJournal.harvest(level,id,rock,Blocks.STONE.defaultBlockState(),pick);
        helper.assertTrue(net.minecraft.world.item.ItemStack.matches(loot.get(0),replay.get(0)),"Receipt returns same cargo after stale work state");
        var delivery=java.util.UUID.randomUUID();
        helper.assertTrue(org.villageastra.persistence.WorldJournal.deposit(level,delivery,chestPos,loot.get(0)),"Delivery accepted");
        helper.assertTrue(org.villageastra.persistence.WorldJournal.deposit(level,delivery,chestPos,loot.get(0)),"Delivery replay accepted");
        helper.assertTrue(((net.minecraft.world.Container)level.getBlockEntity(chestPos)).countItem(net.minecraft.world.item.Items.COBBLESTONE)==1,"Replay cannot duplicate delivered resource");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void civilizationSurvivesRegistryReload(GameTestHelper helper){
        var s=Settlement.initial(java.util.UUID.randomUUID());s.civilization().completedHallUpgrade(2);
        s.civilization().begin("cartography");s.civilization().work(80,true,true);
        var data=new SettlementData();data.add(new SettlementData.Entry(s,"minecraft:overworld",BlockPos.ZERO));
        var copy=SettlementData.load(data.save(new CompoundTag())).entry(s.id()).settlement().civilization();
        helper.assertTrue(copy.level()==2&&copy.progress()==80&&copy.active().equals("cartography"),"Hall level and unfinished research persist");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void catalogueMaterialCounts(GameTestHelper helper) {
        for (var design : org.villageastra.world.BuildingBlueprints.designs()) {
            var cost=org.villageastra.world.BlueprintMaterials.count(design.id());
            helper.assertTrue(!cost.containsKey("minecraft:air") && cost.values().stream().allMatch(n->n>0), "Only real positive materials");
            // AD-121: the castle has several chests and dark-oak doors; counts belong to the actual design, not the old cottage.
            var cells=org.villageastra.world.BuildingBlueprints.layout(design.id(),net.minecraft.core.BlockPos.ZERO).values();
            long chests=cells.stream().filter(st->st.is(VillageAstra.OWNED_CHEST.get())||st.is(net.minecraft.world.level.block.Blocks.CHEST)).count();
            helper.assertTrue(cost.getOrDefault("minecraft:chest",0L)==chests, design.id()+": one item per chest block, expected "+chests+", got "+cost.getOrDefault("minecraft:chest",0L));
            var doors=new java.util.TreeMap<String,Long>();
            for(var st:cells)if(st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock&&st.getValue(net.minecraft.world.level.block.DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                doors.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getBlock().asItem()).toString(),1L,Long::sum);
            for(var door:doors.entrySet())helper.assertTrue(cost.getOrDefault(door.getKey(),0L).equals(door.getValue()),design.id()+": one item per whole "+door.getKey()+", expected "+door.getValue());
        }
        var home=org.villageastra.world.BlueprintMaterials.count("home");
        helper.assertTrue(home.get("minecraft:white_bed")==2 && home.get("minecraft:cobblestone")==49, "Two whole beds and one 7x7 foundation");
        // Design review 2026-09-24 (AD-148): the cottage holds more than one pot now; each potted flower still costs its pot and its flower.
        var homeCells=org.villageastra.world.BuildingBlueprints.layout("home",net.minecraft.core.BlockPos.ZERO).values();
        long pots=homeCells.stream().filter(st->st.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock).count(),poppies=homeCells.stream().filter(st->st.is(net.minecraft.world.level.block.Blocks.POTTED_POPPY)).count();
        helper.assertTrue(pots>=1 && home.get("minecraft:flower_pot")==pots && home.get("minecraft:poppy")==poppies, "Potted flower requires both items: "+pots+" pots, "+poppies+" poppies, "+home);
        helper.assertTrue(org.villageastra.world.BlueprintMaterials.count("home_2").get("minecraft:white_bed")==4, "Larger home has four whole beds");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void catalogueAndNamedSkinsPersist(GameTestHelper helper) {
        for(Profession role:Profession.values()) {
            var design=org.villageastra.world.BuildingBlueprints.design(role.workplace());
            var cells=org.villageastra.world.BuildingBlueprints.layout(design.id(),BlockPos.ZERO);
            helper.assertTrue(cells.size()>100,"Every professional workplace has actual block geometry");
            helper.assertTrue(cells.values().stream().anyMatch(state->state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) || (cells.get(new BlockPos(design.width()/2,1,0)).getCollisionShape(helper.getLevel(),BlockPos.ZERO).isEmpty() && cells.get(new BlockPos(design.width()/2,2,0)).isAir()),"Every workplace has a door or an open two-block-high entrance");
        }
        helper.assertTrue(org.villageastra.world.BuildingBlueprints.design("home_2").width()>org.villageastra.world.BuildingBlueprints.design("home").width(),"Second house level is physically larger");
        var settlement=Settlement.initial(java.util.UUID.randomUUID());
        var person=settlement.residents().iterator().next();
        var npc=VillageAstra.RESIDENT.get().create(helper.getLevel());npc.bind(settlement.id(),person);
        helper.assertTrue(npc.getCustomName().getString().equals(person.profile().name()),"Personal name replaces profession label");
        var tag=new CompoundTag();npc.saveWithoutId(tag);
        var copy=VillageAstra.RESIDENT.get().create(helper.getLevel());copy.load(tag);
        helper.assertTrue(copy.skinVariant()==npc.skinVariant() && copy.getCustomName().equals(npc.getCustomName()),"Name and skin survive entity reload");
        var data=new SettlementData();data.add(new SettlementData.Entry(settlement,"minecraft:overworld",BlockPos.ZERO));
        var restored=SettlementData.load(data.save(new CompoundTag()));
        helper.assertTrue(restored.entry(settlement.id()).settlement().resident(person.id()).profile().equals(person.profile()),"Registry profile persists");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ruinedRoofInvalidatesHomeDespiteIntactBeds(GameTestHelper helper) {
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(2,2,2));
        var settlement=StarterVillage.create(level,origin);var data=SettlementData.get(level.getServer());
        var base=origin.offset(10,0,0);
        helper.assertTrue(org.villageastra.server.BuildingIntegrity.home(level,base)==org.villageastra.server.BuildingIntegrity.Result.USABLE,"Starter house initially usable");
        for(int x=0;x<3;x++)for(int z=0;z<7;z++)level.setBlock(base.offset(x,4,z),Blocks.AIR.defaultBlockState(),2);
        org.villageastra.server.HousingMonitor.inspect(level.getServer(),data,data.entry(settlement.id()));
        helper.assertTrue(settlement.residents().stream().filter(r->r.home()==null).count()==2,"Roof loss invalidates occupied house even with surviving beds");
        helper.assertTrue(level.getBlockState(base.offset(2,1,3)).getBlock() instanceof net.minecraft.world.level.block.BedBlock,"Beds really remain");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void poweredPistonCannotMoveVillageButWorksOutside(GameTestHelper helper) {
        var level=helper.getLevel();var origin=helper.absolutePos(new BlockPos(2,2,2));StarterVillage.create(level,origin);
        var direction=net.minecraft.core.Direction.EAST;
        var state=Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,direction);
        level.setBlockAndUpdate(origin.west(),state);level.setBlockAndUpdate(origin.west(2),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var outside=origin.offset(40,0,4);level.setBlockAndUpdate(outside.east(),Blocks.COBBLESTONE.defaultBlockState());
        level.setBlockAndUpdate(outside,state);level.setBlockAndUpdate(outside.west(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(10,()-> {
            helper.assertTrue(!level.getBlockState(origin.west()).getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED),"Real powered piston cannot move structural block");
            helper.assertTrue(level.getBlockState(origin).is(Blocks.COBBLESTONE),"Village foundation unchanged");
            helper.assertTrue(level.getBlockState(outside).getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED)
                    && level.getBlockState(outside.east(2)).is(Blocks.COBBLESTONE),"Ordinary piston outside village still works");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void staleWorkerSaveReplaysReceiptsWithoutDoubleDebit(GameTestHelper helper) {
        var level=helper.getLevel();var source=helper.absolutePos(new BlockPos(3,3,3));var target=source.offset(2,0,0);
        level.setBlockAndUpdate(source,VillageAstra.OWNED_CHEST.get().defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(source);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,2));
        var npc=VillageAstra.RESIDENT.get().create(level);npc.moveTo(source.getX()+1,source.getY(),source.getZ()+1);
        var job=new org.villageastra.world.BlockWork(level.dimension().location().toString(),source,target,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState());
        CompoundTag stale=job.save();
        for(int n=0;n<25;n++)job.step(npc,true);
        helper.assertTrue(job.stage()==org.villageastra.world.BlockWork.Stage.COMPLETE,"Original operation completed");
        var replay=org.villageastra.world.BlockWork.load(stale);
        for(int n=0;n<25;n++)replay.step(npc,true);
        helper.assertTrue(replay.stage()==org.villageastra.world.BlockWork.Stage.COMPLETE,"Old NPC checkpoint reconciled with world receipts");
        helper.assertTrue(chest.getItem(0).getCount()==1 && replay.carried().isEmpty(),"Exactly one material debit after stale checkpoint replay");
        helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE),"Exactly one block placed");
        var receipts=org.villageastra.persistence.ChunkReceipts.of(level.getChunkAt(source));
        var restored=new org.villageastra.persistence.ChunkReceipts();restored.deserializeNBT(receipts.serializeNBT());
        helper.assertTrue(restored.contains(Settlement.childId(job.id(),"fetch")),"Receipt capability is serializable with chunk");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void ownedChestAttributionAndAutomation(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(5,3,5));
        level.setBlockAndUpdate(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState());
        var chest=(org.villageastra.world.OwnedChestEntity)level.getBlockEntity(pos);
        var village=java.util.UUID.randomUUID();
        chest.getPersistentData().putUUID("AstraSettlement",village);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,8));
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"PropertyTest"));
        var ledger=org.villageastra.server.PropertyLedger.get(level.getServer());
        var menu=new org.villageastra.world.OwnedChestMenu(1,player.getInventory(),chest);
        helper.assertTrue(ledger.stolen(village,player.getUUID())==0,"Viewing is not theft");
        menu.clicked(0,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
        helper.assertTrue(menu.getCarried().getCount()==4 && ledger.stolen(village,player.getUUID())==4,"Half-stack pickup attributed exactly");
        menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        helper.assertTrue(ledger.stolen(village,player.getUUID())==4,"Returning items does not erase or repeat crime");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,player);
        helper.assertTrue(ledger.stolen(village,player.getUUID())==8,"Quick move attributed");
        var restored=org.villageastra.server.PropertyLedger.load(ledger.save(new CompoundTag()));
        helper.assertTrue(restored.reputationPenalty(village,player.getUUID())==-8,"Penalty persists for same player and settlement");
        helper.assertTrue(restored.stolen(java.util.UUID.randomUUID(),player.getUUID())==0,"Independent village reputation");
        var handler=chest.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("Missing capability"));
        helper.assertTrue(handler.extractItem(1,4,false).isEmpty(),"Unknown capability extraction denied");
        level.setBlockAndUpdate(pos.below(),Blocks.HOPPER.defaultBlockState());
        helper.runAfterDelay(40,()->{
            var hopper=(net.minecraft.world.level.block.entity.HopperBlockEntity)level.getBlockEntity(pos.below());
            helper.assertTrue(hopper.isEmpty() && chest.getItem(1).getCount()==4,"Real ticking hopper cannot steal");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyChestMigrationKeepsItemsWithoutDrops(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(new BlockPos(5,3,5));
        level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(pos); var village=java.util.UUID.randomUUID();
        chest.getPersistentData().putUUID("AstraSettlement",village);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        org.villageastra.server.OwnershipEvents.upgradeLegacyChest(level,pos);
        var replacement=(org.villageastra.world.OwnedChestEntity)level.getBlockEntity(pos);
        helper.assertTrue(replacement.getItem(0).getCount()==7 && replacement.getPersistentData().getUUID("AstraSettlement").equals(village),"Legacy chest owner and exact stock retained");
        helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(),"Migration does not spill duplicate items");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void independentSettlementsAndWorkplaces(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(2,2,2));
        var secondOrigin=origin.above(18);
        var first=StarterVillage.create(level,origin);
        var second=StarterVillage.create(level,secondOrigin);
        helper.assertTrue(!first.id().equals(second.id()),"Different settlements have separate identities");
        var seen=new java.util.HashSet<java.util.UUID>();
        for(var settlement:java.util.List.of(first,second)) {
            helper.assertTrue(settlement.buildings().size()==7,"Seven owned buildings per settlement");
            for(var person:settlement.residents()) {
                helper.assertTrue(seen.add(person.id()),"No shared resident identity");
                helper.assertTrue(level.getEntity(person.id())!=null,"All twelve physical entities exist");
                helper.assertTrue(settlement.workplace(person.id())!=null,"Every starter has a specific workplace");
            }
            var builder=settlement.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();
            var porter=settlement.residents().stream().filter(r->r.profession()==Profession.PORTER).findFirst().orElseThrow();
            helper.assertTrue(settlement.workplace(builder.id()).id().equals(settlement.workplace(porter.id()).id()),"Builder and temporary porter share town hall");
        }
        var chest=(ChestBlockEntity)level.getBlockEntity(origin.offset(1,1,4));
        var other=(ChestBlockEntity)level.getBlockEntity(secondOrigin.offset(1,1,4));
        chest.removeItem(0,7);
        helper.assertTrue(other.getItem(0).getCount()==48,"Other village stock unaffected by removal");
        first.damageHome(first.residents().iterator().next().home(),5);
        helper.assertTrue(second.residents().stream().allMatch(r->r.home()!=null),"Other village housing unaffected");
        var data=SettlementData.get(level.getServer());
        var saved=data.save(new CompoundTag());
        var restored=SettlementData.load(saved);
        for(var original:java.util.List.of(first,second)) {
            var copy=restored.entry(original.id()).settlement();
            helper.assertTrue(copy.buildings().equals(original.buildings()) || copy.buildings().stream().toList().equals(original.buildings().stream().toList()),"Building identities and positions round trip");
            for(var person:original.residents()) helper.assertTrue(copy.workplace(person.id()).equals(original.workplace(person.id())),"Assignment round trip");
        }
        saved.putInt("schema",1);
        for(var raw:saved.getList("settlements",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)raw; tag.remove("buildings");
            for(var person:tag.getList("residents",net.minecraft.nbt.Tag.TAG_COMPOUND)) ((CompoundTag)person).remove("workplace");
        }
        var migrated=SettlementData.load(saved);
        helper.assertTrue(migrated.isDirty(),"Migrated schema will be saved");
        // AD-131: a schema-1 save was built with the mine at x 24, where the migration registers it; the new starter's stands at x 32.
        for(var person:first.residents()) helper.assertTrue(migrated.entry(first.id()).settlement().workplace(person.id()).id().equals(first.workplace(person.id()).id()),"Legacy starter assignments migrate deterministically");
        helper.assertTrue(migrated.entry(first.id()).settlement().buildings().stream().anyMatch(b->b.type().equals("mine")&&b.x()==24),"A schema-1 mine is registered where it was built");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oldResidentNavigationMigratesWithoutChangingIdentity(GameTestHelper helper) {
        var npc=VillageAstra.RESIDENT.get().create(helper.getLevel());
        var id=npc.getUUID();
        npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(32);
        var tag=new CompoundTag(); npc.saveWithoutId(tag); tag.remove("AstraEntitySchema");
        var restored=VillageAstra.RESIDENT.get().create(helper.getLevel()); restored.load(tag);
        helper.assertTrue(restored.getUUID().equals(id),"Migration preserves identity");
        helper.assertTrue(restored.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)==64,"Legacy path range migrated");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fundedBlockWorkAndCheckpoint(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos source=helper.absolutePos(new BlockPos(3,3,3));
        BlockPos target=source.offset(2,0,0);
        level.setBlockAndUpdate(source,Blocks.CHEST.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(source);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,2));
        var worker=VillageAstra.RESIDENT.get().create(level);
        worker.moveTo(source.getX()+1,source.getY(),source.getZ()+1);
        level.addFreshEntity(worker);
        var job=new org.villageastra.world.BlockWork(level.dimension().location().toString(),source,target,
                level.getBlockState(target),Blocks.COBBLESTONE.defaultBlockState());
        for(int i=0;i<30;i++)job.step(worker,false);
        helper.assertTrue(chest.getItem(0).getCount()==2 && level.getBlockState(target).isAir(),"No work during inactive simulation");
        job.step(worker,true);
        helper.assertTrue(chest.getItem(0).getCount()==1 && job.carried().getCount()==1,"One item moved from real chest into carried stack");
        var restored=org.villageastra.world.BlockWork.load(job.save());
        helper.assertTrue(restored.id().equals(job.id()),"Operation ID survives checkpoint");
        for(int i=0;i<30;i++)restored.step(worker,true);
        helper.assertTrue(level.getBlockState(target).is(Blocks.COBBLESTONE),"Paid block installed in world");
        helper.assertTrue(restored.carried().isEmpty() && chest.getItem(0).getCount()==1,"Material consumed once");
        var completed=org.villageastra.world.BlockWork.load(restored.save());
        for(int i=0;i<30;i++)completed.step(worker,true);
        helper.assertTrue(chest.getItem(0).getCount()==1,"Completion replay cannot consume again");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void changedBlockStopsWorkerWithoutLosingCargo(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos source=helper.absolutePos(new BlockPos(3,3,3));
        BlockPos target=source.offset(2,0,0);
        level.setBlockAndUpdate(source,Blocks.CHEST.defaultBlockState());
        var chest=(ChestBlockEntity)level.getBlockEntity(source);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        var worker=VillageAstra.RESIDENT.get().create(level);
        worker.moveTo(source.getX()+1,source.getY(),source.getZ()+1);
        var job=new org.villageastra.world.BlockWork(level.dimension().location().toString(),source,target,
                level.getBlockState(target),Blocks.COBBLESTONE.defaultBlockState());
        job.step(worker,true);
        level.setBlockAndUpdate(target,Blocks.DIAMOND_BLOCK.defaultBlockState());
        for(int i=0;i<30;i++)job.step(worker,true);
        helper.assertTrue(job.stage()==org.villageastra.world.BlockWork.Stage.CONFLICT,"Stale survey produces a conflict");
        helper.assertTrue(job.carried().getCount()==1,"Cargo stays with worker");
        helper.assertTrue(level.getBlockState(target).is(Blocks.DIAMOND_BLOCK),"Player change preserved");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void corruptSaveIsNotReinterpretedAsEmpty(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 99);
        boolean futureRejected = false;
        try { SettlementData.load(tag); } catch (IllegalArgumentException expected) { futureRejected = true; }
        helper.assertTrue(futureRejected, "Future schema rejected");
        tag.putInt("schema",1);
        boolean incompleteRejected = false;
        try { SettlementData.load(tag); } catch (IllegalArgumentException expected) { incompleteRejected = true; }
        helper.assertTrue(incompleteRejected, "Incomplete save rejected");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenBedsStartPersistentHousingEpisode(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(2,2,2));
        var settlement = StarterVillage.create(helper.getLevel(),origin);
        var data = SettlementData.get(helper.getLevel().getServer());
        var resident = settlement.residents().iterator().next();
        java.util.UUID home = resident.home();
        helper.getLevel().destroyBlock(origin.offset(12,1,3), false);
        org.villageastra.server.HousingMonitor.inspect(helper.getLevel().getServer(),data,data.entry(settlement.id()));
        helper.assertTrue(resident.home() == null, "Destroyed real bed invalidates house");
        long since = resident.homelessSince();
        helper.assertTrue(since >= 0, "Housing episode started");
        helper.assertTrue(settlement.residents().stream().filter(r -> r.home() == null).count() == 2, "Both occupants lose unusable home");
        data.activeTick(true);
        org.villageastra.server.HousingMonitor.inspect(helper.getLevel().getServer(),data,data.entry(settlement.id()));
        helper.assertTrue(resident.homelessSince() == since, "Repeated check does not reset deadline");
        helper.assertTrue(!settlement.homes().stream().filter(h -> h.id().equals(home)).findFirst().orElseThrow().usable(), "House remains unusable");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void generationRegistriesAndProtection(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (String biome : new String[]{"plains", "desert", "savanna", "snowy", "taiga"})
            helper.assertTrue(registry.get(new net.minecraft.resources.ResourceLocation("minecraft", "village_" + biome))
                    instanceof org.villageastra.world.SettlementStructure, "Vanilla village replaced: " + biome);
        helper.assertTrue(registry.get(new net.minecraft.resources.ResourceLocation("minecraft", "desert_pyramid")) != null,
                "Other structures preserved");
        var origin = helper.absolutePos(new BlockPos(2,2,2));
        StarterVillage.create(helper.getLevel(),origin);
        helper.assertTrue(org.villageastra.server.OwnershipEvents.protectedBlock(helper.getLevel(),origin), "Structural block protected");
        var breakEvent = new net.minecraftforge.event.level.BlockEvent.BreakEvent(helper.getLevel(),origin,
                helper.getLevel().getBlockState(origin),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(breakEvent);
        helper.assertTrue(breakEvent.isCanceled(), "Manual break event cancelled for a real registered building");
        helper.assertTrue(!org.villageastra.server.OwnershipEvents.protectedBlock(helper.getLevel(),origin.offset(8,0,8)), "No area-wide invulnerability");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void boatAndEntitySave(GameTestHelper helper) {
        var level = helper.getLevel();
        var npc = VillageAstra.RESIDENT.get().create(level);
        var resident = Settlement.initial(java.util.UUID.randomUUID()).residents().iterator().next();
        var village = java.util.UUID.randomUUID();
        npc.bind(village, resident);
        npc.escort(java.util.UUID.randomUUID());
        var pos = helper.absolutePos(new BlockPos(4,3,4));
        npc.moveTo(pos.getX(),pos.getY(),pos.getZ());
        level.addFreshEntity(npc);
        var boat = new net.minecraft.world.entity.vehicle.Boat(level,pos.getX(),pos.getY(),pos.getZ());
        level.addFreshEntity(boat);
        helper.assertTrue(npc.startRiding(boat), "Resident can board real boat");
        helper.assertTrue(boat.getPassengers().contains(npc), "Single physical passenger");
        var tag = new CompoundTag();
        npc.saveWithoutId(tag);
        var copy = VillageAstra.RESIDENT.get().create(level);
        copy.load(tag);
        helper.assertTrue(copy.getUUID().equals(npc.getUUID()), "Identity round trip");
        helper.assertTrue(copy.settlementId().equals(village), "Settlement round trip");
        helper.assertTrue(copy.escortPlayer().equals(npc.escortPlayer()), "Escort owner round trip");
        // The copy is intentionally not added to the world.
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativePortalRetainsIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var npc = VillageAstra.RESIDENT.get().create(level);
        var s = Settlement.initial(java.util.UUID.randomUUID());
        npc.bind(s.id(), s.residents().iterator().next());
        var owner = java.util.UUID.randomUUID();
        npc.escort(owner);
        var pos = helper.absolutePos(new BlockPos(8,3,8));
        npc.moveTo(pos.getX() + 0.5,pos.getY(),pos.getZ() + 0.5);
        npc.setNoAi(true);
        level.addFreshEntity(npc);
        java.util.UUID identity = npc.getUUID();
        var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos destination = new BlockPos(Math.floorDiv(pos.getX(), 8), 70, Math.floorDiv(pos.getZ(), 8));
        // Ordinary entities need an existing destination portal; only players create a new exit.
        portal(level, pos.offset(-1,-1,0));
        portal(nether, destination.offset(-1,-1,0));
        // AD-106: an escorted resident goes through a portal only behind its player, by the note the player's crossing leaves.
        npc.recordPortal(pos, level.dimension(), net.minecraft.world.level.Level.NETHER, level.getGameTime() + 600);
        helper.runAfterDelay(5, () -> npc.handleInsidePortal(pos));
        helper.succeedWhen(() -> {
            var arrived = nether.getEntity(identity);
            helper.assertTrue(arrived instanceof org.villageastra.world.ResidentEntity, "NPC crossed native portal");
            var resident = (org.villageastra.world.ResidentEntity) arrived;
            helper.assertTrue(resident.settlementId().equals(s.id()), "Village preserved across dimension");
            helper.assertTrue(resident.escortPlayer().equals(owner), "Escort preserved across dimension");
            helper.assertTrue(level.getEntity(identity) == null, "No second entity in source dimension");
        });
    }
    static void portal(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        for (int x = 0; x < 4; x++) for (int y = 0; y < 5; y++)
            level.setBlockAndUpdate(origin.offset(x,y,0), x == 0 || x == 3 || y == 0 || y == 4
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
        for (int x = 1; x < 3; x++) for (int y = 1; y < 4; y++)
            level.setBlockAndUpdate(origin.offset(x,y,0), Blocks.NETHER_PORTAL.defaultBlockState());
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void initialAndReload(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2,2,2));
        Settlement first = StarterVillage.create(helper.getLevel(), origin);
        helper.assertTrue(first.residents().size() == 6, "Exactly six residents");
        helper.assertTrue(first.homes().size() == 3, "Exactly three homes");
        var data = SettlementData.get(helper.getLevel().getServer());
        var restored = SettlementData.load(data.save(new CompoundTag()));
        var copy = restored.entry(first.id()).settlement();
        helper.assertTrue(copy.residents().stream().map(Resident::id).toList().equals(
                first.residents().stream().map(Resident::id).toList()), "Identities survive NBT round trip");
        for (Resident resident : first.residents()) {
            helper.assertTrue(helper.getLevel().getEntity(resident.id()) != null, "Real NPC exists");
            helper.assertTrue(!resident.educated(), "Starter adults are not educated");
        }
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(origin.offset(1,1,4));
        chest.removeItem(0, 7);
        StarterVillage.create(helper.getLevel(), origin);
        helper.assertTrue(chest.getItem(0).getCount() == 41, "Repeated initialization does not restock");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void occupiedSiteIsNeverOverwritten(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2,2,2));
        helper.getLevel().setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
        boolean rejected = false;
        try { StarterVillage.create(helper.getLevel(), origin); }
        catch (IllegalStateException expected) { rejected = expected.getMessage().equals("occupied"); }
        helper.assertTrue(rejected, "Occupied site rejected");
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.DIAMOND_BLOCK), "Player block preserved");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyServerClockAndHousing(GameTestHelper helper) {
        SettlementData data = new SettlementData();
        for (int i = 0; i < 100; i++) data.activeTick(false);
        helper.assertTrue(data.clock().ticks() == 0, "No players means no progress");
        data.activeTick(true);
        Settlement s = Settlement.initial(java.util.UUID.randomUUID());
        Resident r = s.residents().iterator().next();
        s.damageHome(r.home(), data.clock().ticks());
        data.add(new SettlementData.Entry(s, "minecraft:overworld", BlockPos.ZERO));
        SettlementData restored = SettlementData.load(data.save(new CompoundTag()));
        Resident copy = restored.entry(s.id()).settlement().resident(r.id());
        helper.assertTrue(copy.homelessSince() == 1 && copy.home() == null, "Housing episode persisted");
        helper.assertTrue(restored.clock().ticks() == 1, "Clock persisted");
        helper.succeed();
    }
}

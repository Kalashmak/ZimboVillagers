package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import org.villageastra.persistence.NbtRecord;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ArchitectureMigrationGameTests {
 private record Site(ServerLevel l,SettlementData.Entry e,Settlement.Building b){}
 private static Site site(GameTestHelper h,String type,int level,int rotation){var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),type,0,0,0,rotation,level);s.addBuilding(b);s.markLegacyArchitecture(b.id());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(3,14,3)));SettlementData.get(l.getServer()).add(e);
  var map=ArchitectureMigration.legacyLayout(BuildingTiers.layoutId(type,level));
  // The loops reuse one site for every type: clear the lot volume first, so no earlier type's blocks stand in cells this old layout leaves empty.
  var lot=org.villageastra.world.BuildingBlueprints.design(type);
  for(int x=0;x<lot.width();x++)for(int z=0;z<lot.depth();z++)for(int y=-8;y<=24;y++){var p=world(e,b,new BlockPos(x,y,z));if(l.isOutsideBuildHeight(p))continue;l.removeBlockEntity(p);l.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2|16);}
  for(var cell:map.entrySet()){var p=world(e,b,cell.getKey());h.assertTrue(!l.isOutsideBuildHeight(p),"Legacy fixture must fit build height: "+type+" "+p);l.removeBlockEntity(p);l.setBlock(p,BuildingPlacement.state(cell.getValue(),rotation),2|16);}
  for(var cell:map.entrySet())h.assertTrue(l.getBlockState(world(e,b,cell.getKey())).equals(BuildingPlacement.state(cell.getValue(),rotation)),"Exact saved fixture before migration: "+type+" "+cell.getKey());
  int serial=0;for(var cell:map.entrySet()){var be=l.getBlockEntity(world(e,b,cell.getKey()));if(be instanceof Container c){var item=new ItemStack(Items.DIAMOND,++serial);item.getOrCreateTag().putInt("migrationSerial",serial);c.setItem(0,item);be.setChanged();}}
  return new Site(l,e,b);
 }
 private static BlockPos world(SettlementData.Entry e,Settlement.Building b,BlockPos p){return BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ());}
 private static Map<String,Integer> inventory(Site t){var all=new HashSet<BlockPos>(ArchitectureMigration.legacyLayout(BuildingTiers.layoutId(t.b.type(),t.b.level())).keySet());all.addAll(BuildingBlueprints.layout(BuildingTiers.layoutId(t.b.type(),t.b.level()),BlockPos.ZERO).keySet());var out=new TreeMap<String,Integer>();for(var p:all)if(t.l.getBlockEntity(world(t.e,t.b,p)) instanceof Container c)for(int i=0;i<c.getContainerSize();i++){var stack=c.getItem(i);if(!stack.isEmpty())out.merge(stack.getItem()+"/"+stack.getTag(),stack.getCount(),Integer::sum);}return out;}
 private static void check(GameTestHelper h,Site t,Map<String,Integer> before){h.assertTrue(inventory(t).equals(before),"All named stacks retained exactly: "+inventory(t)+" / "+before);
  h.assertTrue(BuildingTiers.level(t.l,t.e,t.b)==t.b.level(),"Working level retained: "+t.b.type()+" "+t.b.level()+" rotation "+t.b.rotation());
  for(var cell:BuildingBlueprints.layout(BuildingTiers.layoutId(t.b.type(),t.b.level()),BlockPos.ZERO).entrySet()){h.assertTrue(BuildingRepairs.present(t.l.getBlockState(world(t.e,t.b,cell.getKey())),BuildingPlacement.state(cell.getValue(),t.b.rotation())),"Target "+t.b.type()+" block stands at "+cell.getKey()+" expected="+cell.getValue()+" actual="+t.l.getBlockState(world(t.e,t.b,cell.getKey())));}
 }
 private static void clean(Site t){SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());}
 @GameTest(template="empty",timeoutTicks=600) public static void everyOldFourthLevelKeepsItsWorkingEquipment(GameTestHelper h){var types=new ArrayList<>(org.villageastra.domain.CoreCatalog.TYPES);types.add("home");types.add("quarry");
  // AD-131: the forester's hut of 15x21 has no AD-117 counterpart — old worlds keep their huts unconverted (owner: new worlds only).
  types.remove("forester");
  // AD-138: likewise the livestock yard of 17x25 (old yards of 11x13 are not converted).
  types.remove("livestock");
  // AD-147: likewise the warehouse of 23x17 (old stores of 13x11 are not converted; the prepare below answers "done" for them, see
  // anOldWarehouseIsLeftAsItStands).
  types.remove(WarehouseStore.TYPE);
  for(var type:types){var t=site(h,type,4,0);try{var before=inventory(t);String status=ArchitectureMigration.prepare(t.l,t.e,t.b);h.assertTrue(status.equals("prepared")||status.equals("done"),"Recognized legacy "+type+": "+status);ArchitectureMigration.recover(t.l,t.b);check(h,t,before);}finally{clean(t);}}h.succeed();}
 @GameTest(template="empty",timeoutTicks=400) public static void aSavedOldUpgradeFinishesItsOwnOperationsThenMigrates(GameTestHelper h){var t=site(h,"school",3,2);try{
  var p=new CompoundTag();p.putString("kind","building");p.putUUID("id",UUID.randomUUID());p.putUUID("project",p.getUUID("id"));p.putUUID("building",t.b.id());p.putString("design","school@4");p.putLong("origin",t.e.center().asLong());p.putInt("rotation",2);p.putInt("upgradeLevel",4);var ops=new ListTag();
  var old=ArchitectureMigration.legacyLayout("school@3");var next=ArchitectureMigration.legacyLayout("school@4");for(var c:next.entrySet())if(!c.getValue().equals(old.get(c.getKey()))){var op=new CompoundTag();op.putLong("pos",world(t.e,t.b,c.getKey()).asLong());op.put("before",NbtUtils.writeBlockState(t.l.getBlockState(BlockPos.of(op.getLong("pos")))));op.put("after",NbtUtils.writeBlockState(BuildingPlacement.state(c.getValue(),2)));ops.add(op);}p.put("ops",ops);p.putBoolean("complete",false);HallUpgradeGoal.enqueue(t.l,t.e,p);
  h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("project"),"Old unfinished upgrade is deferred");
  // Resume exactly the saved operations, without making a new survey against the new blueprint.
  for(var raw:ops){var op=(CompoundTag)raw;var pos=BlockPos.of(op.getLong("pos"));t.l.setBlock(pos,NbtUtils.readBlockState(t.l.holderLookup(Registries.BLOCK),op.getCompound("after")),2);}
  h.assertTrue(BuildingOrders.complete(t.l,t.e,p),"Old operation list still completes under new code");p.putBoolean("complete",true);HallUpgradeGoal.drop(t.l,t.e.settlement().id());HallUpgradeGoal.enqueue(t.l,t.e,p);
  var raised=t.e.settlement().buildings().stream().filter(b->b.id().equals(t.b.id())).findFirst().orElseThrow();var finished=new Site(t.l,t.e,raised);var before=inventory(finished);
  h.assertTrue(raised.level()==4&&t.e.settlement().legacyArchitecture().contains(raised.id()),"Completion retains legacy revision until conversion");h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,raised).equals("prepared"),"Completed old project can now migrate");ArchitectureMigration.recover(t.l,raised);check(h,finished,before);
 }finally{HallUpgradeGoal.drop(t.l,t.e.settlement().id());clean(t);}h.succeed();}
 @GameTest(template="empty",timeoutTicks=600) public static void oldLevelsAndEveryRotationKeepTheirContentsAndGrade(GameTestHelper h){
  for(int level=2;level<=6;level++)for(int turn=0;turn<4;turn++){var t=site(h,"school",level,turn);try{var before=inventory(t);if(level==2&&turn==0){var p=world(t.e,t.b,Legacy117Architecture.core("school"));t.l.setBlock(p,Cores.state("school",6),2);}
   h.assertTrue(BuildingTiers.level(t.l,t.e,t.b)==level,"Legacy working equipment recognized before migration");String status=ArchitectureMigration.prepare(t.l,t.e,t.b);h.assertTrue(status.equals("prepared"),"Old school detected: "+status);ArchitectureMigration.recover(t.l,t.b);check(h,t,before);if(level==2&&turn==0)h.assertTrue(Cores.grade(t.l.getBlockState(world(t.e,t.b,LevelArchitecture.core("school"))))==6,"Already paid higher rings retained");
    h.assertTrue(!ArchitectureMigration.recover(t.l,t.b)&&ArchitectureMigration.prepare(t.l,t.e,t.b).equals("done"),"Repeat is a no-op");}finally{clean(t);}}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void journalRecoversPartialBlocksAndDoesNotRestoreWithdrawnItems(GameTestHelper h){
  // AD-147: the level-VI old type walked here was the warehouse until its lot became 23x17 (not converted); the barracks keeps its footprint.
  var t=site(h,"barracks",6,1);try{var before=inventory(t);h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("prepared"),"Prepared before touching the world");var journal=NbtRecord.read(ArchitectureMigration.path(t.l,t.b.id()));var cells=journal.getList("cells",Tag.TAG_COMPOUND);
   // Simulate interruption after some blocks were replaced but before their inventories/chunks were committed.
   for(int i=0;i<cells.size()/2;i++){var op=cells.getCompound(i);var p=BlockPos.of(op.getLong("pos"));t.l.removeBlockEntity(p);t.l.setBlock(p,NbtUtils.readBlockState(t.l.holderLookup(Registries.BLOCK),op.getCompound("after").getCompound("state")),2);}
   ArchitectureMigration.forget();ArchitectureMigration.started(new net.minecraftforge.event.server.ServerStartedEvent(t.l.getServer()));h.assertTrue(!t.e.settlement().legacyArchitecture().contains(t.b.id()),"Startup recovered the durable prepared record before normal work");check(h,t,before);
   for(var p:BuildingBlueprints.layout(BuildingTiers.layoutId(t.b.type(),6),BlockPos.ZERO).keySet())if(t.l.getBlockEntity(world(t.e,t.b,p)) instanceof Container c)c.clearContent();
   ArchitectureMigration.forget();h.assertTrue(!ArchitectureMigration.recover(t.l,t.b)&&inventory(t).isEmpty(),"Done journal cannot recreate items withdrawn after migration");
  }finally{clean(t);}h.succeed();}
 /** AD-147: an old warehouse of 13x11 marked for the AD-117 conversion is left as it stands — "done" at once, not a footprint error — and
  *  keeps its blocks and its stock. */
 @GameTest(template="empty",timeoutTicks=400) public static void anOldWarehouseIsLeftAsItStands(GameTestHelper h){var t=site(h,WarehouseStore.TYPE,4,0);try{
  var map=ArchitectureMigration.legacyLayout(BuildingTiers.layoutId(t.b.type(),4));var before=inventory(t);
  h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("done"),"An old warehouse is not converted");
  h.assertTrue(!ArchitectureMigration.recover(t.l,t.b),"Nothing to recover for it");
  for(var cell:map.entrySet())h.assertTrue(t.l.getBlockState(world(t.e,t.b,cell.getKey())).equals(BuildingPlacement.state(cell.getValue(),0)),"Old warehouse block kept at "+cell.getKey());
  h.assertTrue(inventory(t).equals(before)&&!before.isEmpty(),"Its stock kept: "+before);
 }finally{clean(t);}h.succeed();}
 @GameTest(template="empty",timeoutTicks=400) public static void activeProjectAndConflictsAreKeptAndRevisionSurvivesSave(GameTestHelper h){var t=site(h,"school",4,0);try{var before=inventory(t);var project=new CompoundTag();project.putBoolean("complete",false);project.putString("fixture","saved old operations and paid rings");HallUpgradeGoal.enqueue(t.l,t.e,project);
   h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("project")&&HallUpgradeGoal.inspect(t.l,t.e.settlement().id()).equals(project)&&inventory(t).equals(before),"An active project keeps its exact operations and contents");HallUpgradeGoal.drop(t.l,t.e.settlement().id());
   var next=BuildingBlueprints.layout("school@4",BlockPos.ZERO);var furnace=ArchitectureMigration.legacyLayout("school@4").entrySet().stream().filter(c->c.getValue().is(Blocks.FURNACE)&&!c.getValue().equals(next.get(c.getKey()))).findFirst().orElseThrow().getKey();
   var borrower=new Settlement.Building(UUID.randomUUID(),"masonry",40,0,0,0,1);t.e.settlement().addBuilding(borrower);var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putBoolean("physicalSmelt",true);job.putString("stage","smelt_wait");job.putLong("furnace",world(t.e,t.b,furnace).asLong());NbtRecord.write(Workshops.path(t.l,borrower.id()),job);
   h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("production")&&Workshops.inspect(t.l,borrower.id()).equals(job)&&inventory(t).equals(before),"Borrowed furnace stays at the saved job position with all contents");job.putString("stage","idle");NbtRecord.write(Workshops.path(t.l,borrower.id()),job);
   var core=world(t.e,t.b,Legacy117Architecture.core("school"));var was=t.l.getBlockState(core);t.l.setBlock(core,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
   h.assertTrue(ArchitectureMigration.prepare(t.l,t.e,t.b).equals("conflict")&&t.l.getBlockState(core).is(Blocks.DIAMOND_BLOCK)&&BuildingRepairs.damage(t.l,t.e,t.b).isEmpty(),"Foreign block kept; automatic repair cannot buy a replacement core");t.l.setBlock(core,was,2);
   var data=SettlementData.get(t.l.getServer());var saved=data.save(new CompoundTag());var loaded=SettlementData.load(saved).entry(t.e.settlement().id());h.assertTrue(loaded.settlement().legacyArchitecture().contains(t.b.id()),"Pending revision survives save/load");
   ArchitectureMigration.prepare(t.l,t.e,t.b);ArchitectureMigration.recover(t.l,t.b);t.e.settlement().finishArchitectureMigration(t.b.id());loaded=SettlementData.load(data.save(new CompoundTag())).entry(t.e.settlement().id());h.assertTrue(!loaded.settlement().legacyArchitecture().contains(t.b.id()),"New revision survives save/load");check(h,t,before);
  }finally{HallUpgradeGoal.drop(t.l,t.e.settlement().id());clean(t);}h.succeed();}
}

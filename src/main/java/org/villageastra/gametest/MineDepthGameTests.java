package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-112: the mine goes as deep as its core's level allows. MineDrive owns the cells: the stair down to the floor step F of the working
 *  level, then a gallery east and one west from its landing, then nothing (mine_floor) until a ring takes it deeper. The miner, the level-V
 *  machine and a crash recovery all step through the same drive, and what is dug is claimed. The lot stands high in the template, so F is
 *  the 8-a-level minimum (the gallery lies within a stone block the test lays itself); the tests run in their own batch. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineDepthGameTests {
 private static final String BATCH="cores_mine";
 private record Pit(ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building mine){}
 /** A hall with its chest and a mine kept at a level with that level's equipment (and core) standing, on open ground high in the template. */
 private static Pit pit(GameTestHelper h,int level){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,20,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<24;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var b=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",14,0,0);s.addBuilding(b);
  for(int i=2;i<=level;i++)s.raiseBuildingLevel(b.id(),i);var mine=kept(s,b);
  var chest=LogisticsRoutes.position(e,mine);l.setBlock(chest.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  for(int i=2;i<=level;i++)for(var placed:BuildingLevels.equipment("mine",i))l.setBlock(BuildingPlacement.at(e,mine,placed.local().getX(),placed.local().getY(),placed.local().getZ()),BuildingPlacement.state(placed.state(),mine.rotation()),3);
  return new Pit(l,e,s,mine);
 }
 private static Settlement.Building kept(Settlement s,Settlement.Building b){return s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();}
 private static BlockPos at(Pit t,int x,int y,int z){return BuildingPlacement.at(t.e,t.mine,x,y,z);}
 private static BlockPos at(Pit t,MineDrive.Cell c){return at(t,c.x(),c.y(),c.z());}
 /** Stone over local x x0..x1, y y0..y1, z z0..z1 of the mine: the rock the drive takes. */
 private static void rock(Pit t,int x0,int x1,int y0,int y1,int z0,int z1){for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)t.l.setBlock(at(t,x,y,z),Blocks.STONE.defaultBlockState(),2);}
 /** A fresh drive record standing at a stair step and cell (east side, first column). */
 private static CompoundTag drive(Pit t,int step,int cell){var r=MineWork.read(t.l,t.mine);r.putInt("step",step);r.putInt("cell",cell);r.putInt("side",MineDrive.EAST);r.putInt("run",0);r.remove("galleryOf");return r;}
 private static void done(Pit t){
  SettlementData.get(t.l.getServer()).remove(t.s.id());
  try{Files.deleteIfExists(MineWork.path(t.l,t.mine.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** AD-122: the last cell of a stair row and the gallery height of a drive opened now (5 high since AD-122, 4 before). */
 private static int last(Pit t){var s=MineWork.shape(MineWork.read(t.l,t.mine));return s.width()*s.height()-1;}
 private static int gh(Pit t){return MineWork.shape(MineWork.read(t.l,t.mine)).galleryHeight();}
 private static int floor(Pit t){return MineWork.floorStep(t.l,t.e,t.mine,MineWork.read(t.l,t.mine));}

 /** #1: at level I the stair stops at F; after its last cell the next target is the east gallery (x 5, z 7+F), never step F+1. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void levelOneStopsAtItsFloorAndTurnsIntoTheGallery(GameTestHelper h){
  var t=pit(h,1);
  try{
   int f=floor(t),mouth=BuildingPlacement.origin(t.e,t.mine).getY()-MineWork.DRIVE_DESCENT;
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.mine)==1,"No core: the mine works at I");
   h.assertTrue(f==MineDrive.floorStep(1,mouth,t.l.getMinBuildHeight())&&f==Math.max(mouth-MineDrive.floorY(1),8)&&f>=8,"F is the deeper of the level's floor and eight steps: F="+f+" mouth="+mouth);
   h.assertTrue(MineDrive.floorY(1)==CoreEffects.value("mine","floor",1),"The floor is the core's promise");
   var d=drive(t,f,last(t));var last=MineWork.next(t.l,t.e,t.mine,d);
   h.assertTrue(last.stage()==MineDrive.Stage.STAIR&&last.cell().z()==7+f,"The last cell of step F is still the stair: "+last);
   h.assertTrue(at(t,last.cell()).getY()==mouth-f,"Its bottom cell lies at the floor: "+at(t,last.cell()).getY()+" vs "+(mouth-f));
   MineWork.step(d);var next=MineWork.next(t.l,t.e,t.mine,d);
   h.assertTrue(next.stage()==MineDrive.Stage.EAST&&next.cell().x()==5&&next.cell().z()==7+f,"Then the east gallery at x 5 of the landing: "+next);
   h.assertTrue(next.cell().z()!=8+f&&d.getInt("step")==f+1,"Never step F+1");
   h.assertTrue(at(t,next.stand()).getY()==mouth-f&&next.cell().y()==next.stand().y()+gh(t)-1,"Dug top-down from the floor of the landing");
  }finally{done(t);}
  h.succeed();
 }
 /** #2 and #6: a ring of level II takes the drive on at step F+1 (from the gallery or the floor it stood at); a level dropped below the
  *  step already dug digs nothing (FLOOR). */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void aRaisedLevelResumesTheStairAndALoweredOneStops(GameTestHelper h){
  var t=pit(h,2);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.mine)==2,"Core grade II: the mine works at II");
   var core=LevelArchitecture.core("mine");var corePos=at(t,core.getX(),core.getY(),core.getZ());
   t.l.setBlock(corePos,Blocks.AIR.defaultBlockState(),3);int f1=floor(t);
   var d=drive(t,f1,last(t));MineWork.next(t.l,t.e,t.mine,d);MineWork.step(d);
   var gallery=MineWork.next(t.l,t.e,t.mine,d);h.assertTrue(gallery.stage()==MineDrive.Stage.EAST,"At I it stands in the gallery: "+gallery);
   MineWork.step(d);
   t.l.setBlock(corePos,Cores.state("mine",2),3);int f2=floor(t);
   h.assertTrue(f2>f1,"Level II goes deeper: F "+f1+" -> "+f2);
   var resumed=MineWork.next(t.l,t.e,t.mine,d);
   h.assertTrue(resumed.stage()==MineDrive.Stage.STAIR&&resumed.cell().z()==8+f1&&d.getInt("cell")==0,"The stair goes on at step F+1 from its first cell: "+resumed+" "+d);
   var atFloor=drive(t,f1+1,0);atFloor.putInt("side",MineDrive.DONE);
   h.assertTrue(MineWork.next(t.l,t.e,t.mine,atFloor).stage()==MineDrive.Stage.STAIR,"A drive at its old floor resumes too");
   var deep=drive(t,f1+4,0);
   h.assertTrue(MineWork.next(t.l,t.e,t.mine,deep).stage()==MineDrive.Stage.STAIR,"At II step F(I)+4 is stair");
   t.l.setBlock(corePos,Blocks.AIR.defaultBlockState(),3);
   var dropped=MineWork.next(t.l,t.e,t.mine,deep);
   h.assertTrue(dropped.floor()&&dropped.cell()==null&&MineWork.target(t.l,t.e,t.mine,deep)==null,"Its core gone, the mine is below its level's floor: nothing to dig");
  }finally{done(t);}
  h.succeed();
 }
 /** #3: a dug gallery column is protected like the stair (with its walls); rock beyond it is not. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void aDugGalleryIsProtected(GameTestHelper h){
  var t=pit(h,1);
  try{
   int f=floor(t);t.s.noteMine(t.mine.id(),f,3,4,MineWork.DRIVE_DESCENT);
   var d=drive(t,f+1,0);MineDrive.Target next=null;
   for(int n=0;n<3*gh(t);n++){next=MineWork.next(t.l,t.e,t.mine,d);MineWork.chose(d,next);MineWork.claim(t.l,t.e,t.mine,d);MineWork.step(d);}
   var area=t.s.mineAreas().get(t.mine.id());
   h.assertTrue(area.galleries().size()==1&&area.galleries().get(0).length()==3&&area.galleries().get(0).side()==MineDrive.EAST,"Three columns of the east gallery are claimed: "+area);
   int y=next.stand().y()+1;
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,at(t,7,y,7+f))&&OwnershipEvents.protectedBlock(t.l,at(t,8,y,7+f)),"A dug column and its end wall are the settlement's");
   h.assertTrue(!OwnershipEvents.protectedBlock(t.l,at(t,10,y,7+f)),"Rock beyond the gallery is nobody's");
  }finally{done(t);}
  h.succeed();
 }
 /** #4: water beside the east gallery turns the drive west; a block that is no ground on the west gives the floor: the miner says mine_floor
  *  and digs nothing. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aWetGalleryTurnsAndBothBlockedIsTheFloor(GameTestHelper h){
  var t=pit(h,1);ResidentEntity npc=null;
  try{
   int f=floor(t),top=-f-MineWork.DRIVE_DESCENT+gh(t)-1;
   rock(t,-3,9,top-5,top+1,5+f,9+f);
   t.l.setBlock(at(t,6,top,7+f),Blocks.WATER.defaultBlockState(),2);t.l.setBlock(at(t,1,top,7+f),Blocks.OAK_PLANKS.defaultBlockState(),2);
   t.s.noteMine(t.mine.id(),f,3,4,MineWork.DRIVE_DESCENT);
   t.s.addHome(new Settlement.Home(Settlement.childId(t.s.id(),"home"),1,4,true));
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.MINER,t.mine.id());
   npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);
   // He stands beside the mine chest, where a miner at the floor of his level goes: nothing moves him, so his status is the drive's.
   var feet=LogisticsRoutes.position(t.e,t.mine).east();npc.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);t.l.addFreshEntity(npc);
   var record=drive(t,f+1,0);record.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,f).toArray());for(int row=0;row<=f;row++)for(var cell:MineDrive.stairs(row,MineWork.shape(record)))t.l.setBlock(at(t,cell),Blocks.COBBLESTONE_STAIRS.defaultBlockState(),2);record.putString("stage","choose");record.put("tool",new ItemStack(Items.IRON_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.mine,record);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);
   h.assertTrue(goal.canUse(),"The miner takes up his drive");
   goal.tick();var after=MineWork.read(t.l,t.mine);
   h.assertTrue(after.getInt("side")==MineDrive.WEST&&after.getString("stage").equals("choose"),"Water beside the east gallery: the drive turns west: "+after);
   goal.tick();after=MineWork.read(t.l,t.mine);
   h.assertTrue(after.getInt("side")==MineDrive.DONE,"Planks on the west: both galleries end: "+after);
   goal.tick();after=MineWork.read(t.l,t.mine);
   h.assertTrue("mine_floor".equals(npc.workStatus())&&after.getString("stage").equals("choose"),"The miner stands at the floor of his level: "+npc.workStatus());
   h.assertTrue(t.l.getBlockState(at(t,5,top,7+f)).is(Blocks.STONE)&&t.l.getBlockState(at(t,1,top,7+f)).is(Blocks.OAK_PLANKS)&&t.l.getFluidState(at(t,6,top,7+f)).isSource(),"Nothing was dug");
   var out=(Container)t.l.getBlockEntity(LogisticsRoutes.position(t.e,t.mine));h.assertTrue(out.isEmpty(),"And nothing delivered");
  }finally{if(npc!=null)npc.discard();done(t);}
  h.succeed();
 }
 /** #5: the level-V machine drives the same stair to the same floor and then the same gallery, and claims what it digs. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void theMachineDrivesTheSameFloorAndGallery(GameTestHelper h){
  var t=pit(h,5);
  try{
   // AD-136: the mine digs by itself at its own level V (Mining V), no mechanics research.
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.mine)==5,"The mine works at V: "+BuildingLevels.level(t.l,t.e,t.mine));
   int f=floor(t),bottom=-f-MineWork.DRIVE_DESCENT;
   rock(t,1,6,bottom-1,bottom+4,6+f,9+f);
   var d=drive(t,f,last(t));d.putString("stage","choose");MineWork.write(t.l,t.mine,d);
   var stair=MineWork.next(t.l,t.e,t.mine,drive(t,f,last(t))).cell();var gallery=new MineDrive.Cell(5,bottom+gh(t)-1,7+f);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.mine);
   h.assertTrue(Machines.tick(t.l,t.e,40,Workshops.wants(t.l,t.e))>0&&t.l.getBlockState(at(t,stair)).isAir(),"The machine digs the last stair cell of its floor: why=["+Machines.lastReason+"]");
   h.assertTrue(Machines.tick(t.l,t.e,80,Workshops.wants(t.l,t.e))>0&&t.l.getBlockState(at(t,gallery)).isAir(),"Then the first cell of the east gallery: why=["+Machines.lastReason+"]");
   h.assertTrue(t.l.getBlockState(at(t,2,bottom+gh(t)-2,8+f)).is(Blocks.STONE),"Step F+1 stays rock");
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==2,"Both blocks are in the mine's chest: "+chest.countItem(Items.COBBLESTONE));
   var area=t.s.mineAreas().get(t.mine.id());
   h.assertTrue(area!=null&&area.lastStep()==f&&area.galleries().size()==1&&area.galleries().get(0).step()==f,"The stair to F and the gallery are claimed: "+area);
  }finally{done(t);}
  h.succeed();
 }
}

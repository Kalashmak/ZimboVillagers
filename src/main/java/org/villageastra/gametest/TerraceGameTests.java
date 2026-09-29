package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-045: without an engineer there is no earthwork design; with one the builder really cuts the rise, fills the hollow and the site becomes orderable. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TerraceGameTests {
 private record Site(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos site){}
 private static Site site(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/engineering"),"engineering",8,0,0));
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  for(int x=-2;x<16;x++)for(int z=-2;z<16;z++){for(int y=-4;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=10;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  return new Site(l,s,e,site);
 }
 private static void rough(Site t){
  for(int x=0;x<3;x++)for(int z=0;z<7;z++)for(int y=1;y<=2;y++)t.l.setBlock(t.site.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
  for(int x=4;x<7;x++)for(int z=0;z<4;z++){t.l.setBlock(t.site.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);t.l.setBlock(t.site.offset(x,-1,z),Blocks.AIR.defaultBlockState(),3);}
 }
 private static Resident engineer(Site t){
  var building=t.s.buildings().stream().filter(b->b.type().equals("engineering")).findFirst().orElseThrow();
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.ENGINEER,building.id());return r;
 }
 @GameTest(template="empty",timeoutTicks=200) public static void engineerDesignsEarthworksThatMakeARoughSiteOrderable(GameTestHelper h){
  var t=site(h);rough(t);
  h.assertTrue(!BuildingOrders.survey(t.l,t.e,"home",0,t.site).ok(),"The rough site is not orderable as it is");
  h.assertTrue(Terraces.plan(t.l,t.e,"home",t.site)==null,"Without an engineer nothing is designed");
  engineer(t);
  var project=Terraces.plan(t.l,t.e,"home",t.site);
  h.assertTrue(project!=null&&project.getInt("cut")>0&&project.getInt("fill")>0,"The engineer designs both cut and fill: "+(project==null?"none":project.getInt("cut")+"/"+project.getInt("fill")));
  h.assertTrue(Terraces.order(t.l,t.e,project),"Ordered as a builder project");
  var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
  h.assertTrue(Terraces.missing(t.l,t.e,project)>0,"The settlement really lacks the filling material");
  chest.setItem(0,new ItemStack(Items.COBBLESTONE,64));chest.setItem(1,new ItemStack(Items.COBBLESTONE,64));
  h.assertTrue(Terraces.missing(t.l,t.e,project)==0,"With stock in the hall nothing is missing");
  for(int i=0;i<400&&!project.getBoolean("complete");i++){
   var result=Roads.apply(t.l,t.e,project);
   if(result.equals("missing")||result.equals("unload"))if(Roads.load(t.l,t.e,Workshops.hall(t.e),project)==0&&result.equals("missing"))break;
  }
  h.assertTrue(project.getBoolean("complete"),"The earthworks are finished: "+project.getInt("index")+"/"+project.getList("ops",10).size());
  h.assertTrue(t.l.getBlockState(t.site.offset(1,1,1)).isAir()&&t.l.getBlockState(t.site.offset(5,-1,1)).is(Blocks.COBBLESTONE),"The rise is really cut and the hollow really filled");
  h.assertTrue(chest.countItem(Items.STONE)>0,"The cut stone came back to the settlement stock");
  var survey=BuildingOrders.survey(t.l,t.e,"home",0,t.site);
  h.assertTrue(survey.ok(),"After the earthworks the site is orderable: "+survey.reason()+" "+survey.conflicts());
  h.succeed();
 }
 /** OWNER_REQUEST 9.9: on rough ground the red removals are the plan's own clearing, and moving the floor down or up really changes cut, fill and foundation. */
 @GameTest(template="empty",timeoutTicks=200) public static void movingTheFloorChangesCutFillAndFoundation(GameTestHelper h){
  var t=site(h);rough(t);engineer(t);
  var levels=new int[]{-1,0,2};var cut=new int[3];var fill=new int[3];var cobble=new int[3];
  for(int i=0;i<3;i++){var origin=t.site.above(levels[i]);
   var estimate=Plans.estimate(t.l,t.e,"home",0,origin);var survey=BuildingOrders.survey(t.l,t.e,"home",0,origin);
   cut[i]=estimate.getInt("cut");fill[i]=estimate.getInt("fill");cobble[i]=survey.state().isEmpty()?-1:survey.state().getCompound("cost").getInt("minecraft:cobblestone");
   if(survey.state().isEmpty())continue;
   var red=new HashSet<Long>();for(long raw:estimate.getLongArray("demolish"))red.add(raw);
   var clears=new HashSet<Long>();var placed=new HashSet<Long>();var scaffold=VillageAstra.TIMBER_SCAFFOLD.get();
   for(var raw:survey.state().getList("ops",10)){var step=HallConstructionPlan.step((net.minecraft.nbt.CompoundTag)raw);
    if(step.after().isAir()&&!step.before().is(scaffold)&&!step.before().isAir())clears.add(step.pos().asLong());
    if(!step.after().isAir()&&!step.after().is(scaffold))placed.add(step.pos().asLong());}
   // A cell cleared and then built again (a floor cell a scaffold column passes) is shown as future building, not as a removal.
   clears.removeAll(placed);
   h.assertTrue(red.equals(clears),"At floor "+levels[i]+" the red cells are exactly the plan's clearing: "+red.size()+" red, "+clears.size()+" cleared");
  }
  h.assertTrue(cut[0]>cut[1]&&cut[1]>=cut[2],"A lower floor cuts more earth: "+Arrays.toString(cut));
  h.assertTrue(fill[2]>fill[1]&&fill[1]>=fill[0],"A higher floor needs more fill: "+Arrays.toString(fill));
  h.assertTrue(cobble[2]<0||cobble[2]>cobble[1],"A floor raised off the ground rests on a deeper foundation: "+Arrays.toString(cobble));
  h.succeed();
 }
}

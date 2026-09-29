package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** AD-123 (Roads V, C11): with bridges and tunnels a trail crosses a river on a railed plank deck and passes a hill through a lit 3×3 bore;
 *  lava is always refused and the spoil of the bore reaches the hall through the journal. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BridgeTunnelGameTests {
 @GameTest(template="empty",batch="trail_bridge",timeoutTicks=200) public static void bridgeSpansRiver(GameTestHelper h){
  var p=TrailGameTests.pair(h);
  try{
   int mid=p.length()/2;for(int k=mid-2;k<=mid+2;k++)for(int z=-8;z<9;z++)p.l().setBlock(TrailGameTests.at(p,k).offset(0,0,z),Blocks.WATER.defaultBlockState(),2);
   TrailGameTests.learn(p,"roads.1","roads.2","roads.3","roads.4","roads.5");
   h.assertTrue(Trails.order(p.l(),p.e(),p.b().settlement().id(),0,false,List.of()).isEmpty(),"Ordered");
   var state=TrailGameTests.run(p,80);var t=Trails.record(p.l(),p.a().id());
   h.assertTrue(state.equals(Trails.COMPLETE),"The trail crosses the river: "+TrailGameTests.why(p));
   for(int k=mid-2;k<=mid+2;k++)h.assertTrue(p.l().getBlockState(TrailGameTests.at(p,k).above()).is(Blocks.OAK_PLANKS)&&p.l().getBlockState(TrailGameTests.at(p,k)).is(Blocks.WATER),"A deck above the water at "+k);
   h.assertTrue(p.l().getBlockState(TrailGameTests.at(p,mid).offset(0,2,1)).is(Blocks.OAK_FENCE)&&p.l().getBlockState(TrailGameTests.at(p,mid).offset(0,2,-1)).is(Blocks.OAK_FENCE),"Rails on both sides");
   h.assertTrue(p.l().getBlockState(TrailGameTests.at(p,mid+5)).is(Blocks.DIRT_PATH),"The trail goes on beyond the river");
  }finally{TrailGameTests.done(p);}
  h.succeed();
 }
 /** A stone hill five high and seven cells long across the middle of the trail; returns the middle cell. */
 private static int hill(TrailGameTests.Pair p){int mid=p.length()/2;for(int k=mid-3;k<=mid+3;k++)for(int z=-8;z<9;z++)for(int y=1;y<=5;y++)p.l().setBlock(TrailGameTests.at(p,k).offset(0,0,z).above(y),Blocks.STONE.defaultBlockState(),2);return mid;}
 @GameTest(template="empty",batch="trail_tunnel",timeoutTicks=200) public static void tunnelCutsHill(GameTestHelper h){
  var p=TrailGameTests.pair(h);
  try{
   int mid=hill(p);TrailGameTests.learn(p,"roads.1","roads.2","roads.3","roads.4","roads.5");
   h.assertTrue(Trails.order(p.l(),p.e(),p.b().settlement().id(),0,false,List.of()).isEmpty(),"Ordered");
   var state=TrailGameTests.run(p,80);var t=Trails.record(p.l(),p.a().id());
   h.assertTrue(state.equals(Trails.COMPLETE),"The trail passes the hill: "+TrailGameTests.why(p));
   for(int k=mid-3;k<=mid+3;k++)for(int dz=-1;dz<=1;dz++)for(int y=1;y<=3;y++){var c=TrailGameTests.at(p,k).offset(0,y,dz);var s=p.l().getBlockState(c);h.assertTrue(s.isAir()||s.is(Blocks.LANTERN),"The bore is open at "+k+","+y+","+dz+": "+s);}
   h.assertTrue(p.l().getBlockState(TrailGameTests.at(p,mid).offset(0,4,0)).is(Blocks.STONE)&&p.l().getBlockState(TrailGameTests.at(p,mid)).is(Blocks.DIRT_PATH),"The roof stands, the floor is paved");
   int lamps=0;for(int k=mid-3;k<=mid+3;k++)for(int dz=-1;dz<=1;dz++)if(p.l().getBlockState(TrailGameTests.at(p,k).offset(0,1,dz)).is(Blocks.LANTERN))lamps++;
   h.assertTrue(lamps>=1,"The tunnel is lit without Roads III: "+lamps);
  }finally{TrailGameTests.done(p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="trail_lava",timeoutTicks=200) public static void lavaRefused(GameTestHelper h){
  var p=TrailGameTests.pair(h);
  try{
   for(int z=-8;z<9;z++)p.l().setBlock(TrailGameTests.at(p,p.length()/2).offset(0,0,z),Blocks.LAVA.defaultBlockState(),2);
   TrailGameTests.learn(p,"roads.1","roads.2","roads.3","roads.4","roads.5");Trails.order(p.l(),p.e(),p.b().settlement().id(),0,false,List.of());
   var state=TrailGameTests.run(p,60);var t=Trails.record(p.l(),p.a().id());
   h.assertTrue(state.equals(Trails.BLOCKED)&&t.getString("reason").equals("lava"),"Lava is refused even with Roads V: "+TrailGameTests.why(p));
  }finally{TrailGameTests.done(p);}
  h.succeed();
 }
 @GameTest(template="empty",batch="trail_spoil",timeoutTicks=200) public static void tunnelSpoilGoesToStock(GameTestHelper h){
  var p=TrailGameTests.pair(h);
  try{
   hill(p);TrailGameTests.learn(p,"roads.1","roads.2","roads.3","roads.4","roads.5");
   var chest=LogisticsRoutes.chest(p.l(),p.e(),p.hall());int before=chest.countItem(Items.COBBLESTONE);
   Trails.order(p.l(),p.e(),p.b().settlement().id(),0,false,List.of());var state=TrailGameTests.run(p,80);
   int bored=7*3*3,after=chest.countItem(Items.COBBLESTONE);
   h.assertTrue(state.equals(Trails.COMPLETE)&&after-before>=bored-7,"The bore's stone is in the hall as cobblestone: +"+(after-before)+" of about "+bored+"; "+TrailGameTests.why(p));
   h.assertTrue(Trails.record(p.l(),p.a().id()).getList("returns",10).isEmpty()&&Trails.record(p.l(),p.a().id()).getList("cargo",10).isEmpty(),"The crew carries nothing back out");
  }finally{TrailGameTests.done(p);}
  h.succeed();
 }
}

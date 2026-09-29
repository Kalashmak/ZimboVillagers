package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-136: the fixture of the research-v2 GameTests (not a test holder) - a town hall with its chest and one building of a type standing on
 *  its lot at level I (as CoreEffectGameTests), knowledge already paid for, and educated scientists assigned to a laboratory. */
final class ResearchV2Town {
 static final class Town{final ServerLevel l;final SettlementData.Entry e;final Settlement s;final Settlement.Building shop;
  Town(ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building shop){this.l=l;this.e=e;this.s=s;this.shop=shop;}
  Settlement.Building kept(){return s.buildings().stream().filter(b->b.id().equals(shop.id())).findFirst().orElseThrow();}
  Settlement.Building hall(){return Workshops.hall(e);}
 }
 private ResearchV2Town(){}
 static Town town(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  Settlement.Building shop=null;if(type!=null){shop=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,10,0,0);s.addBuilding(shop);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  for(int x=-2;x<26;x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  if(shop!=null)lay(l,e,shop,type);
  BookResearch.clearCache();
  return new Town(l,e,s,shop);
 }
 static void lay(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(design,BuildingPlacement.origin(e,b),b.rotation()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);}
 /** Raises the building to a kept level with that level's whole design (its core or ring included) standing. */
 static Settlement.Building raise(Town t,int level){
  for(int n=t.kept().level()+1;n<=level;n++)t.s.raiseBuildingLevel(t.shop.id(),n);var b=t.kept();
  if(level>1)lay(t.l,t.e,b,BuildingTiers.layoutId(b.type(),level));BuildingLevels.forgetBest(t.s.id());return b;
 }
 /** Knowledge the settlement has already paid for (the done list). */
 static void learn(Town t,String... nodes){
  var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);
  for(var node:nodes)done.add(StringTag.valueOf(node));record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);ResearchKnobs.forget(t.s.id());
 }
 /** An educated adult scientist assigned to the building (a record only: the works follow the village clock, not a walking body). */
 static Resident scientist(Town t,Settlement.Building lab,String name){
  var r=new Resident(Settlement.childId(t.s.id(),"science/"+name),Resident.Life.CHILD,false,null,null,-1);
  t.s.admit(r,Settlement.childId(t.s.id(),"home"));r.educate();r.growUp();t.s.assign(r.id(),Profession.SCIENTIST,lab.id());return r;
 }
 static int works(Town t,Settlement.Building b){var c=LogisticsRoutes.chest(t.l,t.e,b);return c==null?-1:c.countItem(VillageAstra.RESEARCH_VOLUME.get());}
 static void done(Town t){SettlementData.get(t.l.getServer()).remove(t.s.id());BuildingLevels.forgetBest(t.s.id());BookResearch.clearCache();}
}

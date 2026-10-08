package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** Exhausting one horizon does not mean the village found the ore its real orders require. */
public final class MineProspecting {
 private MineProspecting(){}
 /** A deeper unlocked horizon resumes the deepest already dug stair, never the shallower survey branch. */
 public static int activeFloor(CompoundTag t,int limit){
  if(t.contains("prospectFloor")&&limit>t.getInt("prospectLimit")){
   t.putInt("step",Math.max(t.getInt("extentStep")+1,t.getInt("step")));t.putInt("cell",0);t.putInt("side",MineDrive.EAST);t.putInt("run",0);
   t.remove("prospectFloor");t.remove("prospectLimit");t.remove("galleryOf");t.remove("prospectLength");t.remove("prospectExtension");
  }
  return t.contains("prospectFloor")?Math.min(limit,t.getInt("prospectFloor")):limit;
 }
 public static Set<net.minecraft.world.item.Item> needed(ServerLevel l,SettlementData.Entry e,Settlement.Building mine){
  var result=new HashSet<net.minecraft.world.item.Item>();var hall=Workshops.hall(e);var stock=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(stock==null)return result;var own=LogisticsRoutes.chest(l,e,mine);
  var free=HallReserve.view(l,e,stock);
  for(var want:Workshops.wants(l,e))for(var in:Workshops.needs(l,e,Workshops.spec("town_hall"),free,List.of(want)))
   if(own==null||LogisticsRoutes.count(own,in::matches)<in.count())for(var stack:in.ingredient().getItems())if(Workshops.mined(stack.getItem()))result.add(stack.getItem());
  return result;
 }
 public static boolean begin(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,CompoundTag t){
  var area=e.settlement().mineAreas().get(mine.id());if(area==null)return false;var wanted=needed(l,e,mine);if(wanted.isEmpty())return false;
  int limit=MineWork.floorStep(l,e,mine,t);if(area.galleries().size()>MineArea.MAX_GALLERIES-2)return extend(l,e,mine,t,area,limit);var visited=new TreeSet<Integer>();for(int floor:t.getIntArray("surveyedFloors"))visited.add(floor);
  visited.add(t.getInt("floorStep"));for(var gallery:area.galleries())visited.add(gallery.step());
  int next=MineOutcrops.floor(l,e,mine,area,Math.min(limit,area.lastStep()),visited,wanted);
  if(next<0)for(int floor=Math.min(limit-6,area.lastStep());floor>=0;floor--)if(floor%6==0&&!visited.contains(floor)){next=floor;break;}
  // Coarse rows can miss entire veins. If real orders remain unmet, survey each
  // skipped landing once, within the existing staircase and working depth.
  // This chooses a direction only: ordinary excavation still validates every block.
  if(next<0)for(int floor=Math.min(limit,area.lastStep());floor>=0;floor--)if(!visited.contains(floor)){next=floor;break;}
  if(next<0)return extend(l,e,mine,t,area,limit);
  t.remove("prospectLength");t.remove("prospectExtension");
  t.putIntArray("surveyedFloors",visited.stream().mapToInt(Integer::intValue).toArray());t.putInt("prospectFloor",next);t.putInt("prospectLimit",limit);t.putInt("floorStep",next);
  t.putInt("extentStep",Math.max(area.lastStep(),t.getInt("extentStep")));t.putInt("step",next+1);t.putInt("cell",0);t.putInt("side",MineDrive.EAST);t.putInt("run",0);t.putInt("galleryOf",next);
  t.putString("stage","choose");t.putUUID("operation",UUID.randomUUID());t.remove("access");t.remove("beam");return true;
 }
 /** Continue only a completed section, never retry a face previously stopped by
  * water or missing support. No ore is created or selected through the rock. */
 private static boolean extend(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,CompoundTag t,MineArea area,int limit){
  int section=CoreEffects.mine().galleryLength();
  var ordered=area.galleries().stream().filter(g->g.step()<=limit&&g.length()%section==0&&g.length()<MineArea.maxGalleryLength())
   .sorted(Comparator.comparingInt(MineArea.Gallery::length).thenComparing(Comparator.comparingInt(MineArea.Gallery::step).reversed()).thenComparingInt(MineArea.Gallery::side)).toList();
  for(var g:ordered){
   int length=g.length()+section,left=area.width()==1?3:2,right=area.width()==1?3:4;
   var endpoint=BuildingPlacement.at(e,mine,g.side()==MineDrive.EAST?right+length:left-length,-g.step()-area.descent(),7+g.step());
   // Keep the entire new section inside the village's supported worker territory,
   // including a little room to stand beside a face or turn back.
   int reach=NaturalSupplyGoal.ROUTE_RANGE-4;if(endpoint.distSqr(e.center())>(double)reach*reach)continue;
   var candidate=t.copy();candidate.putInt("floorStep",g.step());candidate.putInt("step",g.step()+1);candidate.putInt("cell",0);candidate.putInt("side",g.side());candidate.putInt("run",g.length());candidate.putInt("prospectLength",g.length()+section);
   var next=MineDrive.next(MineWork.drive(candidate),g.step(),MineWork.shape(candidate));var pos=MineWork.at(e,mine,next.cell());
   if(!l.hasChunkAt(pos)||org.villageastra.server.OwnershipEvents.protectedBlock(l,pos,b->!b.id().equals(mine.id()))||!MineWork.galleryFloor(l,e,mine,candidate,next.cell())||MineWork.unsafeGallery(l,pos)||!MineWork.diggable(l,e,mine,pos,candidate))continue;
   t.putInt("prospectFloor",g.step());t.putInt("prospectLimit",limit);t.putInt("floorStep",g.step());t.putInt("step",g.step()+1);t.putInt("cell",0);t.putInt("side",g.side());t.putInt("run",g.length());t.putInt("galleryOf",g.step());
   t.putInt("prospectLength",g.length()+section);t.putBoolean("prospectExtension",true);t.putInt("extentStep",Math.max(area.lastStep(),t.getInt("extentStep")));
   t.putString("stage","choose");t.putUUID("operation",UUID.randomUUID());t.remove("access");t.remove("beam");return true;
  }
  return false;
 }

}

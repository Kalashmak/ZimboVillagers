package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** A small village's one available raw worker covers the resource its real project lacks. */
public final class ResourceStaffing {
 private ResourceStaffing(){}
 private static boolean staffed(Settlement s,Profession role,Settlement.Building b){return s.residents().stream().anyMatch(r->r.alive()&&r.profession()==role&&b.equals(s.workplace(r.id())));}
 private static boolean timberMissing(ServerLevel l,SettlementData.Entry e,Settlement.Building forest){
  var hall=Workshops.hall(e);var stock=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(stock==null)return false;
  var free=HallReserve.view(l,e,stock);var timber=LogisticsRoutes.chest(l,e,forest);
  var available=new net.minecraft.world.SimpleContainer(free.getContainerSize()+(timber==null?0:timber.getContainerSize()));
  for(int i=0;i<free.getContainerSize();i++)available.setItem(i,free.getItem(i).copy());
  if(timber!=null)for(int i=0;i<timber.getContainerSize();i++)available.setItem(free.getContainerSize()+i,timber.getItem(i).copy());
  for(var want:Workshops.wants(l,e))for(var in:Workshops.needs(l,e,Workshops.spec("town_hall"),free,List.of(want)))
   if(Arrays.stream(in.ingredient().getItems()).anyMatch(s->s.is(ItemTags.LOGS))
      &&(timber==null||LogisticsRoutes.count(timber,in::matches)<in.count())){
    // A tag order accepts any matching output. The first missing oak recipe does not
    // require a new forester when a real birch recipe can use the village's existing stock.
    // This only plans against copies; goods still travel and are paid by the normal journals.
    if(Workshops.plan(l,e,hall,available,List.of(want))!=null)continue;
    if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH timberNeed want={} wantCount={} needClass={} raw={} needed={} foresterStock={}",want.ingredient().toJson(),want.count(),want.need(),in.ingredient().toJson(),in.count(),timber==null?0:LogisticsRoutes.count(timber,in::matches));
    return true;
   }
  return false;
 }
 private static boolean finishingTree(ServerLevel l,Settlement.Building from){
  if(!from.type().equals("forester"))return false;
  var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+from.id()+".bin");
  return java.nio.file.Files.exists(file)&&Set.of("dig","replant","sapling","nursery_soil").contains(org.villageastra.persistence.NbtRecord.read(file).getString("stage"));
 }
 public static boolean tick(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(s.governance().playerMayor()!=null)return false;
  boolean research=!org.villageastra.server.BookResearch.wants(l,e).isEmpty();
  if(HallUpgradeGoal.pending(l,s.id())){
   var project=HallUpgradeGoal.fundingView(l,s.id());if((project.getBoolean("funded")||s.governance().paused(HallConstructionPlan.projectId(project)))&&!research)return false;
  }else if(!research)return false;
  var forest=s.buildings().stream().filter(b->b.type().equals("forester")&&Population.slots(s,b)>0).findFirst().orElse(null);
  var mine=s.buildings().stream().filter(b->b.type().equals("mine")&&Population.slots(s,b)>0).findFirst().orElse(null);if(forest==null||mine==null)return false;
  boolean forestStaffed=staffed(s,Profession.FORESTER,forest),mineStaffed=staffed(s,Profession.MINER,mine);if(forestStaffed&&mineStaffed||!forestStaffed&&!mineStaffed)return false;
  boolean wood=timberMissing(l,e,forest);var target=wood&&!forestStaffed?forest:!wood&&!mineStaffed&&!MineProspecting.needed(l,e,mine).isEmpty()?mine:null;if(target==null)return false;
  var from=target.equals(mine)?forest:mine;var role=target.equals(mine)?Profession.MINER:Profession.FORESTER;
  for(var r:s.residents())if(r.alive()&&r.life()==Resident.Life.ADULT&&!r.sick()&&Population.mayWork(r)&&from.equals(s.workplace(r.id()))
    &&l.getEntity(r.id()) instanceof ResidentEntity body&&body.escortPlayer()==null&&!CargoCustody.pending(l.getServer(),r.id())
    &&!finishingTree(l,from)&&(!from.type().equals("mine")||!NaturalSupplyGoal.primaryResourcePending(l,e,body))&&!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,r.id()))){
   // The ordinary custody journal returns the actual old tool and harvested cargo before new work.
   if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH staffing id={} from={} to={} woodMissing={} minedNeeded={}",r.id(),r.profession(),role,wood,MineProspecting.needed(l,e,mine));
   s.unassign(r.id());CargoCustody.beginReturn(body);s.assign(r.id(),role,target.id());return true;
  }
  return false;
 }
}

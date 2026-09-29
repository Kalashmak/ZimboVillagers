package org.villageastra.world;
import java.util.*;
import net.minecraft.network.chat.Component;
import org.villageastra.domain.*;
/** AD-120, AD-123: player-facing facts from the same gates, blueprints and numbers the simulation uses. Every effect line says how it applies
 *  (R3): at once to what the village has ({@code existing}), after a rebuild and its core or ring ({@code rebuild}), or to new projects ({@code new}).
 *  A node with no live effect says so plainly and names what it leads to; nothing planned is shown as an effect. */
public final class ResearchEffects {
 private ResearchEffects(){}
 private static final Map<String,List<Component>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 public record Unlock(String type,int level){}
 /** The research queue the office accepts after the current target (Research I); the panel and the card read this one number. */
 public static final int QUEUE=8;
 private static final List<String> ROMAN=List.of("I","II","III","IV","V","VI");
 /** The language the catalogue title is read in: the client's own, or English on a server. */
 public static String locale(){var v=net.minecraft.locale.Language.getInstance().getOrDefault("research.villageastra.branch.housing");return v.chars().anyMatch(c->c>='А'&&c<='я')?"ru_ru":"en_us";}
 /** R5: branch, numeral and the catalogue's short essence: «Жильё II — дом на 4 жителя». */
 public static Component title(String id){var n=ResearchCatalog.get(id);
  return Component.empty().append(Component.translatable("research.villageastra.branch."+n.branch())).append(" "+ROMAN.get(n.tier()-1)+" — "+(locale().equals("ru_ru")?n.ru():n.en()));}
 private static Component text(String key,Object... args){return Component.translatable("research.villageastra.fact."+key,args);}
 /** The application tag of an effect line (R3). */
 public static Component applies(String how){return Component.translatable("research.villageastra.applies."+how);}
 public static Component tagged(Component line,String how){return line.copy().append(" ").append(applies(how));}
 public static void clearCache(){CACHE.clear();}
 public static List<Unlock> unlocks(String id){var out=new ArrayList<Unlock>();for(var d:BuildingBlueprints.designs()){
  String type=d.id();if(type.equals("home_2")||type.equals("town_hall_2")||type.equals("town_hall_3")||!CoreCatalog.canonical(type).equals(type))continue;
  for(int level=1;level<=BuildingTiers.max(type);level++)if((level==1?ResearchGate.forDesign(type):BuildingTiers.research(type,level)).contains(id))out.add(new Unlock(type,level));
 }return List.copyOf(out);}
 public static List<Component> describe(String id){return CACHE.computeIfAbsent(locale()+"/"+id,key->build(id));}
 /** AD-131: the forester's hut has no level-V cycle of Machines (its saw and grove are its own, ForestryMachines). */
 private static final Set<String> CYCLE=Set.of("farm","mine",Quarry.BUILDING);
 /** AD-136 (§4.3): an INTERIM card shows what works today and, apart and grey, the owner's words as «Будет: …»; a PLANNED card shows only that
  *  line, marked «в разработке», with no number or arrow that would read as working. */
 private static Component future(ResearchCatalog.Node n){return Component.literal(locale().equals("ru_ru")?n.futureRu():n.futureEn());}
 private static List<Component> build(String id){var node=ResearchCatalog.get(id);var out=new ArrayList<Component>();
  if(node.status().equals("PLANNED")){out.add(text("planned",node.phase(),future(node)));addNext(id,out);return List.copyOf(out);}
  for(var u:unlocks(id)){String how=u.level()==1?"new":"rebuild";
   out.add(tagged(text("unlock",Component.translatable("building.villageastra."+u.type()),ROMAN.get(u.level()-1)),how));
   var req=u.level()==1?ResearchGate.forDesign(u.type()):BuildingTiers.research(u.type(),u.level());
   for(var other:req)if(!other.equals(id))out.add(text("also",title(other)));
   for(var effect:CoreEffects.effects(u.type())){if(u.type().equals("quarry")&&effect.id().equals("floor"))continue;var name=Component.translatable("core.villageastra.effect."+effect.id());
    if(!effect.active())continue; // R4: an effect that is not live is not shown as one
    out.add(u.level()==1?tagged(text("value",name,effect.at(1),text("unit."+effect.unit())),"new"):tagged(text("number",name,effect.at(u.level()-1),effect.at(u.level()),text("unit."+effect.unit())),"rebuild"));
    // AD-123: the atlas area (Atlas.area(l,e)) is also where village roads, clearings, houses and the quarry may be ordered.
    if(u.type().equals("cartographer")&&effect.id().equals("survey")&&u.level()>1)out.add(text("survey_orders"));
   }
   // AD-136 (D9): the machinery of a building is the top of its own ladder (balance/automation.json).
   if(u.level()>1&&u.level()==Automation.level(u.type(),false)){var g=Automation.grant(u.type());int p=Machines.period(u.level());
    if(g.bench())out.add(tagged(text("machinery.4",title(id),p),"rebuild"));
    if(g.dig()||g.cycle()&&!u.type().equals("farm"))out.add(tagged(text("machinery.5",title(id),p),"rebuild"));
    if(g.haul())out.add(tagged(text("machinery.6",title(id),p),"rebuild"));}
   // AD-130: what the farm's own levels build — the fields, the barn and its floors, and at VI the machine that works them all.
   if(u.type().equals("farm")&&u.level()>=2)out.add(tagged(u.level()==6?text("farm_machine",title(id),Machines.period(6),FarmField.FIELDS):u.level()>=FarmField.BARN_FROM
    ?text("farm_barn."+u.level(),FarmField.modules(u.level()).size(),FarmField.UPPER_SOIL*FarmField.added(u.level(),false).size(),FarmField.farmers(u.level()))
    :text("farm_fields",FarmField.modules(u.level()).size()),"rebuild"));
   // AD-131: what the forester's hut's levels do besides its numbers.
   if(u.type().equals(ForesterHut.TYPE)&&u.level()>=2)out.add(tagged(switch(u.level()){
    case 2->text("forest.2");case 3->text("forest.3",ForestBalance.SAPLING_STOCK);
    case 4->text("forest.4",ForestBalance.PLANKS_PER_LOG,f(ForestBalance.sawPeriod(4)/20.0),ForestBalance.LOG_KEEP);
    case 5->text("forest.5",f(ForestBalance.walkSpeed(5)),ForestBalance.treesPerTrip(5));
    default->text("forest.6",ForestBalance.GROVE.size(),VANILLA_GROWTH/ForestBalance.GROW_TICKS,ForestBalance.GROW_TICKS/20);},"rebuild"));
   // AD-139: what the restaurant's levels do besides its numbers (seats, couriers and labour are the core's lines above).
   if(Dining.TYPE.equals(u.type())){var fact=switch(u.level()){
     case 1->text("restaurant.1",Dining.SIT_TICKS/20);
     case 2->text("restaurant.2",Workshops.spec(Dining.TYPE,2).outputItems().size()-Workshops.spec(Dining.TYPE,1).outputItems().size());
     case 3->{var a=Workshops.custom(Dining.TYPE,"bread",2);var b=Workshops.custom(Dining.TYPE,"bread",3);yield text("restaurant.3",a[0],b[0],a[1],b[1]);}
     case 4->text("restaurant.4",Dining.NEAR_WORK_RADIUS,Dining.COURIER_RADIUS,Dining.HAND_PORTIONS);
     // The dog and cart work only once the livestock work brings the village dogs (VillageDogs); until then the card's grey line says so.
     case 5->VillageDogs.provided()?text("restaurant.5",Dining.CART_PORTIONS,Dining.COURIER_CART_RADIUS):null;
     default->text("restaurant.6");};
    if(fact!=null)out.add(tagged(fact,u.level()==1?"new":"rebuild"));}
   // AD-147: what the warehouse's levels do besides its numbers (load, storage, cart stacks and couriers are the core's lines above).
   if(WarehouseStore.TYPE.equals(u.type())){var fact=switch(u.level()){
     case 1->text("warehouse.1",WarehouseStore.pages(1));
     case 2->text("warehouse.2",WarehouseTrips.cartStacks(2),title(WarehouseCarts.HALL_CARTS));
     case 3->text("warehouse.3",WarehouseStore.pages(2),WarehouseStore.pages(3));
     case 4->text("warehouse.4",Staff.slots(WarehouseStore.TYPE,4));
     // The wolves work only once the livestock work brings them (VillageDogs); until then the stalls stand and the card's grey line says so.
     case 5->VillageDogs.pulls()?text("warehouse.5_wolves",WarehouseTrips.cartStacks(5)):text("warehouse.5");
     default->VillageDogs.pulls()?text("warehouse.6_wolves",WarehouseSort.CATEGORIES-1,CartWolves.TEAMS):text("warehouse.6",WarehouseSort.CATEGORIES-1,Machines.period(6));};
    out.add(tagged(fact,u.level()==1?"new":"rebuild"));}
   if(CoreCatalog.coreId(u.type())!=null&&u.level()>1)out.add(text("equipment"));
  }
  HousingLadder.facts(id,out);
  if(node.branch().equals("roads")){
   for(int tier=1;tier<Roads.TIERS.size();tier++){var req=ResearchGate.forRoad(tier<<2);int last=req.stream().filter(x->x.startsWith("roads.")).mapToInt(x->ResearchCatalog.get(x).tier()).max().orElse(0);
    if(last!=node.tier())continue;var road=Roads.TIERS.get(tier);var prev=Roads.TIERS.get(tier-1);
    out.add(tagged(text("surface",Component.translatable(road.block().getDescriptionId()),f(1+road.bonus()),f(1+road.bonus()/2),Component.translatable(prev.block().getDescriptionId()),f(1+prev.bonus())),"new"));
    if(tier>=2){out.add(tagged(text("surface_existing",Component.translatable(road.block().getDescriptionId()),f(1+road.bonus())),"existing"));
     if(road.bonus()>Roads.HOSTILE_CAP)out.add(text("hostile_cap",f(1+Roads.HOSTILE_CAP)));}
    if(tier==1)out.add(text("inside"));}
   if(ResearchGate.forRoad(1<<4).contains(id))out.add(tagged(text("lighting",Roads.LAMP_SPACING,Roads.LIGHT_TARGET),"new"));
   if(ResearchGate.forRoad(1<<5).contains(id))out.add(tagged(text("fences",Roads.GATE_SPACING,Roads.POST_SPACING),"new"));
   if(ResearchGate.forRoad(5<<6).contains(id))out.add(tagged(text("width"),"new"));
  }
  Trails.facts(id,out);
  ResearchKnobs.facts(id,out);
  if(id.equals("defense.1"))out.add(tagged(text("walls"),"new"));
  if(ResearchGate.forDesign(Walls.TOWER).contains(id))out.add(tagged(text("towers"),"new"));
  if(id.equals("defense.4"))out.add(tagged(text("tower_small_stone"),"paid_rebuild"));
  if(id.equals("defense.5"))out.add(tagged(text("tower_big_stone"),"paid_rebuild"));
  if(id.equals("research.1"))out.add(tagged(text("queue",QUEUE),"existing"));
  if(id.equals(Machines.ROAD_REPAIR))out.add(tagged(text("road_maintenance"),"existing"));
  // AD-136: the hall's own branch caps every building's level; engineering V opens level VI of every branch; engineering I/II teach the hall.
  if(node.branch().equals("town_hall"))out.add(tagged(text("hall_cap",ROMAN.get(node.tier()-1)),"rebuild"));
  if(id.equals(ResearchCatalog.GATE_VI))out.add(tagged(text("vi_gate",ResearchCatalog.NODES.values().stream().filter(n->n.tier()==6).count()),"new"));
  int crafts=Workshops.hallCrafts(id).size();if(crafts>0)out.add(tagged(text("hall_crafts",crafts,Workshops.HALL_FAST/20,Workshops.HALL_SLOW/20),"existing"));
  // §4.3: «only opens the path» is said of a node that truly does nothing yet (forestry I); an INTERIM node says what it will do instead.
  if(out.isEmpty()&&!node.status().equals("INTERIM"))out.add(text("prerequisite_only"));
  if(node.branch().equals("housing"))out.add(text("housing_models",BuildingOrders.capacity("home"),BuildingOrders.capacity("home_2")));
  if(node.status().equals("INTERIM"))out.add(text("future",node.phase(),future(node)));
  addNext(id,out);
  return List.copyOf(out);
 }
 private static void addNext(String id,List<Component> out){
  var next=ResearchCatalog.NODES.values().stream().filter(n->n.requires().contains(id)).toList();
  if(!next.isEmpty()){out.add(text("next"));for(var n:next)out.add(title(n.id()));}
 }
 /** AD-131: the mean ticks a vanilla sapling takes to grow at randomTickSpeed 3 (about 16 minutes): what the grove's pace is measured against. */
 static final int VANILLA_GROWTH=19200;
 private static String f(double v){return String.format(Locale.ROOT,"%.2f",v);}
}

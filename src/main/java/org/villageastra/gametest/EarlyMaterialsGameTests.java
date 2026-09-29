package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 4.2, R2; the annex rule of AD-135): the mill, the stoneworks and the carpentry are side buildings behind their side nodes
 *  now, so nothing a village builds early may wait for them. Every design at its levels I-III (the town hall I-III included) is paid only in
 *  items the village comes by without them: raw from the farm, the forester's hut, the mine and the pen, gathered from nature, or made by the town hall (its own crafting,
 *  smelting and stonecutting lists and every workshop's custom recipe, AD-114) from those, step by step - Workshops.makeable, the very rule an
 *  NPC mayor orders by. The hall chest is empty, so nothing counts because it happens to lie there. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EarlyMaterialsGameTests {
 /** What a design costs whole (level I): every cell's items, as the builders' survey charges them. */
 private static Map<String,Integer> whole(String design){var out=new TreeMap<String,Integer>();
  for(var cell:BuildingBlueprints.layout(design,BlockPos.ZERO).entrySet()){var now=cell.getValue();if(now.isAir())continue;
   var items=BuildingOrders.materials(Blocks.AIR.defaultBlockState(),BuildingOrders.payable(now));if(items==null)continue;
   for(var item:items)if(!item.isEmpty())out.merge(item,1,Integer::sum);}
  return out;}
 @GameTest(template="empty",timeoutTicks=400) public static void everyEarlyDesignIsMadeWithoutTheSideWorkshops(GameTestHelper h){
  var t=town(h,null);
  try{
   // The resource buildings of a starting village (farm, forester's hut, mine) and the pen of Livestock I (paid in resources, no side
   // node), an idle adult who gathers natural blocks (sand, clay, dirt: NaturalSupplyGoal), and no mill, stoneworks, carpentry or smithy.
   for(var type:List.of("farm",ForesterHut.TYPE,"mine","livestock"))t.s.addBuilding(new Settlement.Building(Settlement.childId(t.s.id(),"raw/"+type),type,40,0,40));
   var idle=new Resident(Settlement.childId(t.s.id(),"raw/gatherer"),Resident.Life.ADULT,false,null,null,-1);t.s.admit(idle,Settlement.childId(t.s.id(),"home"));
   LogisticsRoutes.chest(t.l,t.e,t.hall()).clearContent();
   var missing=new TreeMap<String,TreeSet<String>>();var checked=new TreeSet<String>();
   for(var d:BuildingBlueprints.designs()){String type=d.id();
    if(type.startsWith("town_hall_"))continue;
    int top=type.equals("town_hall")?3:BuildingTiers.upgradable(type)?Math.min(3,BuildingTiers.max(type)):1;
    for(int level=1;level<=top;level++){var cost=level==1?whole(BuildingTiers.layoutId(type,1)):BuildingTiers.cost(type,level);checked.add(type+"@"+level);
     for(var item:cost.keySet())if(!Workshops.makeable(t.l,t.e,item))missing.computeIfAbsent(item,k->new TreeSet<>()).add(type+"@"+level);}}
   h.assertTrue(checked.size()>20,"The walk covers the catalogue: "+checked.size()+" "+checked);
   h.assertTrue(missing.isEmpty(),"Early designs need items only a side workshop makes: "+missing);
  }finally{done(t);}
  h.succeed();
 }
}

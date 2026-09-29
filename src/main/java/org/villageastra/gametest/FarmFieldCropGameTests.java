package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-130 (owner 2026-09-22): a crop for each of the farm's 18 fields — one field's own choice, "all fields" clearing them, only opened crops,
 *  only the mayor's current revision, and a schema-1 policy file read as the farm's crop. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmFieldCropGameTests {
 @GameTest(template="empty",batch="farm_field_crops",timeoutTicks=600) public static void eachFieldGrowsItsOwnCrop(GameTestHelper h){
  var t=FarmBarnGameTests.yard(h);
  try{
   FarmBarnGameTests.raise(h,t,4);var farm=FarmBarnGameTests.farm(t);var s=t.s();var id=s.id();
   var origin=BuildingPlacement.origin(t.e(),farm);var mayor=FakePlayerFactory.get(t.l(),new GameProfile(UUID.randomUUID(),"FieldMayor"));mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());
   s.appointPlayerMayor(mayor.getUUID());long epoch=s.governance().epoch();var policies=FarmPolicies.get(t.l().getServer());
   // A plot of field 7 (floor 1, the field over field 1) and of field 1 itself.
   var seventh=FarmField.field(7,false,false);var first=FarmField.field(1,false,false);
   var p7=cell(t,farm,seventh);var p1=cell(t,farm,first);
   h.assertTrue(!FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision(),7,"carrot"),"A crop the village has not opened is refused for a field");
   CropUnlocks.unlock(t.l(),t.e(),"minecraft:carrot","quest");CropUnlocks.unlock(t.l(),t.e(),"minecraft:potato","quest");
   h.assertTrue(!FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision()-1,7,"carrot"),"A stale revision is refused");
   h.assertTrue(FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision(),7,"carrot"),"Field 7 to carrots");
   h.assertTrue(policies.at(t.e(),p7)==FarmCrops.CARROT&&policies.at(t.e(),p1)==FarmCrops.WHEAT&&policies.own(id,farm.id(),7)&&!policies.own(id,farm.id(),1),"Field 7 grows carrots, field 1 keeps wheat");
   h.assertTrue(!FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision(),7,"carrot"),"The same crop again changes nothing and is refused");
   h.assertTrue(FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision(),15,"potato")&&policies.crop(id,farm.id(),15)==FarmCrops.POTATO,"A field not built yet keeps its choice for later");
   h.assertTrue(FarmPolicies.order(mayor,id,farm.id(),epoch,s.governance().revision(),0,"potato"),"All fields to potatoes");
   for(int n=1;n<=FarmField.FIELDS;n++)h.assertTrue(policies.crop(id,farm.id(),n)==FarmCrops.POTATO&&!policies.own(id,farm.id(),n),"Field "+n+" follows the farm's crop");
   // The card's grid shows the 18 fields; the plots of field 7 are built, those of field 13 (floor 2) are not yet.
   var card=BuildingCards.card(t.l(),t.e(),farm);var grid=card.getList("fields",Tag.TAG_COMPOUND);
   h.assertTrue(grid.size()==18&&grid.getCompound(6).getBoolean("built")&&!grid.getCompound(12).getBoolean("built")&&grid.getCompound(12).getInt("level")==5,"The card's grid of the fields: "+grid.getCompound(12));
   // A save of schema 1: its crop is the farm's crop, no field has its own.
   var old=new CompoundTag();old.putInt("schema",1);var rows=new ListTag();var row=new CompoundTag();row.putUUID("village",id);row.putUUID("building",farm.id());row.putString("crop","carrot");rows.add(row);old.put("policies",rows);
   var loaded=FarmPolicies.load(old);h.assertTrue(loaded.crop(id,farm.id(),7)==FarmCrops.CARROT&&!loaded.own(id,farm.id(),7),"A schema-1 file loads as the farm's crop");
   var saved=loaded.save(new CompoundTag());h.assertTrue(saved.getInt("schema")==2&&FarmPolicies.load(saved).crop(id,farm.id())==FarmCrops.CARROT,"And saves as schema 2");
   boolean refused=false;try{var bad=new CompoundTag();bad.putInt("schema",3);FarmPolicies.load(bad);}catch(IllegalArgumentException ex){refused=true;}h.assertTrue(refused,"An unknown schema is refused");
  }finally{FarmBarnGameTests.done(t.l(),t.s());}
  h.succeed();
 }
 private static BlockPos cell(FarmBarnGameTests.Yard t,org.villageastra.domain.Settlement.Building farm,int[] m){var c=FarmField.corner(m);return BuildingPlacement.at(t.e(),farm,c.getX()+1,c.getY()+1,c.getZ()+1);}
}

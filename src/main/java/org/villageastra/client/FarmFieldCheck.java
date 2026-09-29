package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import org.villageastra.server.SettlementData;
import org.villageastra.world.FarmField;
/** AD-104: the field of a farm the world generator really laid — every plot of its module moist farmland under wheat of mixed ages, one
 *  water source at the module's centre under its cover, open air above — checked in the generated village of the natural smoke run.
 *  A plot may stand bare only where the farmer has already reaped it: never more bare plots than wheat he has brought in or carries. */
final class FarmFieldCheck {
 private FarmFieldCheck(){}
 static void verify(ServerLevel level,SettlementData.Entry entry){
  int farms=0,plots=0,moist=0,sown=0,bare=0,ripe=0,water=0,covered=0,shaded=0,reaped=0,expected=0;var ages=new java.util.TreeSet<Integer>();var odd=new java.util.ArrayList<String>();
  for(var b:entry.settlement().buildings()){if(!b.type().equals("farm"))continue;farms++;
   // AD-112: the generated farm works (its core level within its land) every plot it was laid with.
   expected+=FarmField.workedCells(level,entry,b).size();
   for(var c:FarmField.cells(entry,b)){if(!level.hasChunkAt(c))throw new IllegalStateException("Farm plot in an unloaded chunk "+c);plots++;
    var soil=level.getBlockState(c.below());boolean wet=soil.is(Blocks.FARMLAND)&&soil.getValue(FarmBlock.MOISTURE)==FarmBlock.MAX_MOISTURE;if(wet)moist++;
    var crop=level.getBlockState(c);if(crop.is(Blocks.WHEAT)){sown++;ages.add(crop.getValue(CropBlock.AGE));if(crop.getValue(CropBlock.AGE)==CropBlock.MAX_AGE)ripe++;}
    else if(crop.isAir()&&wet)bare++;
    else if(odd.size()<6)odd.add(org.villageastra.world.BuildingPlacement.local(entry,b,c).toShortString()+"="+crop+"/"+soil);
    for(int y=2;y<=FarmField.HEADROOM;y++)if(!level.getBlockState(c.above(y-1)).isAir())shaded++;}
   for(var w:FarmField.water(entry,b)){if(level.getFluidState(w).isSource()&&level.getFluidState(w).is(FluidTags.WATER))water++;
    else odd.add("water "+w.toShortString()+"="+level.getBlockState(w)+" t="+level.getBiome(w).value().getBaseTemperature());
    if(level.getBlockState(w.above()).equals(FarmField.COVER))covered++;}
   // What the farmer has reaped: the wheat in the farm's chest (a new farm's chest starts empty) and the wheat he carries to it.
   var chest=org.villageastra.world.LogisticsRoutes.chest(level,entry,b);if(chest!=null)reaped+=chest.countItem(Items.WHEAT);
   var work=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+b.id()+".bin");
   if(java.nio.file.Files.exists(work))for(var raw:org.villageastra.persistence.NbtRecord.read(work).getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((net.minecraft.nbt.CompoundTag)raw);if(item.is(Items.WHEAT))reaped+=item.getCount();}}
  if(farms!=1||plots!=expected||moist!=plots||sown+bare!=plots||bare>reaped||water!=farms||covered!=farms||shaded>0||ages.size()<2)
   throw new IllegalStateException("Generated field: farms="+farms+" plots="+plots+" worked="+expected+" moist="+moist+" sown="+sown+" bare="+bare+" reaped="+reaped+" ages="+ages+" water="+water+" covered="+covered+" shaded="+shaded+" "+odd);
  LogUtils.getLogger().info("ASTRA_FARM_FIELD VERIFIED generated field of {} plots, {} moist, {} sown (ages {}, {} ripe), {} reaped by the farmer, {} covered water source, open air above",plots,moist,sown,ages,ripe,bare,water);
 }
}

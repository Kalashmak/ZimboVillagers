package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StructureFoundationGameTests {
 @GameTest(template="empty",batch="structure_foundation",timeoutTicks=200) public static void generatedMineKeepsItsExcavatedStaircase(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(2,16,2));var layout=BuildingBlueprints.layout("mine",base);
  for(var cell:layout.entrySet()){
   l.setBlock(cell.getKey(),cell.getValue(),2);
   if(cell.getKey().getY()==base.getY())StructureFoundations.support(l,cell.getKey(),layout);
  }
  for(int z=1;z<=6;z++)for(int x=3;x<=4;x++){
   h.assertTrue(l.getBlockState(base.offset(x,-z,z)).is(Blocks.STONE_BRICK_STAIRS),"The generated tread survives at "+x+","+z);
   for(int y=1-z;y<=0;y++)h.assertTrue(l.getBlockState(base.offset(x,y,z)).isAir(),"Generation must not fill the shaft with foundation dirt: "+x+","+y+","+z);
  }
  // Ordinary floor cells still receive support over a cavity.
  h.assertTrue(l.getBlockState(base.offset(0,-1,0)).is(Blocks.DIRT),"A solid floor over air still receives its foundation");h.succeed();
 }
}

package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
/** Keeps neighboring tests' fluids, mobs and weather out of a controlled movement fixture. */
final class MovementTestEnclosure {
 private MovementTestEnclosure(){}
 static void seal(ServerLevel l,BlockPos foot,int radius,int top){
  for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
   l.setBlock(foot.offset(x,top,z),Blocks.GLASS.defaultBlockState(),2);
   if(Math.abs(x)==radius||Math.abs(z)==radius)for(int y=0;y<top;y++)l.setBlock(foot.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  }
  l.setBlock(foot.offset(radius-1,top,-radius+1),Blocks.SEA_LANTERN.defaultBlockState(),2);
 }
}

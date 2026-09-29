package org.villageastra.gametest;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.CartEntity;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CartTerrainGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aCartRollsOutOfADirtPathOntoOrdinaryGround(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(4,2,4));
  for(int x=0;x<12;x++)for(int z=-2;z<=2;z++){l.setBlock(at.offset(x,0,z),(x<4?Blocks.DIRT_PATH:Blocks.STONE).defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var dog=EntityType.WOLF.create(l);dog.setNoAi(true);dog.moveTo(at.getX()+8.5,at.getY()+1,at.getZ()+.5,-90,0);l.addFreshEntity(dog);
  var cart=new CartEntity(l,at.offset(1,1,0),UUID.randomUUID());cart.setPos(at.getX()+1.5,at.getY()+.9375,at.getZ()+.5);cart.puller(dog.getUUID());l.addFreshEntity(cart);
  try{for(int tick=0;tick<150;tick++)cart.tick();h.assertTrue(cart.getX()>at.getX()+5,"A 1/16-block path edge must not stop a cart: x="+(cart.getX()-at.getX())+" step="+cart.maxUpStep());h.succeed();}
  finally{cart.discard();dog.discard();}
 }
}

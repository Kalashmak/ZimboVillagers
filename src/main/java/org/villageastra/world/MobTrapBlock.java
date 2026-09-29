package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import org.villageastra.server.SettlementData;
/** AD-156 (owner's Engineering ladder IV): a mob trap - a plate of iron spikes flat on the ground. A hostile mob that walks onto it is
 *  held (as in cobweb) and hurt DAMAGE every time it may be hurt again; players, residents and animals pass over it unharmed. A player puts
 *  it only on village land (within Walls.MAX_RADIUS of a village centre in the same world) of a village that has Engineering IV
 *  (ServerEvents.placeMobTrap). */
public final class MobTrapBlock extends Block {
 public static final String NODE="engineering.4";
 public static final float DAMAGE=3F;
 private static final VoxelShape SHAPE=Block.box(1,0,1,15,2,15);
 public MobTrapBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).noCollission().noOcclusion());}
 @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
 @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return Block.canSupportCenter(l,p.below(),net.minecraft.core.Direction.UP);}
 @Override public BlockState updateShape(BlockState s,net.minecraft.core.Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos np){return d==net.minecraft.core.Direction.DOWN&&!canSurvive(s,l,p)?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():super.updateShape(s,d,n,l,p,np);}
 @Override public void entityInside(BlockState s,Level l,BlockPos p,Entity entity){
  if(l.isClientSide||!(entity instanceof LivingEntity mob)||!(entity instanceof Enemy)||!mob.isAlive())return;
  mob.makeStuckInBlock(s,new Vec3(.2,.05,.2));mob.hurt(l.damageSources().cactus(),DAMAGE);
 }
 /** Whether a mob trap may stand here: on the land of a village of this world that has Engineering IV. */
 public static boolean allowed(ServerLevel l,BlockPos pos){
  for(var e:SettlementData.get(l.getServer()).entries()){if(!e.dimension().equals(l.dimension().location().toString()))continue;
   double dx=pos.getX()-e.center().getX(),dz=pos.getZ()-e.center().getZ();
   if(dx*dx+dz*dz<=(double)Walls.MAX_RADIUS*Walls.MAX_RADIUS&&ResearchKnobs.done(l,e).contains(NODE))return true;}
  return false;
 }
}

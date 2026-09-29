package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
/** ROAD-008: the cart the carpenter and the smith made, set down on the ground. It belongs to the settlement it is put down in. */
public class CartItem extends Item {
 public CartItem(Properties properties){super(properties);}
 @Override public InteractionResult useOn(UseOnContext context){
  var level=context.getLevel();if(level.isClientSide)return InteractionResult.SUCCESS;
  var at=context.getClickedPos().above();
  if(!level.getBlockState(at).isAir()||!level.getBlockState(at.above()).isAir())return InteractionResult.FAIL;
  var server=(net.minecraft.server.level.ServerLevel)level;
  var entry=SettlementData.get(server.getServer()).entries().stream()
    .filter(e->e.dimension().equals(server.dimension().location().toString())&&e.center().distSqr(at)<=96*96)
    .min((a,b)->Double.compare(a.center().distSqr(at),b.center().distSqr(at))).orElse(null);
  var cart=new CartEntity(level,at,entry==null?null:entry.settlement().id());
  cart.setYRot(context.getHorizontalDirection().toYRot());
  if(!level.addFreshEntity(cart))return InteractionResult.FAIL;
  if(context.getPlayer()!=null&&!context.getPlayer().isCreative())context.getItemInHand().shrink(1);
  return InteractionResult.CONSUME;
 }
 public static BlockPos ground(net.minecraft.world.level.Level level,BlockPos at){return at;}
 public static boolean isCart(net.minecraft.world.entity.Entity entity){return entity instanceof CartEntity;}
 public static net.minecraft.world.item.ItemStack stack(){return new net.minecraft.world.item.ItemStack(VillageAstra.CART_ITEM.get());}
}

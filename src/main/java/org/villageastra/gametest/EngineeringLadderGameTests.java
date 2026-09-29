package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.Trade;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-156 (owner's Engineering ladder): III the hall learns the redstone mechanisms, the engineering office keeps a shelf of them and its
 *  engineer sells them; IV a mob trap goes only on the land of a village with Engineering IV and hurts hostile mobs, not animals. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EngineeringLadderGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void theVillageMakesRedstoneAndPutsItUpForSale(GameTestHelper h){
  var t=town(h,"engineering");var office=t.kept();var at=LogisticsRoutes.position(t.e,office);
  if(!(t.l.getBlockEntity(at) instanceof OwnedChestEntity))t.l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var shelf=LogisticsRoutes.chest(t.l,t.e,office);shelf.clearContent();var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());
  var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var repeater=new ItemStack(Items.REPEATER);
   h.assertTrue(EngineeringSales.goods().contains(Items.REPEATER)&&EngineeringSales.goods().contains(Items.PISTON),"Engineering III lists the mechanisms: "+EngineeringSales.goods());
   learn(t,"engineering.1","engineering.2");
   h.assertTrue(!Workshops.hallFast(t.l,t.e,repeater)&&EngineeringSales.wants(t.l,t.e).isEmpty(),"Before III nothing of it is made fast nor kept for sale");
   learn(t,"engineering.3");
   var wants=EngineeringSales.wants(t.l,t.e);
   h.assertTrue(Workshops.hallFast(t.l,t.e,repeater)&&wants.stream().anyMatch(w->w.matches(repeater)&&w.destination().equals(office.id())&&w.count()==EngineeringSales.SALE_STOCK),"III: the hall makes them, the office's empty shelf wants "+EngineeringSales.SALE_STOCK+" of each");
   hall.setItem(0,new ItemStack(Items.STICK,4));hall.setItem(1,new ItemStack(Items.REDSTONE,4));
   var job=Workshops.plan(t.l,t.e,t.hall(),hall,Workshops.wants(t.l,t.e));
   h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.REDSTONE_TORCH)),"With sticks and redstone the hall makes redstone torches for the shelf: "+job);
   shelf.setItem(0,new ItemStack(Items.REPEATER,2));
   h.assertTrue(EngineeringSales.wants(t.l,t.e).stream().noneMatch(w->w.matches(repeater)),"A shelf with repeaters wants none");
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.ENGINEER,office.id());
   npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+1.5,0,0);t.l.addFreshEntity(npc);
   var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),"engineering_buyer"));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(npc.getX()+1,npc.getY(),npc.getZ());
   var offer=Trade.offer(p,npc);
   h.assertTrue(offer.sells().stream().anyMatch(x->x.item()==Items.REPEATER&&x.count()==2),"The engineer sells both repeaters: "+offer.reason()+" "+offer.sells());
  }finally{npc.discard();done(t);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=100) public static void theMobTrapStandsOnVillageLandAndHurtsOnlyMonsters(GameTestHelper h){
  var t=town(h,null);var l=t.l;var at=t.e.center().offset(3,0,3);var far=t.e.center().offset(Walls.MAX_RADIUS+8,0,0);
  var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);var cow=net.minecraft.world.entity.EntityType.COW.create(l);
  try{
   h.assertTrue(!MobTrapBlock.allowed(l,at),"Without Engineering IV no trap on the village land");
   learn(t,"engineering.1","engineering.2","engineering.3","engineering.4");
   h.assertTrue(MobTrapBlock.allowed(l,at)&&!MobTrapBlock.allowed(l,far),"Engineering IV: on the village land, not beyond it");
   var trap=VillageAstra.MOB_TRAP.get().defaultBlockState();l.setBlock(at,trap,3);
   for(var mob:List.of(zombie,cow)){mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(mob);}
   float z0=zombie.getHealth(),c0=cow.getHealth();
   trap.entityInside(l,at,zombie);trap.entityInside(l,at,cow);
   h.assertTrue(zombie.getHealth()<z0-MobTrapBlock.DAMAGE/2&&cow.getHealth()==c0,"The zombie is hurt (its own armour takes a little), the cow is not: "+zombie.getHealth()+"/"+z0+" "+cow.getHealth()+"/"+c0);
  }finally{zombie.discard();cow.discard();done(t);}
  h.succeed();
 }
}

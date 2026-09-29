package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopAccessGameTests {
 @GameTest(template="empty",batch="workshop_access",timeoutTicks=100) public static void workshopCannotTakeIngredientsThroughTemporaryWall(GameTestHelper h){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"smithy",0,0,0);s.addBuilding(b);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);var person=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home.id());s.assign(person.id(),Profession.BLACKSMITH,b.id());
  var pos=LogisticsRoutes.position(e,b);for(int x=-2;x<=3;x++)for(int z=-3;z<=2;z++){l.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<=2;y++)l.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,b);stock.setItem(0,new ItemStack(Items.COBBLESTONE,3));stock.setItem(1,new ItemStack(Items.STICK,2));
  var worker=VillageAstra.RESIDENT.get().create(l);worker.bind(s.id(),person);worker.setNoAi(true);worker.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()-1.5,0,0);l.addFreshEntity(worker);
  try{Workshops.advance(l,e,b,-100,List.of(new Workshops.Want(Ingredient.of(Items.STONE_PICKAXE),1,b.id())));
   h.assertTrue(Workshops.inspect(l,b.id()).getString("stage").equals("fund"),"Real recipe awaiting payment");
   var wall=pos.offset(1,0,-1);l.setBlock(wall,Blocks.STONE.defaultBlockState(),2);l.setBlock(wall.above(),Blocks.STONE.defaultBlockState(),2);
   var goal=new WorkshopGoal(worker,true);worker.tickCount=20;goal.tick();
   h.assertTrue(stock.countItem(Items.COBBLESTONE)==3&&stock.countItem(Items.STICK)==2&&Workshops.inspect(l,b.id()).getInt("withdrawals")==0,"Close worker must not extract materials through a wall");
   l.setBlock(wall,Blocks.AIR.defaultBlockState(),2);l.setBlock(wall.above(),Blocks.AIR.defaultBlockState(),2);worker.tickCount=40;goal.tick();
   h.assertTrue(Workshops.inspect(l,b.id()).getInt("withdrawals")==1,"The same worker resumes when physical access is restored");
  }finally{worker.discard();SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());}h.succeed();
 }
}

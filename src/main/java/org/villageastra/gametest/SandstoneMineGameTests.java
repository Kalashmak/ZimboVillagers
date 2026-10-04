package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SandstoneMineGameTests {
 @GameTest(template="empty",batch="sandstone_mine",timeoutTicks=200)
 public static void minerCutsRealSedimentaryStrataWithoutAcceptingStructures(GameTestHelper h){
  var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());
  var e=new SettlementData.Entry(old.s,old.e.dimension(),old.e.center().above(24));data.add(e);
  ResearchV2Town.lay(old.l,e,old.shop,"mine");
  var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);
   t.s.admit(resident,Settlement.childId(t.s.id(),"home"));t.s.assign(resident.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(resident.id()));
   for(var rock:List.of(Blocks.SANDSTONE,Blocks.RED_SANDSTONE)){
    var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",3);state.putInt("height",5);state.putInt("descent",7);state.putInt("step",0);state.putInt("cell",12);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
    var next=MineWork.next(t.l,e,t.shop,state);var target=MineWork.at(e,t.shop,next.cell());var stand=MineWork.at(e,t.shop,next.stand());
    for(int x=-2;x<=2;x++)for(int y=-1;y<=4;y++)for(int z=-2;z<=2;z++)t.l.setBlock(stand.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
    t.l.setBlock(target,rock.defaultBlockState(),2);MineWork.write(t.l,t.shop,state);npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);npc.setOnGround(true);
    var goal=new ResourceWorkGoal(npc,true,()->6000);h.assertTrue(goal.canUse(),"Miner resumes actual drive at sedimentary layer");
    for(int i=0;i<180&&!t.l.getBlockState(target).isAir();i++)goal.tick();
    var after=MineWork.read(t.l,t.shop);
    h.assertTrue(t.l.getBlockState(target).isAir(),"Real layer excavated instead of unsafe_ground: "+rock+" status="+after.getString("status"));
    h.assertTrue(after.getList("cargo",Tag.TAG_COMPOUND).stream().map(raw->ItemStack.of((CompoundTag)raw)).filter(s->s.is(rock.asItem())).mapToInt(ItemStack::getCount).sum()==1,"Exactly one real sandstone block enters cargo");
   }
   for(var built:List.of(Blocks.CHEST,Blocks.OAK_PLANKS,Blocks.CHISELED_SANDSTONE,Blocks.CUT_SANDSTONE))h.assertTrue(!MineWork.diggable(built.defaultBlockState()),"Structural blocks remain excluded: "+built);
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}

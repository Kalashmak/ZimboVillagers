package org.villageastra.client;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.server.ConstructionNetwork;
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class ConstructionOverlay {
 public record Box(AABB shape,int color,boolean removal){}
 private static List<Box> boxes=List.of();private static CompoundTag snapshot=new CompoundTag();private static Object world;private static int age;
 public static boolean visible=true;public static float opacity=.25f;public static int mode;
 public static CompoundTag snapshot(){return snapshot.copy();}
 public static List<Box> geometry(){return boxes;}
 @SubscribeEvent public static void receive(ConstructionNetwork.Received event){
  var mc=Minecraft.getInstance();if(Boolean.getBoolean("villageastra.constructionSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_CONSTRUCTION received {}",event.tag.getAllKeys());if(mc.level==null||!event.tag.getString("dimension").equals(mc.level.dimension().location().toString()))return;
  age=0;world=mc.level;if(event.tag.equals(snapshot))return;snapshot=event.tag.copy();var next=new ArrayList<Box>();
  for(var raw:snapshot.getList("cells",Tag.TAG_COMPOUND)){
   var cell=(CompoundTag)raw;var pos=BlockPos.of(cell.getLong("pos"));boolean blocked=cell.getBoolean("blocked");
   var before=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),cell.getCompound("before"));var after=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),cell.getCompound("after"));
   if(blocked){next.add(new Box(new AABB(pos).inflate(.006),0xFFB347,true));continue;}
   add(next,before,pos,0xFF394B,true);add(next,after,pos,0x398CFF,false);
  }
  boxes=List.copyOf(next);
  // AD-126: the schematic keeps its own model of the queue and bakes only the layers that changed.
  SchematicRenderer.accept(snapshot,mc.level,mc.level.getGameTime());
 }
 private static void add(List<Box> out,BlockState state,BlockPos pos,int color,boolean removal){
  if(state.isAir())return;var mc=Minecraft.getInstance();var shape=state.getShape(mc.level,pos);
  for(var a:shape.toAabbs())out.add(new Box(a.move(pos),color,removal));
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=world||++age>140){snapshot=new CompoundTag();boxes=List.of();world=mc.level;SchematicRenderer.release();}}
 @SubscribeEvent public static void loggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){snapshot=new CompoundTag();boxes=List.of();world=null;SchematicRenderer.release();}
 public static void draw(PoseStack pose,MultiBufferSource buffers){
  for(var box:boxes){if((mode==1&&!box.removal)||(mode==2&&box.removal))continue;var a=box.shape;float r=((box.color>>16)&255)/255f,g=((box.color>>8)&255)/255f,b=(box.color&255)/255f;
   if(!box.removal)LevelRenderer.addChainedFilledBoxVertices(pose,buffers.getBuffer(RenderType.debugFilledBox()),a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ,r,g,b,opacity);
   LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),a.inflate(box.removal?.004:.001),r,g,b,Math.min(1,opacity+.55f));
  }
 }
 public static java.util.List<CompoundTag> missingMaterials(CompoundTag tag){return tag.getList("materials",Tag.TAG_COMPOUND).stream().map(raw->(CompoundTag)raw).filter(m->m.getInt("missing")>0).toList();}
 /** A design id carries its tier after @; only the base is a translation key. */
 public static net.minecraft.network.chat.Component designName(String design){
  var base=net.minecraft.network.chat.Component.translatable("building.villageastra."+org.villageastra.world.BuildingBlueprints.base(design));
  return design.contains("@")?net.minecraft.network.chat.Component.translatable("construction.villageastra.building_level",base,OfficeUi.roman(org.villageastra.world.BuildingBlueprints.level(design))):base;
 }
 public static net.minecraft.network.chat.Component projectName(CompoundTag t){String design=t.getString("design"),kind=t.getString("kind");if(t.getBoolean("road")||kind.equals("road"))return net.minecraft.network.chat.Component.translatable("survey.villageastra.road");if(kind.equals("nursery"))return net.minecraft.network.chat.Component.translatable("building.villageastra.forester");if(!design.isEmpty())return designName(design);if(kind.equals("road"))return net.minecraft.network.chat.Component.translatable("survey.villageastra.road");return net.minecraft.network.chat.Component.translatable("construction.villageastra.hall",t.getInt("level"));}
 @SubscribeEvent public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post event){
  var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||mc.options.renderDebug||!visible||!snapshot.hasUUID("id"))return;
  SchematicHud.draw(event.getGuiGraphics(),snapshot);
 }
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
  if(!visible){SchematicRenderer.layersDrawn=0;return;}
  SchematicRenderer.render(e,Minecraft.getInstance().renderBuffers().bufferSource(),boxes);
 }
}

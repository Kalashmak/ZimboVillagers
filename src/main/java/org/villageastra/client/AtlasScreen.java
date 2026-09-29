package org.villageastra.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.villageastra.VillageAstra;
import org.villageastra.server.ConstructionNetwork;
/** AD-037/AD-056: the settlement map — an isometric model of every block the cartographers remembered, whole columns deep,
 *  with the residents drawn as their own faces exactly where they run, a column inspector and the mayor's planner. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class AtlasScreen extends Screen {
 private static final Map<Long,CompoundTag> CHUNKS=new LinkedHashMap<>();private static long generation=-1;private static UUID village;private static CompoundTag live=new CompoundTag();
 static final AtlasTerrain TERRAIN=new AtlasTerrain();
 private static final BufferBuilder BUILDER=new BufferBuilder(1<<21);
 /** Title bar and tool bar heights: the map takes the whole screen between them. */
 static final int TOP=20,BAR=22,PANEL_W=156;
 private static final int[] TILTS={30,45,60,90};
 /** Depth scale of the scene and its place inside the GUI depth range: deep columns never fall out of the view volume. */
 private static final float DEPTH=2F,LAYER=4000F;
 private static final int GRID_LIMIT=160;
 private static final long GLIDE=500;
 private static final List<ResourceLocation> SKINS=org.villageastra.domain.ResidentProfile.SKINS.stream()
  .map(name->new ResourceLocation("minecraft","textures/entity/player/wide/"+name+".png")).toList();
 // ISO-003/ISO-005: a plan is a design put on a real spot, its estimate, and up to three drafts kept for comparison.
 private static final List<CompoundTag> DRAFTS=new ArrayList<>();private static CompoundTag plan=new CompoundTag();private static int designIndex;
 // AD-057: the map is also where the mayor gives orders — a house, a road, a clearing or a quarry, each tool with its own marks and a dry run from the server.
 // AD-058: buildings are picked in the view itself — a click on a house opens its card; there is no separate buildings tool.
 static final int VIEW=0,HOUSE=1,ROAD=2,DEMOLISH=3,WALL=4,TRAIL=5;
 /** AD-123: the trail being drawn — the neighbour it goes to, its surface (0 dirt path, 1 gravel), lamps, and up to eight waypoints clicked on the map. */
 private static int trailTarget,trailSurface;private static boolean trailLight;private static final List<BlockPos> WAYPOINTS=new ArrayList<>();
 /** AD-094: the castle wall the mayor is drawing — its shape (0 square, 1 round) and its radius from the hall. */
 private static int wallShape,wallRadius=24;
 /** AD-127: the headroom of the wall fitted to the village (shape 2), and the number the wall tool sends for the shape chosen. */
 private static int wallHeadroom=org.villageastra.world.Walls.FIT.headroom();
 private static int wallLevel(){return wallShape==2?wallHeadroom:wallRadius;}
 private static final String[] TOOLS={"view","house","road","demolish","wall","trail"};
 /** AD-125: moving a picked building — no tab of its own, it starts from the building's card. A named constant, never a tab index. */
 static final int MOVE=16;
 /** The building being moved (its card as the map last knew it), the clicked ground and the new origin the dry run is asked for. */
 private static CompoundTag movingCard;private static BlockPos moveGround,moveTarget;
 // Road variant bits: route, surface, lamps, fence and a width of one to five blocks (three to start with).
 private static int tool,roadVariant=3<<6,level=Integer.MIN_VALUE,changes,asked,answered;private static long askedAt;
 private static UUID building;
 private static BlockPos cornerA,cornerB;
 // AD-067 (A07-VIS-004): the floor of a planned house can be raised or lowered, and the plan seen «now» (blue volume) or «after» (the real blocks).
 private static int floorOffset;private static boolean after;private static BlockPos houseGround;private VertexBuffer future;private String futureKey="";private static CompoundTag preview=new CompoundTag(),outcome=new CompoundTag();
 private static UUID followed;private static long chunk=Long.MIN_VALUE;private static BlockPos selected;
 private static int tilt=1;private static boolean info=true;
 /** Where each resident was and where it is going: the map glides between two updates instead of jumping. */
 private static final Map<UUID,double[]> MOTION=new HashMap<>();
 private VertexBuffer terrain;private boolean dirty=true,framed;private long built;
 private float focusX,focusY,focusZ,zoom,panX,panY,yaw=45;
 private int refresh;private BlockPos hover;
 private final Map<String,Button> buttons=new LinkedHashMap<>();
 /** Screen spots of the residents drawn in the last frame: clicks pick them and the probe checks them. */
 private final Map<UUID,float[]> icons=new LinkedHashMap<>();
 private static List<String> designs(){return org.villageastra.world.BuildingBlueprints.designs().stream().map(org.villageastra.world.BuildingBlueprints.Design::id)
  .filter(org.villageastra.world.BuildingOrders.ORDERABLE::contains).sorted().toList();}
 private static String design(){var all=designs();if(all.isEmpty())return "";
  if(designIndex==0&&all.contains("home"))designIndex=all.indexOf("home");
  return all.get(Math.floorMod(designIndex,all.size()));}
 public AtlasScreen(){super(Component.translatable("atlas.villageastra.title"));}
 @SubscribeEvent public static void received(ConstructionNetwork.AtlasReceived event){
  var t=event.tag;if(t.getBoolean("missing")){live=t;return;}
  if(!t.getUUID("village").equals(village)||t.getLong("generation")!=generation){CHUNKS.clear();TERRAIN.clear();MOTION.clear();village=t.getUUID("village");generation=t.getLong("generation");}
  live=t;boolean added=false;
  for(var raw:t.getList("chunks",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(CHUNKS.put(c.getLong("pos"),c)==null){TERRAIN.put(c.getLong("pos"),c);added=true;}}
  long now=System.currentTimeMillis();var seen=new HashSet<UUID>();
  for(var raw:t.getList("residents",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;if(!p.hasUUID("id"))continue;var id=p.getUUID("id");seen.add(id);
   var block=BlockPos.of(p.getLong("pos"));
   double x=p.contains("x")?p.getDouble("x"):block.getX()+.5,y=p.contains("y")?p.getDouble("y"):block.getY(),z=p.contains("z")?p.getDouble("z"):block.getZ()+.5;
   var motion=MOTION.get(id);
   if(motion==null)MOTION.put(id,new double[]{x,y,z,x,y,z,now});
   else{var at=position(motion,now);MOTION.put(id,new double[]{at[0],at[1],at[2],x,y,z,now});}
  }
  MOTION.keySet().retainAll(seen);
  if(Minecraft.getInstance().screen instanceof AtlasScreen s){if(added)s.dirty=true;if(CHUNKS.size()<t.getInt("total"))request();}
 }
 private static double[] position(double[] m,long now){double k=Mth.clamp((now-m[6])/(double)GLIDE,0,1);return new double[]{m[0]+(m[3]-m[0])*k,m[1]+(m[4]-m[1])*k,m[2]+(m[5]-m[2])*k};}
 private static void request(){ConstructionNetwork.sendAtlas(new ConstructionNetwork.AtlasRequest(generation,CHUNKS.size()));}
 @SubscribeEvent public static void planned(ConstructionNetwork.PlanReceived event){plan=event.tag;}
 @SubscribeEvent public static void ordered(ConstructionNetwork.MapResultReceived event){
  var t=event.tag;
  // A late wall answer for an older radius or shape is no answer: the current one is asked again.
  if(t.getInt("action")==0){if(t.getInt("tool")==server(tool)&&(tool!=WALL||!t.contains("radius")||t.getInt("radius")==wallLevel()&&t.getInt("shape")==wallShape)){preview=t;answered=asked;}return;}
  outcome=t;
  // A placed order clears the marks: the next click starts a new one.
  if(t.getBoolean("ordered")){cornerA=null;cornerB=null;preview=new CompoundTag();if(t.getInt("tool")==org.villageastra.world.MapOrders.HOUSE)plan=new CompoundTag();
   // AD-125: an ordered move goes back to the view with the moved building's card open, its progress shown there.
   if(t.getInt("tool")==org.villageastra.world.MapOrders.RELOCATE&&tool==MOVE){tool=VIEW;moveTarget=null;moveGround=null;if(movingCard!=null&&movingCard.hasUUID("id"))building=movingCard.getUUID("id");}}
 }
 private static int server(int tool){return switch(tool){case MOVE->org.villageastra.world.MapOrders.RELOCATE;case HOUSE->org.villageastra.world.MapOrders.HOUSE;case ROAD->org.villageastra.world.MapOrders.ROAD;case DEMOLISH->org.villageastra.world.MapOrders.DEMOLISH;case WALL->org.villageastra.world.MapOrders.WALL;default->-1;};}
 /** Marks changed: the dry run is asked again, and asked once more if the server's rate limit swallowed it. */
 private static void marksChanged(){changes++;preview=new CompoundTag();}
 private static void ask(){
  if(asked==changes&&(answered==asked||System.currentTimeMillis()-askedAt<400))return;
  if(tool==WALL&&live.hasUUID("village")){asked=changes;askedAt=System.currentTimeMillis();long c=live.getLong("center");
   ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(live.getUUID("village"),live.getLong("epoch"),org.villageastra.world.MapOrders.WALL,0,c,c,wallShape,wallLevel(),""));return;}
  if(tool==MOVE&&live.hasUUID("village")&&moveTarget!=null&&movingCard!=null){asked=changes;askedAt=System.currentTimeMillis();
   ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(live.getUUID("village"),live.getLong("epoch"),org.villageastra.world.MapOrders.RELOCATE,0,moveTarget.asLong(),movingCard.getLong("pos"),turns,0,""));return;}
  if(!live.hasUUID("village")||cornerA==null||cornerB==null||(tool!=ROAD&&tool!=DEMOLISH))return;
  asked=changes;askedAt=System.currentTimeMillis();
  ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(live.getUUID("village"),live.getLong("epoch"),server(tool),0,cornerA.asLong(),cornerB.asLong(),roadVariant,level,""));
 }
 private void order(){
  if(!live.hasUUID("village"))return;var village=live.getUUID("village");long epoch=live.getLong("epoch");
  switch(tool){
   case HOUSE->{if(plan.contains("origin"))ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,org.villageastra.world.MapOrders.HOUSE,1,plan.getLong("origin"),plan.getLong("origin"),plan.getInt("variant"),0,plan.getString("design")));}
   case ROAD,DEMOLISH->{if(cornerA!=null&&cornerB!=null)ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,server(tool),1,cornerA.asLong(),cornerB.asLong(),roadVariant,level,""));}
   case WALL->{long c=center().asLong();ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,org.villageastra.world.MapOrders.WALL,1,c,c,wallShape,wallLevel(),""));}
   case MOVE->{if(moveTarget!=null&&movingCard!=null&&preview.getBoolean("ok"))ConstructionNetwork.sendMapOrder(new ConstructionNetwork.MapOrder(village,epoch,org.villageastra.world.MapOrders.RELOCATE,1,moveTarget.asLong(),movingCard.getLong("pos"),turns,0,""));}
   case TRAIL->{var n=neighbour();if(n!=null)ConstructionNetwork.sendTrail(new ConstructionNetwork.TrailOrder(village,epoch,n.getUUID("id"),org.villageastra.server.TrailOrders.ORDER,trailSurface,trailLight,WAYPOINTS.stream().mapToLong(BlockPos::asLong).toArray()));}
   default->{}
  }
 }
 /** The neighbour the trail tool points at, among those the atlas page lists. */
 static CompoundTag neighbour(){var list=live.getCompound("trails").getList("neighbours",Tag.TAG_COMPOUND);if(list.isEmpty())return null;return list.getCompound(Math.floorMod(trailTarget,list.size()));}
 static CompoundTag trailState(){return live.getCompound("trails").getCompound("trail");}
 static List<BlockPos> waypoints(){return WAYPOINTS;}
 /** AD-159 VI: the capture button names its neighbour. */
 private static Component captureLabel(){var n=neighbour();return Component.translatable("atlas.villageastra.capture",n==null?"":VillageNameText.shown(n.getString("name")));}
 private static Component trailTargetLabel(){var n=neighbour();if(n==null)return Component.translatable("atlas.villageastra.trail.no_neighbour");var c=BlockPos.of(n.getLong("center"));var o=BlockPos.of(live.getLong("center"));
  if(!n.getString("name").isEmpty())return Component.translatable("atlas.villageastra.trail.target_named",VillageNameText.shown(n.getString("name")),n.getInt("distance"),Component.translatable("atlas.villageastra.trail.dir."+direction(c.getX()-o.getX(),c.getZ()-o.getZ())));
  return Component.translatable("atlas.villageastra.trail.target",n.getInt("distance"),Component.translatable("atlas.villageastra.trail.dir."+direction(c.getX()-o.getX(),c.getZ()-o.getZ())));}
 private static String direction(int dx,int dz){double a=Math.toDegrees(Math.atan2(dx,-dz));int i=(int)Math.round(((a%360)+360)%360/45)%8;return List.of("n","ne","e","se","s","sw","w","nw").get(i);}
 private static Component trailSurfaceLabel(){return Component.translatable("atlas.villageastra.surface."+trailSurface);}
 // Read by the client probes.
 static int opened(){return CHUNKS.size();}
 static CompoundTag live(){return live;}
 static CompoundTag plan(){return plan;}
 static int drafts(){return DRAFTS.size();}
 static ListTag people(){return live.getList("residents",Tag.TAG_COMPOUND);}
 static long selectedChunk(){return chunk;}
 static BlockPos selected(){return selected;}
 static UUID following(){return followed;}
 static boolean canopy(){return TERRAIN.crowns;}
 static boolean isolated(){return TERRAIN.isolate!=Long.MIN_VALUE;}
 static long heightSum(){return TERRAIN.heightSum();}
 static int drawnBlocks(){return TERRAIN.blocks;}
 static int drawnQuads(){return TERRAIN.quads;}
 static int tool(){return tool;}
 /** Opens the map on a tool, as the office's buildings tab does. */
 static void openTool(int value){tool=value;cornerA=null;cornerB=null;}
 static UUID selectedBuilding(){return building;}
 static CompoundTag card(){if(building==null)return null;for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.hasUUID("id")&&t.getUUID("id").equals(building))return t;}return null;}
 static CompoundTag preview(){return preview;}
 static CompoundTag outcome(){return outcome;}
 static int level(){return level;}
 static BlockPos corner(boolean first){return first?cornerA:cornerB;}
 /** How many blocks of the selected column the map remembers. */
 static int columnDepth(){if(selected==null)return 0;var c=TERRAIN.column(selected.getX(),selected.getZ());return c==null?0:c.states.length;}
 float zoomLevel(){return zoom;}
 float[] focus(){return new float[]{focusX,focusY,focusZ};}
 Map<UUID,float[]> icons(){return icons;}
 /** True when a click at this spot would pick a block: on the map, off the panel, off every face. */
 boolean pickable(double mx,double my){return my>=TOP&&my<height-BAR&&!overPanel(mx,my)&&iconAt(mx,my)==null&&pickAt(scene(),mx,my)!=null;}
 /** Screen spot of the middle of a block's top face in the current view. */
 float[] screenOf(BlockPos top){var o=center();var v=scene().transformPosition(new Vector3f(top.getX()-o.getX()+.5F,top.getY()+1-o.getY(),top.getZ()-o.getZ()+.5F));return new float[]{v.x,v.y};}
 /** Puts a block in the middle of the view, as dragging the map there would. */
 void lookAt(BlockPos p){var o=center();followed=null;panX=0;panY=0;focusX=p.getX()-o.getX()+.5F;focusY=p.getY()+1-o.getY();focusZ=p.getZ()-o.getZ()+.5F;}
 /** Looks at a spot from a given height of zoom, as the wheel and dragging would. */
 void overview(BlockPos p,float scale){lookAt(p);zoom=Mth.clamp(scale,.5F,64F);}
 int[] buttonCenter(String name){var b=buttons.get(name);return b==null?null:new int[]{b.getX()+b.getWidth()/2,b.getY()+b.getHeight()/2};}
 /** Probe hook: the button itself — an inactive one cannot be found under the mouse. */
 Button widget(String name){return buttons.get(name);}
 private void requestPlan(BlockPos origin){if(!design().isEmpty())ConstructionNetwork.sendPlan(new ConstructionNetwork.PlanRequest(design(),turns,snap(origin).asLong()));}
 // AD-068: the design is turned by quarter turns clockwise before it is placed.
 private static int turns;
 static int turns(){return turns;}
 private static Component turnLabel(){return Component.translatable("atlas.villageastra.turn",turns*90);}
 /** A new house lines up with the nearest building when the mayor clicks close to its axis, so a settlement grows in rows and not at random. */
 private BlockPos snap(BlockPos origin){
  BlockPos best=null;int distance=Integer.MAX_VALUE;
  for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var at=BlockPos.of(((CompoundTag)raw).getLong("pos"));
   // AD-125 (C19): a building being moved does not line up with itself — its own card is always the nearest one.
   if(tool==MOVE&&movingCard!=null&&((CompoundTag)raw).hasUUID("id")&&((CompoundTag)raw).getUUID("id").equals(movingCard.getUUID("id")))continue;
   int d=Math.abs(at.getX()-origin.getX())+Math.abs(at.getZ()-origin.getZ());
   if(d<distance){distance=d;best=at;}}
  if(best==null||distance>24)return origin;
  int x=Math.abs(best.getX()-origin.getX())<=3?best.getX():origin.getX();
  int z=Math.abs(best.getZ()-origin.getZ())<=3?best.getZ():origin.getZ();
  return new BlockPos(x,origin.getY(),z);
 }
 /** Blocks between the planned footprint and the nearest existing building — the settlement keeps a three-cell buffer. */
 private int clearance(CompoundTag plan){
  if(!plan.contains("origin"))return -1;var o=BlockPos.of(plan.getLong("origin"));int w=plan.getInt("width"),d=plan.getInt("depth"),best=Integer.MAX_VALUE;
  for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var at=BlockPos.of(t.getLong("pos"));
   int gapX=Math.max(0,Math.max(at.getX()-(o.getX()+w-1),o.getX()-(at.getX()+t.getInt("w")-1)));
   int gapZ=Math.max(0,Math.max(at.getZ()-(o.getZ()+d-1),o.getZ()-(at.getZ()+t.getInt("d")-1)));
   best=Math.min(best,Math.max(gapX,gapZ));}
  return best==Integer.MAX_VALUE?-1:best;
 }
 private Component designLabel(String id){return Component.translatable("atlas.villageastra.design",Component.translatable("building.villageastra."+id));}
 private static Component crownsLabel(boolean on){return Component.translatable(on?"atlas.villageastra.crowns_on":"atlas.villageastra.crowns_off");}
 private static Component sectionLabel(){return Component.translatable(TERRAIN.sideAxis==1?"atlas.villageastra.section_x":TERRAIN.sideAxis==2?"atlas.villageastra.section_z":"atlas.villageastra.section_off");}
 /** Where a new section plane goes: through the chosen chunk, or through the settlement when none is chosen. */
 private BlockPos sectionAt(){return chunk!=Long.MIN_VALUE?new net.minecraft.world.level.ChunkPos(chunk).getMiddleBlockPosition(0):center();}
 private static Component wallShapeLabel(){return Component.translatable(wallShape==2?"atlas.villageastra.wall.fitted":wallShape==1?"atlas.villageastra.wall.round":"atlas.villageastra.wall.square");}
 private static Component chunkLabel(){return Component.translatable(TERRAIN.isolate==Long.MIN_VALUE?"atlas.villageastra.chunk_only":"atlas.villageastra.chunk_all");}
 private static Component tiltLabel(){return Component.translatable("atlas.villageastra.tilt",TILTS[tilt]);}
 private Button button(String name,Component label,int wide,Button.OnPress press){
  var b=Button.builder(label,press).bounds(0,0,Math.max(16,wide+10),18).build();buttons.put(name,b);addRenderableWidget(b);return b;}
 private Button button(String name,Component label,Button.OnPress press){return button(name,label,font.width(label),press);}
 @Override protected void init(){
  request();buttons.clear();
  // The map opens close enough to tell one block from another: about two dozen screen pixels per block, whatever the GUI scale.
  if(zoom==0)zoom=Math.max(5F,22F/(float)Minecraft.getInstance().getWindow().getGuiScale());
  // Tool tabs along the title bar; the tab in use is shown pressed.
  var tabs=new ArrayList<Button>();
  for(int i=0;i<TOOLS.length;i++){final int index=i;
   tabs.add(button("tool_"+TOOLS[i],Component.translatable("atlas.villageastra.tool."+TOOLS[i]),b->{tool=index;cornerA=null;cornerB=null;marksChanged();outcome=new CompoundTag();refreshTools();}));}
  tabs.add(button("done",Component.literal("✕"),b->onClose()));
  int x=width-4;for(int i=tabs.size()-1;i>=0;i--){var b=tabs.get(i);x-=b.getWidth();b.setPosition(x,1);x-=2;}
  // Buttons of the tool panel: rows under the panel title and one row at its foot.
  int left=width-PANEL_W+2,full=PANEL_W-12,half=(PANEL_W-15)/2,row1=TOP+18,row2=TOP+38,row3=TOP+58,foot=height-BAR-24;
  place(button("floor_down",Component.translatable("atlas.villageastra.floor_down"),b->{floorOffset=Math.max(-3,floorOffset-1);replan();}),left,row2,half);
  place(button("floor_up",Component.translatable("atlas.villageastra.floor_up"),b->{floorOffset=Math.min(3,floorOffset+1);replan();}),left+half+3,row2,half);
  place(button("after",afterLabel(),b->{after=!after;b.setMessage(afterLabel());}),left,row3,half);
  place(button("turn",turnLabel(),b->{turns=(turns+1)%4;b.setMessage(turnLabel());if(plan.contains("origin"))requestPlan(BlockPos.of(plan.getLong("origin")));}),left+half+3,row3,half);
  place(button("design",designLabel(design()),full-10,b->{designIndex++;b.setMessage(designLabel(design()));if(plan.contains("origin"))requestPlan(BlockPos.of(plan.getLong("origin")));}),left,row1,full);
  place(button("draft",Component.translatable("atlas.villageastra.draft"),b->{if(!plan.isEmpty()&&!plan.contains("missing")){DRAFTS.removeIf(d->d.getLong("origin")==plan.getLong("origin"));DRAFTS.add(0,plan.copy());while(DRAFTS.size()>org.villageastra.world.Plans.DRAFTS)DRAFTS.remove(DRAFTS.size()-1);}}),left,foot,half);
  place(button("order",Component.translatable("atlas.villageastra.order"),b->order()),left+half+3,foot,half);
  place(button("reset",Component.translatable("atlas.villageastra.reset"),b->{cornerA=null;cornerB=null;marksChanged();}),left,foot,half);
  place(button("route",routeLabel(),b->{roadVariant=(roadVariant&~3)|(((roadVariant&3)+1)%4);if((roadVariant&3)==3)roadVariant&=~32;b.setMessage(routeLabel());marksChanged();}),left,row1,full);
  place(button("surface",surfaceLabel(),b->{roadVariant=(roadVariant&~12)|((((roadVariant>>2)&3)+1)%4<<2);b.setMessage(surfaceLabel());marksChanged();}),left,row2,full);
  place(button("light",toggleLabel("light",(roadVariant>>4&1)==1),b->{roadVariant^=16;b.setMessage(toggleLabel("light",(roadVariant>>4&1)==1));marksChanged();}),left,row3,half);
  place(button("fence",toggleLabel("fence",(roadVariant>>5&1)==1),b->{roadVariant^=32;b.setMessage(toggleLabel("fence",(roadVariant>>5&1)==1));marksChanged();}),left+half+3,row3,half);
  place(button("wall_shape",wallShapeLabel(),b->{wallShape=(wallShape+1)%3;b.setMessage(wallShapeLabel());marksChanged();}),left,row1,full);
  place(button("wall_smaller",Component.translatable("atlas.villageastra.wall.smaller"),b->{if(wallShape==2)wallHeadroom=org.villageastra.world.Walls.headroom(wallHeadroom-(hasShiftDown()?1:4));else wallRadius=Math.max(org.villageastra.world.Walls.MIN_RADIUS,wallRadius-(hasShiftDown()?1:4));marksChanged();}),left,row2,half);
  place(button("wall_larger",Component.translatable("atlas.villageastra.wall.larger"),b->{if(wallShape==2)wallHeadroom=org.villageastra.world.Walls.headroom(wallHeadroom+(hasShiftDown()?1:4));else wallRadius=Math.min(org.villageastra.world.Walls.MAX_RADIUS,wallRadius+(hasShiftDown()?1:4));marksChanged();}),left+half+3,row2,half);
  place(button("level_down",Component.translatable("atlas.villageastra.level_down"),b->{if(level!=Integer.MIN_VALUE){level=Math.max(live.getInt("digFloor")-1,level-(hasShiftDown()?5:1));marksChanged();}}),left,row1,half);
  place(button("level_up",Component.translatable("atlas.villageastra.level_up"),b->{if(level!=Integer.MIN_VALUE){level+=hasShiftDown()?5:1;marksChanged();}}),left+half+3,row1,half);
  place(button("width",widthLabel(),b->{int width=(org.villageastra.server.MayorSurvey.width(roadVariant)%5)+1;roadVariant=(roadVariant&~(7<<6))|(width<<6);b.setMessage(widthLabel());marksChanged();}),left,TOP+78,full);
  place(button("archer",BuildingCard.text("post_archer"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.postArcher(live.getUUID("village"),live.getLong("epoch"),live.getLong("revision"),card);}),left,foot-44,full);
  place(button("species",BuildingCard.text("next_species"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.nextSpecies(live.getUUID("village"),live.getLong("epoch"),live.getLong("revision"),card);}),left,foot-66,full);
  place(button("saw",BuildingCard.text("saw_toggle"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.toggleSaw(live.getUUID("village"),live.getLong("epoch"),live.getLong("revision"),card);}),left,foot-44,full);
  place(button("cart",BuildingCard.text("leave_cart"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.leaveCart(live.getUUID("village"),live.getLong("epoch"),live.getLong("revision"),card);}),left,foot-44,full);
  place(button("crop",BuildingCard.text("next_crop"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.nextCrop(live.getUUID("village"),live.getLong("epoch"),live.getLong("revision"),card);}),left,foot-22,full);
  place(button("upgrade",BuildingCard.text("upgrade"),b->{var card=card();if(card!=null&&live.hasUUID("village"))BuildingCard.upgrade(live.getUUID("village"),live.getLong("epoch"),card);}),left,foot,full);
  // AD-125: the card's «Move» starts the move tool; while moving, the turn, the floor and «Cancel» take the tool rows.
  place(button("move",Component.translatable("atlas.villageastra.move"),b->startMove()),left,foot-22,full);
  place(button("move_turn",turnLabel(),b->turnMove()),left,row1,half);
  place(button("move_cancel",Component.translatable("atlas.villageastra.move.cancel"),b->cancelMove()),left+half+3,row1,half);
  // AD-123: the trail tool — the neighbour, the surface, lamps, the waypoints, and a crew pass over a finished trail.
  place(button("trail_target",trailTargetLabel(),full-10,b->{trailTarget++;b.setMessage(trailTargetLabel());WAYPOINTS.clear();}),left,row1,full);
  place(button("trail_surface",trailSurfaceLabel(),b->{trailSurface=1-trailSurface;b.setMessage(trailSurfaceLabel());}),left,row2,half);
  place(button("trail_light",toggleLabel("light",trailLight),b->{trailLight=!trailLight;b.setMessage(toggleLabel("light",trailLight));}),left+half+3,row2,half);
  place(button("trail_reset",Component.translatable("atlas.villageastra.trail.reset"),b->WAYPOINTS.clear()),left,row3,half);
  place(button("trail_repair",Component.translatable("atlas.villageastra.trail.repair"),b->{if(live.hasUUID("village")&&neighbour()!=null)ConstructionNetwork.sendTrail(new ConstructionNetwork.TrailOrder(live.getUUID("village"),live.getLong("epoch"),neighbour().getUUID("id"),org.villageastra.server.TrailOrders.REPAIR,trailSurface,trailLight,new long[0]));}),left+half+3,row3,half);
  place(button("capture",captureLabel(),b->{if(live.hasUUID("village")&&neighbour()!=null)ConstructionNetwork.sendWar(new ConstructionNetwork.WarOrder(live.getUUID("village"),live.getLong("epoch"),5,neighbour().getUUID("id")));}),left,TOP+78,full);
  var bottom=List.of(
   button("crowns",crownsLabel(TERRAIN.crowns),Math.max(font.width(crownsLabel(true)),font.width(crownsLabel(false))),b->{TERRAIN.crowns=!TERRAIN.crowns;viewChanged();b.setMessage(crownsLabel(TERRAIN.crowns));}),
   button("down",Component.literal("▼"),b->{TERRAIN.cut=(TERRAIN.cut==Integer.MAX_VALUE?TERRAIN.top():TERRAIN.cut)-(hasShiftDown()?8:1);viewChanged();}),
   button("up",Component.literal("▲"),b->{if(TERRAIN.cut!=Integer.MAX_VALUE)TERRAIN.cut+=hasShiftDown()?8:1;if(TERRAIN.cut>=TERRAIN.top())TERRAIN.cut=Integer.MAX_VALUE;viewChanged();}),
   button("chunk",chunkLabel(),Math.max(font.width(Component.translatable("atlas.villageastra.chunk_only")),font.width(Component.translatable("atlas.villageastra.chunk_all"))),
    b->{TERRAIN.isolate=TERRAIN.isolate==Long.MIN_VALUE&&chunk!=Long.MIN_VALUE?chunk:Long.MIN_VALUE;viewChanged();b.setMessage(chunkLabel());if(TERRAIN.isolate!=Long.MIN_VALUE)frameChunk();}),
   button("section",sectionLabel(),Math.max(font.width(Component.translatable("atlas.villageastra.section_x")),font.width(Component.translatable("atlas.villageastra.section_off"))),
    b->{TERRAIN.sideAxis=(TERRAIN.sideAxis+1)%3;if(TERRAIN.sideAxis!=0){var at=sectionAt();TERRAIN.side=TERRAIN.sideAxis==1?at.getX():at.getZ();}viewChanged();b.setMessage(sectionLabel());}),
   button("section_back",Component.literal("◂"),b->{if(TERRAIN.sideAxis!=0){TERRAIN.side-=hasShiftDown()?8:1;viewChanged();}}),
   button("section_on",Component.literal("▸"),b->{if(TERRAIN.sideAxis!=0){TERRAIN.side+=hasShiftDown()?8:1;viewChanged();}}),
   button("tilt",tiltLabel(),font.width(Component.translatable("atlas.villageastra.tilt",90)),b->{tilt=(tilt+1)%TILTS.length;b.setMessage(tiltLabel());}),
   button("rotate",Component.literal("⟲"),b->yaw+=90),
   button("out",Component.literal("−"),b->zoomAt(width/2F,mapMid(),1/1.25F)),
   button("in",Component.literal("+"),b->zoomAt(width/2F,mapMid(),1.25F)),
   button("resident",Component.translatable("atlas.villageastra.next_resident"),b->followNext()),
   button("info",Component.translatable("atlas.villageastra.info"),b->info=!info));
  x=4;for(var b:bottom){b.setPosition(x,height-BAR+2);x+=b.getWidth()+3;}
  refreshTools();
 }
 private static void place(Button b,int x,int y,int w){b.setPosition(x,y);b.setWidth(w);}
 private static Component widthLabel(){return Component.translatable("atlas.villageastra.width",org.villageastra.server.MayorSurvey.width(roadVariant));}
 private static Component afterLabel(){return Component.translatable(after?"atlas.villageastra.view_after":"atlas.villageastra.view_now");}
 /** The plan is asked again for the same ground at the new floor height. */
 private void replan(){if(tool==MOVE){if(moveGround!=null){moveTarget=snap(moveGround.offset(0,floorOffset,0));marksChanged();}return;}if(houseGround!=null)requestPlan(houseGround.offset(0,floorOffset,0));}
 /** AD-125: the picked building is taken up by the move tool at its own turn and floor; the first click on the map chooses its new place. */
 private void startMove(){var card=card();if(card==null||!commands())return;movingCard=card.copy();tool=MOVE;turns=card.getInt("rotation");floorOffset=0;moveGround=null;moveTarget=null;
  outcome=new CompoundTag();marksChanged();var t=buttons.get("move_turn");if(t!=null)t.setMessage(turnLabel());refreshTools();}
 private void turnMove(){turns=(turns+1)%4;var t=buttons.get("move_turn");if(t!=null)t.setMessage(turnLabel());if(moveTarget!=null)marksChanged();}
 private void cancelMove(){tool=VIEW;moveTarget=null;moveGround=null;marksChanged();if(movingCard!=null&&movingCard.hasUUID("id"))building=movingCard.getUUID("id");refreshTools();}
 /** Why the card's building cannot move now, as the atlas page says, or empty. */
 static String moveRefusal(CompoundTag card){var r=live.getCompound("relocation").getCompound("refusals");return card==null||!card.hasUUID("id")?"building":r.getString(card.getUUID("id").toString());}
 static BlockPos moveTarget(){return moveTarget;}
 static CompoundTag movingCard(){return movingCard;}
 static int floorOffset(){return floorOffset;}
 static boolean after(){return after;}
 static int futureQuads=0;
 private static Component routeLabel(){return Component.translatable("atlas.villageastra.route."+(roadVariant&3));}
 private static Component surfaceLabel(){return Component.translatable("atlas.villageastra.surface."+((roadVariant>>2)&3));}
 private static Component toggleLabel(String what,boolean on){return Component.translatable("atlas.villageastra."+what+(on?".on":".off"));}
 /** Only the buttons of the tool in use are shown, and the tab of that tool is pressed. */
 private void refreshTools(){
  // OWNER_REQUEST 9.2: the order tools belong to the mayor — an open map loses them the moment the office is lost.
  for(int i=0;i<TOOLS.length;i++){var tab=buttons.get("tool_"+TOOLS[i]);if(tab!=null)tab.active=i!=tool&&(i==VIEW||commands());}
  boolean shown=panelShown();
  var visible=new HashSet<String>();
  if(shown)switch(tool){
   case HOUSE->visible.addAll(List.of("design","floor_down","floor_up","after","turn","draft","order"));
   // A paved yard fills its whole rectangle: it has no width to choose and no fence along a centre line.
   case ROAD->{visible.addAll(List.of("route","surface","light","reset","order"));if((roadVariant&3)!=3)visible.addAll(List.of("fence","width"));}
   case DEMOLISH->visible.addAll(List.of("level_down","level_up","reset","order"));
   case WALL->visible.addAll(List.of("wall_shape","wall_smaller","wall_larger","order"));
   case TRAIL->{visible.addAll(List.of("trail_target","trail_surface","trail_light","trail_reset","trail_repair","order"));var b=buttons.get("trail_target");if(b!=null)b.setMessage(trailTargetLabel());
    // AD-159 VI: the neighbour picked for a trail can be taken by the army on its own, once the village has Military VI.
    if(live.getCompound("trails").getBoolean("capture")&&neighbour()!=null){visible.add("capture");var c=buttons.get("capture");if(c!=null)c.setMessage(captureLabel());}}
   case MOVE->visible.addAll(List.of("move_turn","move_cancel","floor_down","floor_up","order"));
   default->{var card=card();if(card!=null&&live.getBoolean("mayor")){if(BuildingCard.farm(card))visible.add("crop");if(BuildingCard.saw(card))visible.add("saw");if(BuildingCard.speciesChoice(card))visible.add("species");if(BuildingCard.archerPost(card))visible.add("archer");if(BuildingCard.cartTrip(card))visible.add("cart");
    if(BuildingCard.upgradable(card)){visible.add("upgrade");var b=buttons.get("upgrade");if(b!=null)b.setMessage(BuildingCard.upgradeLabel(card));}
    // AD-125: «Move» takes the lowest free row of the card's buttons; greyed out with the reason while the building cannot move.
    var move=buttons.get("move");if(move!=null){visible.add("move");var why=moveRefusal(card);move.active=why.isEmpty();
     move.setTooltip(why.isEmpty()?null:net.minecraft.client.gui.components.Tooltip.create(Component.translatable("relocate.villageastra.refused."+why)));
     int foot=height-BAR-24,row=visible.contains("upgrade")?1:0;if(visible.contains("crop"))row=2;if(visible.contains("archer")||visible.contains("saw")||visible.contains("cart"))row=Math.max(row,3);if(visible.contains("species"))row=4;
     move.setY(foot-22*row);}}}
  }
  for(var name:List.of("design","floor_down","floor_up","after","turn","draft","order","reset","route","surface","light","fence","width","level_down","level_up","wall_shape","wall_smaller","wall_larger","crop","saw","species","archer","cart","upgrade","trail_target","trail_surface","trail_light","trail_reset","trail_repair","capture","move","move_turn","move_cancel")){var b=buttons.get(name);if(b!=null)b.visible=visible.contains(name);}
  var order=buttons.get("order");if(order!=null)place(order,width-PANEL_W+2+(tool==MOVE?0:(PANEL_W-15)/2+3),order.getY(),tool==MOVE?PANEL_W-12:(PANEL_W-15)/2);
  if(order!=null){order.active=tool!=MOVE||preview.getBoolean("ok");order.setMessage(Component.translatable(tool==MOVE?"atlas.villageastra.move.order":"atlas.villageastra.order"));}
 }
 @Override public void tick(){if(++refresh%10==0)request();
  if(tool!=VIEW&&live.hasUUID("village")&&!commands()){tool=VIEW;cornerA=null;cornerB=null;marksChanged();plan=new CompoundTag();outcome=new CompoundTag();refreshTools();}
  else if(refresh%10==0)refreshTools();
  ask();}
 /** Whether this player may give orders from the map right now: the live page says so, refreshed twice a second. */
 static boolean commands(){return live.getBoolean("mayor");}
 private void viewChanged(){TERRAIN.viewChanged();dirty=true;built=0;}
 /** Probe hook: sets the side section exactly as the buttons do. */
 void section(int axis){TERRAIN.sideAxis=axis;if(axis!=0){var at=sectionAt();TERRAIN.side=axis==1?at.getX():at.getZ();}viewChanged();}
 private float mapMid(){return (TOP+height-BAR)/2F;}
 private BlockPos center(){return live.contains("center")?BlockPos.of(live.getLong("center")):BlockPos.ZERO;}
 private Matrix4f scene(){
  var m=new Matrix4f();m.translate(width/2F+panX,mapMid()+panY,LAYER);m.scale(zoom,-zoom,DEPTH);
  m.rotate(Axis.XP.rotationDegrees(TILTS[tilt]));m.rotate(Axis.YP.rotationDegrees(yaw));m.translate(-focusX,-focusY,-focusZ);return m;}
 /** The first look is centred on the settlement itself at ground level; after that the player's own view is kept. */
 private void frame(){
  if(framed||TERRAIN.keys().isEmpty())return;framed=true;var o=center();
  Integer h=TERRAIN.heightAt(o.getX(),o.getZ());focusX=.5F;focusZ=.5F;focusY=h==null?0:h+1-o.getY();
 }
 /** A followed resident keeps the view on itself; the camera glides after it instead of jumping. */
 private void follow(long now){
  if(followed==null)return;var motion=MOTION.get(followed);if(motion==null)return;var o=center();var at=position(motion,now);
  focusX+=((float)(at[0]-o.getX())-focusX)*.3F;focusY+=((float)(at[1]-o.getY())-focusY)*.3F;focusZ+=((float)(at[2]-o.getZ())-focusZ)*.3F;
 }
 /** One chunk on its own is brought to the middle of the map and sized to fill it, so its sides and every stored layer can be read. */
 private void frameChunk(){
  var cp=new ChunkPos(TERRAIN.isolate);var o=center();var column=TERRAIN.column(cp.getMinBlockX()+8,cp.getMinBlockZ()+8);
  followed=null;panX=0;panY=0;focusX=cp.getMinBlockX()+8-o.getX();focusZ=cp.getMinBlockZ()+8-o.getZ();
  focusY=column==null?focusY:(column.top+column.bottom())/2F-o.getY();
  zoom=Mth.clamp((height-TOP-BAR)*.55F/Math.max(16,column==null?24:column.states.length),.5F,64F);
 }
 private void followNext(){
  var list=people();if(list.isEmpty()){followed=null;return;}
  int at=-1;for(int i=0;i<list.size();i++)if(list.getCompound(i).getUUID("id").equals(followed))at=i;
  followed=list.getCompound((at+1)%list.size()).getUUID("id");panX=0;panY=0;
 }
 private void zoomAt(float x,float y,float factor){
  float before=zoom;zoom=Mth.clamp(zoom*factor,.5F,64F);
  float cx=width/2F+panX,cy=mapMid()+panY;
  cx=x-(x-cx)*zoom/before;cy=y-(y-cy)*zoom/before;panX=cx-width/2F;panY=cy-mapMid();
 }
 /** Uploads every visible face of the remembered blocks once per data or view change. */
 private void rebuild(){
  dirty=false;built=System.currentTimeMillis();if(terrain!=null){terrain.close();terrain=null;}
  if(TERRAIN.keys().isEmpty())return;
  var b=BUILDER;b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
  TERRAIN.mesh(b,center(),CHUNKS.size()<=GRID_LIMIT);
  var rendered=b.end();
  if(rendered.isEmpty()){rendered.release();return;}
  terrain=new VertexBuffer(VertexBuffer.Usage.STATIC);terrain.bind();terrain.upload(rendered);VertexBuffer.unbind();
 }
 /** The block under the cursor: a ray from the viewer through the remembered columns. */
 private BlockPos pickAt(Matrix4f m,double mx,double my){
  if(TERRAIN.keys().isEmpty())return null;
  var inverse=new Matrix4f(m).invert();
  var near=inverse.transformPosition(new Vector3f((float)mx,(float)my,LAYER+4900));var far=inverse.transformPosition(new Vector3f((float)mx,(float)my,LAYER-4900));
  return TERRAIN.pick(new float[]{near.x,near.y,near.z},new float[]{far.x,far.y,far.z},center());
 }
 /** Colour of a resident's frame by trade: builders amber, fighters red, the land green, the learned blue, the roads gold; children pale. */
 private static int personColour(CompoundTag person){
  if(person.getBoolean("child"))return 0xBFE3C6;
  return switch(person.getString("profession")){
   case "builder","carpenter","mason","engineer" -> 0xE0A85A;
   case "soldier","guard","archer_guard" -> 0xE0654A;
   case "farmer","livestock_farmer","forester","miner" -> 0x63D68A;
   case "scientist","teacher","doctor","cartographer" -> 0x6FB7E0;
   case "caravaneer","expeditioner","porter" -> 0xD7C566;
   case "mayor" -> 0xC98BE8;
   default -> 0x4FD06A;};
 }
 private static void box(BufferBuilder b,float x,float y,float z,float grow,int rgb,int alpha){
  float a=-grow,c=1+grow;int r=(rgb>>16)&255,g=(rgb>>8)&255,bl=rgb&255;
  float[][] faces={{a,c,a, a,c,c, c,c,c, c,c,a},{a,a,a, c,a,a, c,a,c, a,a,c},{a,a,a, a,c,a, c,c,a, c,a,a},{a,a,c, c,a,c, c,c,c, a,c,c},{a,a,a, a,a,c, a,c,c, a,c,a},{c,a,a, c,c,a, c,c,c, c,a,c}};
  for(var f:faces)for(int i=0;i<4;i++)b.vertex(x+f[i*3],y+f[i*3+1],z+f[i*3+2]).color(r,g,bl,alpha).endVertex();
 }
 private static void flat(BufferBuilder b,float x,float y,float z,float x2,float z2,int rgb,int alpha){
  int r=(rgb>>16)&255,g=(rgb>>8)&255,bl=rgb&255;
  b.vertex(x,y,z).color(r,g,bl,alpha).endVertex();b.vertex(x,y,z2).color(r,g,bl,alpha).endVertex();b.vertex(x2,y,z2).color(r,g,bl,alpha).endVertex();b.vertex(x2,y,z).color(r,g,bl,alpha).endVertex();
 }
 /** Translucent world overlays: dark roads, the examined chunk, the plan's blue future volume and red demolition, the picked and hovered blocks. */
 private void overlays(Matrix4f m){
  var stack=RenderSystem.getModelViewStack();stack.pushPose();stack.mulPoseMatrix(m);RenderSystem.applyModelViewMatrix();
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.depthMask(false);RenderSystem.setShader(GameRenderer::getPositionColorShader);
  var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var o=center();
  {var roads=live.getLongArray("roads");var meta=live.getIntArray("roadMeta");
   for(int i=0;i<Math.min(roads.length,meta.length);i++){if((meta[i]>>12&1)==1)continue;var p=BlockPos.of(roads[i]);
    flat(b,p.getX()-o.getX()+.1F,p.getY()+1.03F-o.getY(),p.getZ()-o.getZ()+.1F,p.getX()-o.getX()+.9F,p.getZ()-o.getZ()+.9F,0xE04A3A,120);}}
  if(chunk!=Long.MIN_VALUE){var cp=new ChunkPos(chunk);
   // The examined chunk is outlined along its four edges, on the surface the view shows.
   for(int i=0;i<16;i++)for(int[] edge:new int[][]{{i,0,0},{i,15,1},{0,i,2},{15,i,3}}){
    int wx=cp.getMinBlockX()+edge[0],wz=cp.getMinBlockZ()+edge[1];Integer h=TERRAIN.heightAt(wx,wz);if(h==null)continue;
    float x=wx-o.getX(),z=wz-o.getZ(),y=h+1.04F-o.getY();
    switch(edge[2]){case 0->flat(b,x,y,z,x+1,z+.18F,0xE9CD92,200);case 1->flat(b,x,y,z+.82F,x+1,z+1,0xE9CD92,200);
     case 2->flat(b,x,y,z,x+.18F,z+1,0xE9CD92,200);default->flat(b,x+.82F,y,z,x+1,z+1,0xE9CD92,200);}}}
  if(tool==HOUSE){for(var draft:DRAFTS)ghost(b,draft,true);if(!plan.isEmpty())ghost(b,plan,false);}
  if(tool==ROAD)roadMarks(b);
  if(tool==DEMOLISH)clearingMarks(b);
  if(tool==WALL)wallMarks(b);
  if(tool==VIEW)buildingMarks(b);
  if(tool==MOVE)moveMarks(b);
  if(selected!=null)box(b,selected.getX()-o.getX(),selected.getY()-o.getY(),selected.getZ()-o.getZ(),.03F,0xFFFFFF,90);
  if(hover!=null&&!hover.equals(selected))box(b,hover.getX()-o.getX(),hover.getY()-o.getY(),hover.getZ()-o.getZ(),.02F,0xFFFFFF,45);
  BufferUploader.drawWithShader(b.end());
  RenderSystem.depthMask(true);stack.popPose();RenderSystem.applyModelViewMatrix();
 }
 private void cell(BufferBuilder b,BlockPos p,float grow,int rgb,int alpha){var o=center();box(b,p.getX()-o.getX(),p.getY()-o.getY(),p.getZ()-o.getZ(),grow,rgb,alpha);}
 /** The two ends of a road, its cells from the server's dry run (or the straight line until it answers) and the cells in its way. */
 private void roadMarks(BufferBuilder b){
  if(cornerA!=null)cell(b,cornerA,.06F,0xFFD75A,150);if(cornerB!=null)cell(b,cornerB,.06F,0xFFD75A,150);
  if(cornerA==null||cornerB==null)return;var o=center();
  var cells=preview.getLongArray("roadCells");
  if(cells.length==0)for(var at:org.villageastra.server.MayorSurvey.line(cornerA,cornerB,(roadVariant&3)==3?0:roadVariant&3)){Integer h=TERRAIN.heightAt(at.getX(),at.getZ());if(h!=null)flat(b,at.getX()-o.getX(),h+1.03F-o.getY(),at.getZ()-o.getZ(),at.getX()-o.getX()+1,at.getZ()-o.getZ()+1,0xD7B46A,110);}
  for(long raw:cells){var p=BlockPos.of(raw);flat(b,p.getX()-o.getX(),p.getY()+1.03F-o.getY(),p.getZ()-o.getZ(),p.getX()-o.getX()+1,p.getZ()-o.getZ()+1,0xD7B46A,130);}
  for(long raw:preview.getLongArray("blocked"))cell(b,BlockPos.of(raw),.04F,0xE04A3A,120);
 }
 /** AD-058: every building is outlined along its border on the ground; the picked one stands out and is filled. */
 private void buildingMarks(BufferBuilder b){
  var o=center();
  for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var at=BlockPos.of(t.getLong("pos"));int w=t.getInt("w"),d=t.getInt("d");
   boolean picked=t.hasUUID("id")&&t.getUUID("id").equals(building);int rgb=picked?0xFFD75A:0x6FD3E0,alpha=picked?220:150;float edge=picked?.3F:.18F;
   for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){
    boolean border=dx==0||dz==0||dx==w-1||dz==d-1;if(!border&&!picked)continue;
    int wx=at.getX()+dx,wz=at.getZ()+dz;Integer h=TERRAIN.heightAt(wx,wz);if(h==null)continue;
    float x=wx-o.getX(),z=wz-o.getZ(),y=h+1.05F-o.getY();
    if(!border){flat(b,x,y,z,x+1,z+1,rgb,40);continue;}
    if(dz==0)flat(b,x,y,z,x+1,z+edge,rgb,alpha);if(dz==d-1)flat(b,x,y,z+1-edge,x+1,z+1,rgb,alpha);
    if(dx==0)flat(b,x,y,z,x+edge,z+1,rgb,alpha);if(dx==w-1)flat(b,x+1-edge,y,z,x+1,z+1,rgb,alpha);}
  }
  // AD-125: the new place of a building being moved stays drawn, pale, until the move is done.
  var active=live.getCompound("relocation").getCompound("active");
  if(active.contains("origin")&&!active.getBoolean("moved")){var at=BlockPos.of(active.getLong("origin"));
   for(int dx=0;dx<active.getInt("width");dx++)for(int dz=0;dz<active.getInt("depth");dz++){Integer h=TERRAIN.heightAt(at.getX()+dx,at.getZ()+dz);if(h==null)continue;
    float x=at.getX()+dx-o.getX(),z=at.getZ()+dz-o.getZ();flat(b,x,h+1.04F-o.getY(),z,x+1,z+1,0x3F8CFF,55);}}
 }
 /** AD-125: the move as one picture — the old building in translucent orange (what is taken apart), the new place as a planned house
  *  (blue future volume, amber replacements, red conflicts), and a dotted line of flat cells from the old middle to the new one. */
 private void moveMarks(BufferBuilder b){
  var o=center();
  if(movingCard!=null&&preview.getLongArray("oldCells").length==0){var at=BlockPos.of(movingCard.getLong("pos"));int w=movingCard.getInt("w"),d=movingCard.getInt("d");
   for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){Integer h=TERRAIN.heightAt(at.getX()+dx,at.getZ()+dz);if(h==null)continue;float x=at.getX()+dx-o.getX(),z=at.getZ()+dz-o.getZ();flat(b,x,h+1.02F-o.getY(),z,x+1,z+1,0xE08A3A,70);}}
  for(long raw:preview.getLongArray("oldCells")){var p=BlockPos.of(raw);box(b,p.getX()-o.getX(),p.getY()-o.getY(),p.getZ()-o.getZ(),.04F,0xE08A3A,80);}
  for(long raw:preview.getLongArray("oldConflicts")){var p=BlockPos.of(raw);box(b,p.getX()-o.getX(),p.getY()-o.getY(),p.getZ()-o.getZ(),.05F,0xE04A3A,130);}
  if(moveTarget==null)return;
  if(preview.contains("origin"))ghost(b,preview,false);
  else cell(b,moveTarget,.06F,0xFFD75A,150);
  if(movingCard==null)return;var from=BlockPos.of(movingCard.getLong("pos"));
  int w=preview.contains("width")?preview.getInt("width"):movingCard.getInt("w"),d=preview.contains("depth")?preview.getInt("depth"):movingCard.getInt("d");
  float ax=from.getX()+movingCard.getInt("w")/2F,az=from.getZ()+movingCard.getInt("d")/2F,bx=moveTarget.getX()+w/2F,bz=moveTarget.getZ()+d/2F;
  int steps=(int)Math.max(Math.abs(bx-ax),Math.abs(bz-az));
  for(int i=0;i<=steps;i+=2){float x=ax+(bx-ax)*i/Math.max(1,steps),z=az+(bz-az)*i/Math.max(1,steps);Integer h=TERRAIN.heightAt(Mth.floor(x),Mth.floor(z));if(h==null)continue;
   flat(b,x-.3F-o.getX(),h+1.06F-o.getY(),z-.3F-o.getZ(),x+.3F-o.getX(),z+.3F-o.getZ(),0xB8A26A,170);}
 }
 /** The marked land: its edge on the ground, the level that stays as a plane, builder blocks in red and the miners' stone and ore in violet. */
 /** AD-094: the ring of the wall over the ground as the map knows it — its gateways yellow, the tower sites blue. */
 private void wallMarks(BufferBuilder b){
  var o=center();var gates=preview.getLongArray("gateCells");
  // AD-127: the ring as the server planned it when it sent one (the fitted wall has no other); the old ring's stretches to come down red,
  // the columns whose water, cliff or building pushed the ring out orange.
  var ring=new ArrayList<BlockPos>();
  if(preview.contains("ringCells"))for(long raw:preview.getLongArray("ringCells"))ring.add(BlockPos.of(raw));
  else if(wallShape!=2)ring.addAll(org.villageastra.world.Walls.ring(o,wallShape==1?org.villageastra.world.Walls.Shape.ROUND:org.villageastra.world.Walls.Shape.SQUARE,wallRadius));
  for(long raw:preview.getLongArray("retireCells"))cell(b,BlockPos.of(raw),.02F,0xE04848,90);
  for(long raw:preview.getLongArray("pushedCells")){var p=BlockPos.of(raw);Integer h=TERRAIN.heightAt(p.getX(),p.getZ());if(h!=null)box(b,p.getX()-o.getX(),h+1-o.getY(),p.getZ()-o.getZ(),.04F,0xFF9A3C,130);}
  for(var column:ring){
   Integer h=TERRAIN.heightAt(column.getX(),column.getZ());if(h==null)continue;boolean gate=false;
   for(long g:gates){var at=BlockPos.of(g);if(Math.abs(at.getX()-column.getX())<=org.villageastra.world.Walls.GATE/2&&Math.abs(at.getZ()-column.getZ())<=org.villageastra.world.Walls.GATE/2)gate=true;}
   for(int y=1;y<=(gate?1:org.villageastra.world.Walls.HEIGHT);y++)box(b,column.getX()-o.getX(),h+y-o.getY(),column.getZ()-o.getZ(),-.03F,gate?0xFFD75A:0x9AA3A8,gate?140:90);
  }
  for(long raw:preview.getLongArray("towerCells")){var t=BlockPos.of(raw);Integer h=TERRAIN.heightAt(t.getX(),t.getZ());if(h==null)continue;
   flat(b,t.getX()-2-o.getX(),h+1.05F-o.getY(),t.getZ()-2-o.getZ(),t.getX()+3-o.getX(),t.getZ()+3-o.getZ(),0x3F8CFF,110);}
 }
 private void clearingMarks(BufferBuilder b){
  if(cornerA!=null)cell(b,cornerA,.06F,0xFFD75A,150);if(cornerB!=null)cell(b,cornerB,.06F,0xFFD75A,150);
  if(cornerA==null||cornerB==null)return;var o=center();
  int x0=Math.min(cornerA.getX(),cornerB.getX()),x1=Math.max(cornerA.getX(),cornerB.getX()),z0=Math.min(cornerA.getZ(),cornerB.getZ()),z1=Math.max(cornerA.getZ(),cornerB.getZ());
  flat(b,x0-o.getX(),level+1.02F-o.getY(),z0-o.getZ(),x1+1-o.getX(),z1+1-o.getZ(),0xFFD75A,60);
  for(long raw:preview.getLongArray("builderCells"))cell(b,BlockPos.of(raw),.02F,0xE0624A,70);
  for(long raw:preview.getLongArray("minerCells"))cell(b,BlockPos.of(raw),.02F,0x9B59D0,90);
 }
 /** A07-VIS-001 on the map: the footprint, the protection buffer, the future volume block by block in blue and what is torn down in red. */
 private void ghost(BufferBuilder b,CompoundTag t,boolean draft){
  if(!t.contains("origin"))return;var origin=BlockPos.of(t.getLong("origin"));var o=center();int w=t.getInt("width"),d=t.getInt("depth");
  int ground=draft?0x4A6E8A:t.getBoolean("ok")?0x63D68A:0xD6A063;
  for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){Integer h=TERRAIN.heightAt(origin.getX()+dx,origin.getZ()+dz);if(h==null)continue;
   float x=origin.getX()+dx-o.getX(),z=origin.getZ()+dz-o.getZ();flat(b,x,h+1.02F-o.getY(),z,x+1,z+1,ground,draft?60:95);}
  if(draft)return;
  for(int dx=-3;dx<w+3;dx++)for(int dz=-3;dz<d+3;dz++){
   if(dx>=0&&dz>=0&&dx<w&&dz<d)continue;if(dx!=-3&&dz!=-3&&dx!=w+2&&dz!=d+2)continue;if(((dx+dz)&1)==0)continue;
   Integer h=TERRAIN.heightAt(origin.getX()+dx,origin.getZ()+dz);if(h==null)continue;
   float x=origin.getX()+dx-o.getX(),z=origin.getZ()+dz-o.getZ();flat(b,x+.15F,h+1.02F-o.getY(),z+.15F,x+.85F,z+.85F,0xB8A26A,150);}
  if(!after){
   var future=t.getIntArray("future");
   for(int i=0;i+3<future.length;i+=4)box(b,origin.getX()+future[i]-o.getX(),origin.getY()+future[i+1]-o.getY(),origin.getZ()+future[i+2]-o.getZ(),-.04F,0x3F8CFF,70);
  }
  // ISO-003: a block that takes the place of another is neither a new block nor a demolition — it is marked in its own amber.
  if(!after)for(long raw:t.getLongArray("replaceCells")){var p=BlockPos.of(raw);box(b,p.getX()-o.getX(),p.getY()-o.getY(),p.getZ()-o.getZ(),-.02F,0xE0A83A,110);}
  for(long raw:t.getLongArray("demolish")){var p=BlockPos.of(raw);box(b,p.getX()-o.getX(),p.getY()-o.getY(),p.getZ()-o.getZ(),.03F,0xE04A3A,after?50:90);}
  for(long raw:t.getLongArray("conflicts")){var p=BlockPos.of(raw);Integer h=TERRAIN.heightAt(p.getX(),p.getZ());if(h!=null)box(b,p.getX()-o.getX(),h-o.getY(),p.getZ()-o.getZ(),.05F,0xE04A3A,130);}
 }
 private boolean onMap(float x,float y,int margin){return x>=-margin&&x<=width+margin&&y>=TOP-margin&&y<=height-BAR+margin;}
 private void tag(GuiGraphics g,Component text,int cx,int y,int colour){
  int w=font.width(text);g.fill(cx-w/2-2,y-1,cx+w/2+2,y+9,0xB00E141A);g.drawString(font,text,cx-w/2,y,colour,false);
 }
 /** Names of the buildings stand over them once the map is close enough to read them. */
 private void labels(GuiGraphics g,Matrix4f m){
  if(zoom<4)return;var o=center();
  for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var p=BlockPos.of(t.getLong("pos"));
   int cx=p.getX()+t.getInt("w")/2,cz=p.getZ()+t.getInt("d")/2;Integer h=TERRAIN.heightAt(cx,cz);if(h==null)continue;
   var v=m.transformPosition(new Vector3f(cx-o.getX()+.5F,h+2-o.getY(),cz-o.getZ()+.5F));
   if(!onMap(v.x,v.y,-8))continue;
   tag(g,Component.translatable("building.villageastra."+t.getString("type")),(int)v.x,(int)v.y,0xFFE9CD92);}
 }
 /** AD-056: every resident is their own face on the map, framed in the colour of their trade, pinned to where they really stand. */
 private void residents(GuiGraphics g,Matrix4f m,int mx,int my,long now){
  icons.clear();var o=center();CompoundTag hovered=null;
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
  int size=(int)Mth.clamp(zoom*1.1F,10,20);
  var list=new ArrayList<CompoundTag>();for(var raw:people())list.add((CompoundTag)raw);
  list.sort(Comparator.comparing(p->p.getUUID("id").equals(followed)));
  for(var p:list){var id=p.getUUID("id");var motion=MOTION.get(id);var block=BlockPos.of(p.getLong("pos"));
   double[] at=motion==null?new double[]{block.getX()+.5,block.getY(),block.getZ()+.5}:position(motion,now);
   var v=m.transformPosition(new Vector3f((float)(at[0]-o.getX()),(float)(at[1]-o.getY()),(float)(at[2]-o.getZ())));
   if(!onMap(v.x,v.y,size))continue;
   boolean track=id.equals(followed);int s=p.getBoolean("child")?size*3/4:size;
   int left=Math.round(v.x)-s/2,top=Math.round(v.y)-s-6;int ring=track?0xFFFFE07A:0xFF000000|personColour(p);
   icons.put(id,new float[]{left+s/2F,top+s/2F,s});
   g.fill(left-2,top-2,left+s+2,top+s+2,0xFF0B1014);g.fill(left-1,top-1,left+s+1,top+s+1,ring);
   var skin=SKINS.get(Math.floorMod(p.getInt("skin"),SKINS.size()));
   g.blit(skin,left,top,s,s,8,8,8,8,64,64);g.blit(skin,left,top,s,s,40,8,8,8,64,64);
   int cx=Math.round(v.x);g.fill(cx-2,top+s+1,cx+2,top+s+3,ring);g.fill(cx-1,top+s+3,cx+1,top+s+5,ring);
   if(track||zoom>=14)tag(g,Component.literal(p.getString("name")),cx,top-12,track?0xFFFFE07A:0xFFFFFFFF);
   if(mx>=left-2&&mx<=left+s+2&&my>=top-2&&my<=top+s+5)hovered=p;
  }
  for(var raw:live.getList("enemies",Tag.TAG_LONG)){var p=BlockPos.of(((LongTag)raw).getAsLong());
   var v=m.transformPosition(new Vector3f(p.getX()-o.getX()+.5F,p.getY()-o.getY(),p.getZ()-o.getZ()+.5F));if(!onMap(v.x,v.y,8))continue;
   int cx=Math.round(v.x),cy=Math.round(v.y)-8;g.fill(cx-5,cy-5,cx+5,cy+5,0xFF0B1014);g.fill(cx-4,cy-4,cx+4,cy+4,0xFFE04A3A);g.drawString(font,"!",cx-1,cy-4,0xFFFFFFFF,false);}
  if(hovered!=null)card(g,hovered,mx,my);
 }
 /** The card of a resident under the cursor: who they are, their trade and what they are doing right now. */
 private void card(GuiGraphics g,CompoundTag p,int mx,int my){
  var role=p.getString("profession").isEmpty()?Component.translatable("trade.villageastra.no_profession"):Component.translatable("profession.villageastra."+p.getString("profession"));
  var work=Component.translatable("work.villageastra."+(p.getString("status").isEmpty()?"idle":p.getString("status")));
  var at=BlockPos.of(p.getLong("pos"));
  var lines=List.of(Component.literal(p.getString("name")),Component.translatable("atlas.villageastra.person.role",role,work),
   Component.translatable("atlas.villageastra.person.where",at.getX(),at.getY(),at.getZ()),
   Component.translatable(p.getUUID("id").equals(followed)?"atlas.villageastra.person.unfollow":"atlas.villageastra.person.follow"));
  int w=lines.stream().mapToInt(font::width).max().orElse(40)+10,h=lines.size()*10+6;
  int x=Math.min(mx+12,width-w-4),y=Math.max(TOP+2,Math.min(my-h-4,height-BAR-h-2));
  g.fill(x,y,x+w,y+h,0xE80E141A);g.fill(x,y,x+2,y+h,0xFF000000|personColour(p));
  for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),x+6,y+4+i*10,i==0?0xFFFFE9B0:i==3?0xFF8FA2A8:0xFFBFD1C8,false);
 }
 private boolean panelShown(){return info&&(tool!=VIEW||selected!=null||building!=null);}
 private boolean overPanel(double mx,double my){return panelShown()&&mx>=width-PANEL_W-4&&my>=TOP&&my<height-BAR;}
 private int wrap(GuiGraphics g,Component text,int x,int y,int colour){for(var part:font.split(text,PANEL_W-12)){g.drawString(font,part,x,y,colour,false);y+=10;}return y;}
 /** AD-057: the right panel belongs to the tool in use — what a click does, what the order would take and why it cannot be given. */
 private void sidePanel(GuiGraphics g,int mx,int my){
  if(!panelShown())return;
  int left=width-PANEL_W-4,x=left+6,bottom=height-BAR-4;
  g.fill(left,TOP+2,width-4,bottom,0xD80E141A);
  line(g,tool==MOVE?Component.translatable("atlas.villageastra.tool_title.move",Component.translatable("building.villageastra."+(movingCard==null?"home":movingCard.getString("type")))):Component.translatable("atlas.villageastra.tool_title."+TOOLS[tool]),x,TOP+6,0xFFE9CD92);
  int y=switch(tool){case HOUSE->TOP+82;case DEMOLISH->TOP+42;case ROAD->TOP+102;case WALL->TOP+62;case TRAIL->TOP+82;case MOVE->TOP+62;default->TOP+20;};
  int grey=0xFFBFD1C8,warn=0xFFDD9C66,good=0xFF9FD39A,bad=0xFFE04A3A;
  switch(tool){
   case MOVE->movePanel(g,x,y,bottom-26);
   case HOUSE->{if(plan.isEmpty())y=wrap(g,Component.translatable("atlas.villageastra.hint.house"),x,y,grey);else estimate(g,x,y);}
   case ROAD->{
    if(cornerA==null)y=wrap(g,Component.translatable("atlas.villageastra.hint.road"),x,y,grey);
    else if(cornerB==null)y=wrap(g,Component.translatable("atlas.villageastra.road.first",cornerA.getX(),cornerA.getZ()),x,y,grey);
    else{
     int length=Math.max(Math.abs(cornerA.getX()-cornerB.getX()),Math.abs(cornerA.getZ()-cornerB.getZ()))+1;
     y=wrap(g,Component.translatable("atlas.villageastra.road.size",length,preview.getInt("cells")),x,y,grey);
     if(preview.isEmpty())y=wrap(g,Component.translatable("atlas.villageastra.checking"),x,y,0xFF8FA2A8);
     else if(!preview.getString("reason").isEmpty())y=wrap(g,Component.translatable("maporder.villageastra.refused."+preview.getString("reason"),preview.getLongArray("blocked").length),x,y,bad);
     else y=wrap(g,Component.translatable("atlas.villageastra.road.items",preview.getInt("operations"),preview.getInt("items")),x,y,good);
     if(preview.getBoolean("busyBuilders"))y=wrap(g,Component.translatable("maporder.villageastra.refused.busy_builders"),x,y,warn);}
   }
   case TRAIL->{
    var trails=live.getCompound("trails");
    if(!trails.getBoolean("research"))y=wrap(g,Component.translatable("atlas.villageastra.trail.research"),x,y,warn);
    else if(neighbour()==null)y=wrap(g,Component.translatable("atlas.villageastra.trail.no_neighbour"),x,y,warn);
    else{y=wrap(g,Component.translatable("atlas.villageastra.trail.hint",org.villageastra.world.Trails.MAX_WAYPOINTS),x,y,grey);
     y=wrap(g,Component.translatable("atlas.villageastra.trail.waypoints",WAYPOINTS.size(),org.villageastra.world.Trails.MAX_WAYPOINTS),x,y,grey);
     if(neighbour().getString("standing").equals("hostile"))y=wrap(g,Component.translatable("trail.villageastra.refused.hostile"),x,y,bad);
     if(!trails.getBoolean("bridges"))y=wrap(g,Component.translatable("atlas.villageastra.trail.no_bridges"),x,y,0xFF8FA2A8);}
    var st=trailState();
    if(!st.isEmpty()){y+=4;y=wrap(g,Component.translatable("atlas.villageastra.trail.state."+st.getString("state"),st.getInt("done"),st.getInt("length"),st.getInt("laid"),st.getInt("skipped")),x,y,st.getString("state").equals("blocked")?bad:good);
     if(!st.getString("reason").isEmpty())y=wrap(g,Component.translatable("trail.villageastra.refused."+st.getString("reason")),x,y,warn);}
   }
   case WALL->{
    y=wrap(g,wallShape==2?Component.translatable("atlas.villageastra.wall.headroom",wallShapeLabel(),wallHeadroom):Component.translatable("atlas.villageastra.wall.size",wallShapeLabel(),wallRadius),x,y,grey);
    if(preview.isEmpty())y=wrap(g,Component.translatable("atlas.villageastra.checking"),x,y,0xFF8FA2A8);
    else if(!preview.getString("reason").isEmpty())y=wrap(g,Component.translatable("maporder.villageastra.refused."+org.villageastra.world.Walls.reasonKey(preview.getString("reason")),org.villageastra.world.Walls.reasonArgs(preview.getString("reason"))),x,y,bad);
    else{y=wrap(g,Component.translatable("atlas.villageastra.wall.parts",preview.getInt("cells"),preview.getInt("gates"),preview.getInt("towers")),x,y,good);
     if(preview.contains("perimeter"))y=wrap(g,Component.translatable("atlas.villageastra.wall.perimeter",preview.getInt("perimeter"),preview.getInt("rmin"),preview.getInt("rmax")),x,y,grey);
     if(preview.getBoolean("outgrown"))y=wrap(g,Component.translatable("atlas.villageastra.wall.outgrown",preview.getInt("retire")),x,y,warn);
     if(!preview.getBoolean("towerResearch"))y=wrap(g,Component.translatable("atlas.villageastra.wall.no_towers"),x,y,warn);}
    if(preview.getBoolean("busyBuilders"))y=wrap(g,Component.translatable("maporder.villageastra.refused.busy_builders"),x,y,warn);
   }
   case DEMOLISH->{
    if(cornerA==null)y=wrap(g,Component.translatable("atlas.villageastra.hint.demolish"),x,y,grey);
    else if(cornerB==null)y=wrap(g,Component.translatable("atlas.villageastra.demolish.first",cornerA.getX(),cornerA.getZ()),x,y,grey);
    else{
     y=wrap(g,Component.translatable("atlas.villageastra.demolish.size",Math.abs(cornerA.getX()-cornerB.getX())+1,Math.abs(cornerA.getZ()-cornerB.getZ())+1,level),x,y,grey);
     if(preview.isEmpty())y=wrap(g,Component.translatable("atlas.villageastra.checking"),x,y,0xFF8FA2A8);
     else if(!preview.getString("reason").isEmpty())y=wrap(g,Component.translatable("maporder.villageastra.refused."+preview.getString("reason")),x,y,bad);
     else{
      y=wrap(g,Component.translatable("atlas.villageastra.demolish.builders",preview.getInt("builders")),x,y,0xFFE58C7A);
      y=wrap(g,Component.translatable("atlas.villageastra.demolish.miners",preview.getInt("miners")),x,y,0xFFC39BEA);
      if(preview.getInt("kept")>0)y=wrap(g,Component.translatable("atlas.villageastra.demolish.kept",preview.getInt("kept")),x,y,0xFF8FA2A8);
      if(preview.getInt("miners")>0&&!preview.getBoolean("miner"))y=wrap(g,Component.translatable("atlas.villageastra.demolish.no_miner"),x,y,warn);
      if(preview.getBoolean("busyBuilders")&&preview.getInt("builders")>0)y=wrap(g,Component.translatable("maporder.villageastra.refused.busy_builders"),x,y,warn);
      if(preview.getBoolean("busyMiners")&&preview.getInt("miners")>0)y=wrap(g,Component.translatable("maporder.villageastra.refused.busy_miners"),x,y,warn);}
     // The protection is spelled out: how deep the settlement lets anybody dig and why.
     y=wrap(g,Component.translatable("atlas.villageastra.demolish.floor",live.getInt("digFloor"),live.getInt("lowest")),x,y+2,level<live.getInt("digFloor")?warn:0xFF8FA2A8);}
   }
   default->{
    var card=card();
    if(card!=null){int end=BuildingCard.render(g,font,card,x,y,PANEL_W-12);
     // AD-125: a building being moved says where its move stands.
     var active=live.getCompound("relocation").getCompound("active");
     if(active.hasUUID("building")&&card.hasUUID("id")&&active.getUUID("building").equals(card.getUUID("id")))
      wrap(g,Component.translatable("atlas.villageastra.relocation.progress",Component.translatable("atlas.villageastra.relocation.phase."+active.getInt("phase")),active.getInt("done"),active.getInt("total")),x,end+2,0xFFE0A85A);
     break;}
    y=wrap(g,Component.translatable("atlas.villageastra.hint.view"),x,y,grey);
    var c=chunk==Long.MIN_VALUE?null:CHUNKS.get(chunk);
    if(c!=null){var cp=new ChunkPos(chunk);y+=4;
     int roads=0;for(long raw:live.getLongArray("roads"))if(new ChunkPos(BlockPos.of(raw)).toLong()==chunk)roads++;
     int buildings=0;for(var raw:live.getList("buildings",Tag.TAG_COMPOUND))if(new ChunkPos(BlockPos.of(((CompoundTag)raw).getLong("pos"))).toLong()==chunk)buildings++;
     int inside=0;for(var raw:people())if(new ChunkPos(BlockPos.of(((CompoundTag)raw).getLong("pos"))).toLong()==chunk)inside++;
     y=wrap(g,Component.translatable("atlas.villageastra.chunk.where",cp.x,cp.z,cp.getMinBlockX(),cp.getMinBlockZ()),x,y,0xFFE9CD92);
     y=wrap(g,Component.translatable("atlas.villageastra.chunk.surveyed",c.getLong("tick")/24000),x,y,0xFF8FA2A8);
     y=wrap(g,Component.translatable("atlas.villageastra.chunk.contents",buildings,roads,inside),x,y,good);}
   }
  }
  // The answer to the last order stays in the panel until the mayor does something else.
  if(!outcome.isEmpty()&&outcome.getInt("tool")==server(tool)&&server(tool)>=0){
   var text=outcome.getBoolean("ordered")?Component.translatable("maporder.villageastra.done."+outcome.getInt("tool")):Component.translatable("maporder.villageastra.refused."+outcome.getString("reason"),0);
   int lines=font.split(text,PANEL_W-12).size();wrap(g,text,x,bottom-26-lines*10,outcome.getBoolean("ordered")?good:bad);}
 }
 private void line(GuiGraphics g,Component text,int x,int y,int colour){g.drawString(font,font.plainSubstrByWidth(text.getString(),PANEL_W-12),x,y,colour,false);}
 /** ISO-005: the estimate of the planned spot — what is cleared, placed, borrowed and still missing. */
 private void estimate(GuiGraphics g,int x,int y){
  if(plan.getBoolean("missing")){g.drawString(font,Component.translatable("atlas.villageastra.missing"),x,y,0xFFDD9C66,false);return;}
  if(!plan.getString("reason").isEmpty()&&!plan.getString("reason").equals("conflicts")){
   g.drawString(font,Component.translatable("atlas.villageastra.plan.reason."+plan.getString("reason")),x,y,0xFFDD9C66,false);return;}
  int wrap=PANEL_W-12;
  for(var line:List.of(
   Component.translatable("atlas.villageastra.plan.title",ConstructionOverlay.designName(plan.getString("design"))),
   Component.translatable("atlas.villageastra.plan.size",plan.getInt("width"),plan.getInt("depth"),clearance(plan)<0?Component.translatable("atlas.villageastra.plan.alone"):Component.literal(String.valueOf(clearance(plan)))),
   Component.translatable("atlas.villageastra.plan.floor",BlockPos.of(plan.getLong("origin")).getY(),(floorOffset>0?"+":"")+floorOffset),
   Component.translatable("atlas.villageastra.plan.totals",plan.getInt("operations"),plan.getInt("cells"),plan.getInt("items")),
   Component.translatable("atlas.villageastra.plan.parts",plan.getInt("clear"),plan.getInt("place"),plan.getInt("temporary")),
   Component.translatable("atlas.villageastra.plan.kinds",plan.getInt("keep"),plan.getInt("replace"),plan.getInt("conflictCount")),
   plan.contains("cut")?Component.translatable("atlas.villageastra.plan.earth",plan.getInt("cut"),plan.getInt("fill"),plan.getInt("earthMissing"))
    :plan.getBoolean("engineer")?Component.empty():Component.translatable("atlas.villageastra.plan.noengineer"))){
   for(var part:font.split(line,wrap)){if(y>height-BAR-40)return;g.drawString(font,part,x,y,y<TOP+10?0xFFE9CD92:0xFFBFD1C8,false);y+=10;}}
  y+=4;int shown=0;
  for(var raw:plan.getList("materials",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(shown>=6||y>height-BAR-56)break;if(t.getInt("missing")==0&&plan.getInt("shortage")>0)continue;shown++;
   var item=BuiltInRegistries.ITEM.get(new ResourceLocation(t.getString("item")));
   g.renderItem(new ItemStack(item),x,y-3);
   g.drawString(font,font.plainSubstrByWidth(Component.translatable("atlas.villageastra.plan.material",item.getDescription(),t.getInt("free"),t.getInt("need")).getString(),wrap-18),x+18,y+1,t.getInt("missing")>0?0xFFDD9C66:0xFF9FD39A,false);y+=16;}
  for(var part:font.split(Component.translatable(plan.getInt("shortage")>0?"atlas.villageastra.plan.missing":"atlas.villageastra.plan.ready",plan.getInt("shortage")),wrap)){if(y>height-BAR-40)return;
   g.drawString(font,part,x,y+2,plan.getInt("shortage")>0?0xFFDD9C66:0xFF9FD39A,false);y+=10;}
  if(y>height-BAR-50)return;
  if(plan.getInt("conflictCount")>0)g.drawString(font,Component.translatable("atlas.villageastra.plan.conflicts",plan.getInt("conflictCount")),x,y+4,0xFFE04A3A,false);
  if(!DRAFTS.isEmpty())g.drawString(font,Component.translatable("atlas.villageastra.drafts",DRAFTS.size(),org.villageastra.world.Plans.DRAFTS),x,y+16,0xFF8FA2A8,false);
 }
 /** AD-125: what the move takes — blocks taken apart and given back, blocks built and how many of them are the building's own, what the hall
  *  still pays, the contents that move along, how long it takes and how long the building stands idle, and why it cannot be ordered. */
 /** Height of wrapped panel lines, with a gap above each. */
 private int height(List<Object[]> lines,int gap){int h=0;for(var line:lines)h+=font.split((Component)line[0],PANEL_W-12).size()*10+gap;return h;}
 private void movePanel(GuiGraphics g,int x,int y,int limit){
  int grey=0xFFBFD1C8,warn=0xFFDD9C66,good=0xFF9FD39A,bad=0xFFE04A3A,dim=0xFF8FA2A8;var p=preview;
  if(moveTarget==null){wrap(g,Component.translatable("atlas.villageastra.move.hint"),x,y,grey);return;}
  if(p.isEmpty()||!p.contains("reason")){wrap(g,Component.translatable("atlas.villageastra.checking"),x,y,dim);return;}
  // The figures and the verdict always fit; material rows take the room that is left, the level line only when there is room for it.
  var head=new ArrayList<Object[]>();var tail=new ArrayList<Object[]>();
  if(p.contains("dismantle")){
   head.add(new Object[]{Component.translatable("atlas.villageastra.move.dismantle",p.getInt("dismantle"),p.getInt("returned")),0xFFE0A85A});
   head.add(new Object[]{Component.translatable("atlas.villageastra.move.build",p.getInt("place")),grey});
   head.add(new Object[]{Component.translatable("atlas.villageastra.move.reused",p.getInt("reused"),p.getInt("place")),good});
   head.add(new Object[]{Component.translatable("atlas.villageastra.move.buy",p.getInt("items"),p.getInt("shortage")),p.getInt("shortage")>0?warn:good});}
  // Most important first: a small window cuts the panel from the bottom, so time and verdict come before the details.
  if(p.contains("seconds"))tail.add(new Object[]{Component.translatable("atlas.villageastra.move.time",(p.getInt("seconds")+59)/60,(p.getInt("downtime")+59)/60),grey});
  if(!p.getString("reason").isEmpty())tail.add(new Object[]{Component.translatable("relocate.villageastra.refused."+p.getString("reason"),p.getInt("conflictCount")),bad});
  else tail.add(new Object[]{Component.translatable("atlas.villageastra.move.ready"),good});
  if(p.getBoolean("busyBuilders"))tail.add(new Object[]{Component.translatable("maporder.villageastra.refused.busy_builders"),warn});
  if(p.contains("containers")){
   tail.add(new Object[]{Component.translatable("atlas.villageastra.move.contents",p.getInt("containers"),p.getInt("packed")),grey});
   if(p.getInt("workers")>0)tail.add(new Object[]{Component.translatable("atlas.villageastra.move.workers",p.getInt("workers")),warn});
   if(p.getInt("dwellers")>0)tail.add(new Object[]{Component.translatable("atlas.villageastra.move.dwellers",p.getInt("dwellers")),warn});}
  int headH=height(head,0),tailH=height(tail,1);
  if(p.contains("level")){var level=new Object[]{Component.translatable("atlas.villageastra.move.level",p.getInt("level"),p.getInt("grade")),dim};
   if(y+headH+tailH+height(List.<Object[]>of(level),0)+16<=limit)head.add(0,level);}
  for(var line:head){if(y>limit-10)return;y=wrap(g,(Component)line[0],x,y,(int)line[1]);}
  int shown=0;
  for(var raw:p.getList("materials",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(shown>=6||y+16+tailH>limit)break;shown++;
   var item=BuiltInRegistries.ITEM.get(new ResourceLocation(t.getString("item")));g.renderItem(new ItemStack(item),x,y-2);
   g.drawString(font,font.plainSubstrByWidth(Component.translatable("atlas.villageastra.plan.material",item.getDescription(),t.getInt("free"),t.getInt("need")).getString(),PANEL_W-30),x+18,y+2,t.getInt("missing")>0?warn:good,false);y+=16;}
  for(var line:tail){if(y>limit-10)return;y=wrap(g,(Component)line[0],x,y+1,(int)line[1]);}
 }
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  int mapBottom=height-BAR;long now=System.currentTimeMillis();
  g.fill(0,0,width,height,0xFF10161B);g.fillGradient(0,TOP,width,mapBottom,0xFF1B2A38,0xFF0C1218);
  if(dirty&&(now-built>400||CHUNKS.size()>=live.getInt("total")))rebuild();
  frame();follow(now);
  var m=scene();
  g.enableScissor(0,TOP,width,mapBottom);
  RenderSystem.clear(0x100,Minecraft.ON_OSX);RenderSystem.enableDepthTest();RenderSystem.disableCull();RenderSystem.disableBlend();
  if(terrain!=null){var modelView=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(m);
   RenderSystem.setShaderTexture(0,InventoryMenu.BLOCK_ATLAS);
   terrain.bind();terrain.drawWithShader(modelView,RenderSystem.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());VertexBuffer.unbind();}
  // AD-067: «after» — the planned building in its real blocks, standing in the terrain as it will.
  if(tool==HOUSE&&after&&plan.contains("origin")&&plan.getIntArray("future").length>0){
   var key=plan.getLong("origin")+"/"+plan.getString("design")+"/"+plan.getIntArray("future").length;
   if(!key.equals(futureKey)||future==null){
    if(future!=null)future.close();future=null;futureKey=key;var origin=BlockPos.of(plan.getLong("origin"));var cells=plan.getIntArray("future");var blocks=new ArrayList<int[]>();
    for(int i=0;i+3<cells.length;i+=4)blocks.add(new int[]{origin.getX()+cells[i],origin.getY()+cells[i+1],origin.getZ()+cells[i+2],cells[i+3]});
    var fb=new BufferBuilder(1<<16);fb.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);int before=TERRAIN.quads;TERRAIN.models(fb,blocks,center());futureQuads=TERRAIN.quads-before;TERRAIN.quads=before;
    var rendered=fb.end();if(rendered.isEmpty())rendered.release();else{future=new VertexBuffer(VertexBuffer.Usage.STATIC);future.bind();future.upload(rendered);VertexBuffer.unbind();}}
   if(future!=null){var modelView=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(m);RenderSystem.setShaderTexture(0,InventoryMenu.BLOCK_ATLAS);
    future.bind();future.drawWithShader(modelView,RenderSystem.getProjectionMatrix(),GameRenderer.getPositionTexColorShader());VertexBuffer.unbind();}}
  hover=my>=TOP&&my<mapBottom&&!overPanel(mx,my)?pickAt(m,mx,my):null;
  overlays(m);
  // Faces, names and cards are drawn over the whole model: the GUI render types test depth, so the terrain depth is cleared first.
  RenderSystem.clear(0x100,Minecraft.ON_OSX);RenderSystem.disableDepthTest();
  labels(g,m);residents(g,m,mx,my,now);
  g.disableScissor();
  RenderSystem.clear(0x100,Minecraft.ON_OSX);RenderSystem.enableDepthTest();RenderSystem.enableCull();
  g.fill(0,0,width,TOP,0xFF151E25);g.fill(0,mapBottom,width,height,0xFF151E25);
  var first=buttons.get("design");int room=(first==null?width:first.getX())-10;
  // The full title where it fits, the short one where the planner takes the bar.
  var heading=live.getString("name").isEmpty()?title:Component.translatable("atlas.villageastra.title_named",VillageNameText.shown(live.getString("name")));
  var name=font.width(heading)<=room?heading:Component.translatable("atlas.villageastra.open");
  if(font.width(name)<=room)g.drawString(font,name,6,6,0xFFE9CD92,false);
  Component status=live.getBoolean("missing")?Component.translatable("atlas.villageastra.missing"):Component.translatable("atlas.villageastra.progress",CHUNKS.size(),live.getInt("area"),live.getLong("generation"),people().size(),live.getList("enemies",Tag.TAG_LONG).size());
  var view=Component.translatable("atlas.villageastra.view",Component.translatable(TERRAIN.crowns?"atlas.villageastra.view_all":"atlas.villageastra.view_ground"),
   TERRAIN.cut==Integer.MAX_VALUE?Component.translatable("atlas.villageastra.view_full"):Component.literal(String.valueOf(TERRAIN.cut)));
  int statusY=mapBottom-11,textRoom=(panelShown()?width-PANEL_W-4:width)-12;
  g.fill(2,statusY-12,Math.min(textRoom+8,Math.max(font.width(status),font.width(view))+10),mapBottom-1,0x900E141A);
  g.drawString(font,font.plainSubstrByWidth(status.getString(),textRoom),6,statusY,0xFFBFD1C8,false);
  g.drawString(font,font.plainSubstrByWidth(view.getString(),textRoom),6,statusY-10,TERRAIN.crowns&&TERRAIN.cut==Integer.MAX_VALUE&&TERRAIN.isolate==Long.MIN_VALUE?0xFF8FA2A8:0xFFDD9C66,false);
  if(hover!=null){
   var text=Component.translatable("atlas.villageastra.hover",hover.getX(),hover.getY(),hover.getZ());
   g.drawString(font,font.plainSubstrByWidth(text.getString(),textRoom),6,statusY-21,0xFFE3ECE6,true);}
  if(live.getLongArray("roads").length>0&&live.getInt("roadsDark")>0)g.drawString(font,Component.translatable("atlas.villageastra.roads",live.getInt("roadsLit"),live.getInt("roadsDark")),6,TOP+4,0xFFDD9C66,true);
  if(CHUNKS.isEmpty()&&!live.getBoolean("missing"))g.drawCenteredString(font,Component.translatable("atlas.villageastra.empty"),width/2,height/2,0xFFDD9C66);
  sidePanel(g,mx,my);
  super.render(g,mx,my,dt);
 }
 private UUID iconAt(double mx,double my){
  UUID best=null;double distance=Double.MAX_VALUE;
  for(var e:icons.entrySet()){var at=e.getValue();double half=at[2]/2+3;
   if(Math.abs(mx-at[0])<=half&&my>=at[1]-half&&my<=at[1]+half+6){double d=Math.abs(mx-at[0])+Math.abs(my-at[1]);if(d<distance){distance=d;best=e.getKey();}}}
  return best;
 }
 private boolean turned;
 @Override public boolean mouseClicked(double mx,double my,int button){
  if(super.mouseClicked(mx,my,button))return true;
  if(button==1)turned=false;
  if(my<TOP||my>=height-BAR||button!=0)return false;
  if(overPanel(mx,my))return true;
  // A resident's face picks that resident: the map then glides after them while they work.
  var person=iconAt(mx,my);
  if(person!=null){followed=person.equals(followed)?null:person;panX=0;panY=0;return true;}
  var hit=pickAt(scene(),mx,my);if(hit==null)return false;
  selected=hit;chunk=new ChunkPos(hit).toLong();
  if(TERRAIN.isolate!=Long.MIN_VALUE&&TERRAIN.isolate!=chunk){TERRAIN.isolate=chunk;viewChanged();}
  switch(tool){
   // AD-058: a house stands level with the ground: its floor takes the place of the clicked ground block.
   case HOUSE->{houseGround=hit;requestPlan(hit.offset(0,floorOffset,0));}
   // AD-125: the new place of the moved building, on the same floor rule as a new house.
   case MOVE->{moveGround=hit;moveTarget=snap(hit.offset(0,floorOffset,0));outcome=new CompoundTag();marksChanged();}

   // AD-123: a trail takes up to eight waypoints, clicked in the order the trail should pass them.
   case TRAIL->{if(WAYPOINTS.size()<org.villageastra.world.Trails.MAX_WAYPOINTS)WAYPOINTS.add(new BlockPos(hit.getX(),0,hit.getZ()));}
   // Roads and clearings take two corners; a third click starts over from the new spot.
   case ROAD,DEMOLISH->{
    if(cornerA==null||cornerB!=null){cornerA=hit;cornerB=null;}
    else{cornerB=hit;if(tool==DEMOLISH)level=Math.min(cornerA.getY(),cornerB.getY());}
    outcome=new CompoundTag();marksChanged();}
   // In the view a click on a building opens its card; a click on open ground closes it.
   default->{building=null;
    for(var raw:live.getList("buildings",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var at=BlockPos.of(t.getLong("pos"));
     if(t.hasUUID("id")&&hit.getX()>=at.getX()&&hit.getX()<at.getX()+t.getInt("w")&&hit.getZ()>=at.getZ()&&hit.getZ()<at.getZ()+t.getInt("d"))building=t.getUUID("id");}}
  }
  refreshTools();return true;
 }
 @Override public boolean mouseReleased(double mx,double my,int button){
  // AD-125: a right click that did not turn the view gives the move up.
  if(button==1&&tool==MOVE&&!turned&&my>=TOP&&my<height-BAR&&!overPanel(mx,my)){cancelMove();return true;}
  refreshTools();return super.mouseReleased(mx,my,button);}
 @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
  if(super.mouseDragged(mx,my,button,dx,dy))return true;
  if(button==1){yaw+=(float)dx*.6F;if(Math.abs(dx)+Math.abs(dy)>1)turned=true;return true;}
  panX+=dx;panY+=dy;if(followed!=null&&Math.abs(dx)+Math.abs(dy)>2)followed=null;return true;
 }
 @Override public boolean mouseScrolled(double mx,double my,double delta){
  if(overPanel(mx,my))return true;
  zoomAt((float)mx,(float)my,delta>0?1.2F:1/1.2F);return true;
 }
 @Override public boolean keyPressed(int key,int scan,int modifiers){
  float step=24;
  // AD-125: R turns the moved building, PgUp/PgDn raise or lower its floor, Esc goes back to the view with its card.
  if(tool==MOVE)switch(key){
   case 82->{turnMove();return true;}
   case 266->{floorOffset=Math.min(3,floorOffset+1);replan();return true;}
   case 267->{floorOffset=Math.max(-3,floorOffset-1);replan();return true;}
   case 256->{cancelMove();return true;}
   default->{}
  }
  switch(key){
   case 87,265->{panY+=step;return true;}
   case 83,264->{panY-=step;return true;}
   case 65,263->{panX+=step;return true;}
   case 68,262->{panX-=step;return true;}
   case 81->{yaw-=15;return true;}
   case 69->{yaw+=15;return true;}
   default->{return super.keyPressed(key,scan,modifiers);}
  }
 }
 @Override public void removed(){if(terrain!=null)terrain.close();terrain=null;if(future!=null)future.close();future=null;}
 @Override public boolean isPauseScreen(){return false;}
}

package org.villageastra.client;
import java.util.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.villageastra.VillageAstra;
/** AD-126: the construction schematic in the world. Blocks still to be placed are their real models, translucent and cool-tinted, baked
 *  into one vertex buffer per Y layer (only changed layers are baked again, a few per frame); frames mark demolition (red), a foreign block
 *  in the way (amber, pulsing, with an X), scaffolds (straw), the builder's current step (white pulse) and his next ones; a placed block
 *  fades out green. Layer modes and the cut-away around the camera decide which layers are drawn. */
public final class SchematicRenderer {
 private SchematicRenderer(){}
 static final SchematicModel MODEL=new SchematicModel();
 public static SchematicModel.Mode mode=SchematicModel.Mode.ALL;
 public static int manualY=SchematicModel.NONE;
 public static volatile boolean fallback;
 // Probe counters.
 public static volatile int bakes,bakedLayers,layersDrawn,layersCut,fadesActive,pendingLayers;public static volatile long lastBakeNanos,vertices;public static volatile double drawNanosAvg;
 private static final int TINT=0xB8D4FF,FILL=0x9CC4FF,REMOVE=0xFF4A5A,CONFLICT=0xFFA23A,SCAFFOLD=0xE8C97A,DONE=0x7CE08A,BRACKET=0xE9CD92,BUDGET=256;
 private static final float SCALE=.998f;
 private record LayerMesh(VertexBuffer vbo,AABB bounds,int vertices){}
 private static final Map<Integer,LayerMesh> MESHES=new TreeMap<>();
 private static final LinkedHashSet<Integer> PENDING=new LinkedHashSet<>();
 private static final Map<String,BlockState> STATES=new HashMap<>();
 private static BufferBuilder builder;
 private static BlockPos origin=BlockPos.ZERO;
 private static boolean draft,survey,warned;
 private static long now;

 static String key(BlockState s){return s.toString();}
 private static BlockState state(String key){return key==null||key.isEmpty()?Blocks.AIR.defaultBlockState():STATES.getOrDefault(key,Blocks.AIR.defaultBlockState());}
 private static BlockState read(CompoundTag t){var s=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),t);STATES.putIfAbsent(key(s),s);return s;}

 /** A new snapshot from the server. C9: no project or no cells in it — everything baked is released at once. */
 static void accept(CompoundTag snapshot,ClientLevel level,long ticks){
  now=ticks;
  if(level==null||!snapshot.hasUUID("id")||!snapshot.contains("cells",Tag.TAG_LIST)||snapshot.getList("cells",Tag.TAG_COMPOUND).isEmpty()){release();return;}
  survey=snapshot.getBoolean("survey");draft=snapshot.getBoolean("draft");
  var scaffold=VillageAstra.TIMBER_SCAFFOLD.get();var cells=new ArrayList<SchematicModel.Cell>();
  for(var raw:snapshot.getList("cells",Tag.TAG_COMPOUND)){
   var c=(CompoundTag)raw;var pos=BlockPos.of(c.getLong("pos"));var before=read(c.getCompound("before"));var after=read(c.getCompound("after"));
   var world=level.getBlockState(pos);
   // C2: grass, flowers and snow never hide a ghost; a solid block does, and so does the block a replacement has still to take down.
   boolean occupied=world.isSolidRender(level,pos)&&!world.canBeReplaced()||!before.isAir()&&!before.canBeReplaced()&&world.getBlock()==before.getBlock()&&world.getBlock()!=after.getBlock();
   // C1: the mayor's survey rewrites cells over a project view; its cells have no place in any queue.
   int op=SchematicModel.opOf(survey,c.contains("op"),c.getInt("op"));
   cells.add(new SchematicModel.Cell(pos.asLong(),op,key(before),key(after),before.isAir(),after.isAir(),before.canBeReplaced(),before.is(scaffold),after.is(scaffold),c.getBoolean("blocked"),occupied));
  }
  int index=survey?0:snapshot.getInt("index"),total=survey?0:snapshot.getInt("total");long target=SchematicModel.targetOf(survey,snapshot.contains("target"),snapshot.getLong("target"));
  String id=snapshot.getUUID("id")+(survey?"/survey":draft?"/draft":"");
  var diff=MODEL.apply(id,cells,index,target,total,ticks,(p,s)->s.equals(key(level.getBlockState(BlockPos.of(p)))));
  // A hand-picked layer belongs to the project it was picked on: another site or a renumbered queue starts with all layers.
  if(diff.reset()){closeMeshes();var b=MODEL.bounds();origin=new BlockPos(b[0],b[1],b[2]);manualY=SchematicModel.NONE;}
  PENDING.addAll(diff.dirtyLayers());pendingLayers=PENDING.size();
 }
 /** Releases every buffer and forgets the project (another world, logging out, an expired or empty snapshot). */
 static void release(){closeMeshes();manualY=SchematicModel.NONE;MODEL.clear();PENDING.clear();pendingLayers=0;STATES.clear();layersDrawn=0;fadesActive=0;}
 private static void closeMeshes(){for(var m:MESHES.values())if(m.vbo!=null)m.vbo.close();MESHES.clear();PENDING.clear();bakedLayers=0;vertices=0;}
 public static boolean hasMeshes(){return !MESHES.isEmpty();}

 /** C8: whole layers per frame while their cells stay under the budget; at least one layer, a big layer alone. */
 private static void bakePending(ClientLevel level){
  if(PENDING.isEmpty())return;int used=0,baked=0;var ghost=new GhostGetter(level);
  for(var it=PENDING.iterator();it.hasNext();){int y=it.next();int n=MODEL.models(y).size();if(baked>0&&used+n>=BUDGET)break;it.remove();bake(level,ghost,y);used+=n;baked++;}
  pendingLayers=PENDING.size();
 }
 private static void bake(ClientLevel level,GhostGetter ghost,int y){
  long t0=System.nanoTime();var old=MESHES.remove(y);if(old!=null&&old.vbo!=null){old.vbo.close();vertices-=old.vertices;}
  var list=MODEL.models(y);bakes++;
  if(!list.isEmpty()){
   if(builder==null)builder=new BufferBuilder(1<<18);
   builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLOCK);
   var pose=new PoseStack();AABB bounds=null;int quads=0;
   for(var c:list){var s=state(c.after());var pos=BlockPos.of(c.pos());
    pose.pushPose();pose.translate(pos.getX()-origin.getX(),pos.getY()-origin.getY(),pos.getZ()-origin.getZ());
    quads+=model(builder,pose,level,ghost,s,pos,1f);pose.popPose();
    var box=new AABB(pos);bounds=bounds==null?box:bounds.minmax(box);}
   var rendered=builder.end();
   if(rendered.isEmpty())rendered.release();
   else{var vbo=new VertexBuffer(VertexBuffer.Usage.STATIC);vbo.bind();vbo.upload(rendered);VertexBuffer.unbind();MESHES.put(y,new LayerMesh(vbo,bounds,quads*4));vertices+=quads*4;}
  }
  bakedLayers=MESHES.size();lastBakeNanos=System.nanoTime()-t0;
 }
 private static final Direction[] SIDES={null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
 private static final int[] LIGHT={LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT,LightTexture.FULL_BRIGHT};
 /** The block's own model, faces against other ghosts or a solid real neighbour left out; a model without quads (a sign, a chest) is
  *  filled by its shape instead. Returns the quads written. */
 private static int model(VertexConsumer out,PoseStack pose,ClientLevel level,BlockAndTintGetter ghost,BlockState s,BlockPos pos,float alpha){
  var mc=Minecraft.getInstance();pose.pushPose();pose.translate(.5,.5,.5);pose.scale(SCALE,SCALE,SCALE);pose.translate(-.5,-.5,-.5);
  int written=0,found=0;float tr=((TINT>>16)&255)/255f,tg=((TINT>>8)&255)/255f,tb=(TINT&255)/255f;
  if(s.getRenderShape()==RenderShape.MODEL){
   var model=mc.getBlockRenderer().getBlockModel(s);
   for(var dir:SIDES){var quads=model.getQuads(s,dir,RandomSource.create(s.getSeed(pos)),ModelData.EMPTY,null);found+=quads.size();
    if(quads.isEmpty()||ghost!=null&&dir!=null&&!Block.shouldRenderFace(s,ghost,pos,dir,pos.relative(dir)))continue;
    for(var q:quads){int color=q.isTinted()?mc.getBlockColors().getColor(s,level,pos,q.getTintIndex()):-1;if(color==-1)color=0xFFFFFF;
     float shade=level.getShade(q.getDirection(),q.isShade());var b=new float[]{shade,shade,shade,shade};
     out.putBulkData(pose.last(),q,b,((color>>16)&255)/255f*tr,((color>>8)&255)/255f*tg,(color&255)/255f*tb,alpha,LIGHT,OverlayTexture.NO_OVERLAY,false);written++;}}
  }
  // C5: no model quads at all (block entities, signs, banners, heads) — the block's shape in a flat cool colour.
  if(found==0){var shape=s.getShape(level,pos);var boxes=shape.isEmpty()?List.of(new AABB(0,0,0,1,1,1)):shape.toAabbs();for(var a:boxes)written+=fill(out,pose,a,FILL,alpha);}
  pose.popPose();return written;
 }
 private static int fill(VertexConsumer out,PoseStack pose,AABB a,int color,float alpha){
  var sprite=Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(new net.minecraft.resources.ResourceLocation("minecraft","block/white_concrete"));
  float u=(sprite.getU0()+sprite.getU1())/2,v=(sprite.getV0()+sprite.getV1())/2,r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;
  float x0=(float)a.minX,y0=(float)a.minY,z0=(float)a.minZ,x1=(float)a.maxX,y1=(float)a.maxY,z1=(float)a.maxZ;
  float[][] faces={{x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0},{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0,0,1,0},{x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0,0,0,-1},
   {x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,0,0,1},{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,-1,0,0},{x1,y0,z0,x1,y1,z0,x1,y1,z1,x1,y0,z1,1,0,0}};
  Matrix4f m=pose.last().pose();Matrix3f n=pose.last().normal();
  for(var f:faces)for(int i=0;i<4;i++)out.vertex(m,f[i*3],f[i*3+1],f[i*3+2]).color(r,g,b,alpha).uv(u,v).uv2(LightTexture.FULL_BRIGHT).normal(n,f[12],f[13],f[14]).endVertex();
  return 6;
 }

 static void render(RenderLevelStageEvent e,MultiBufferSource.BufferSource buffers,List<ConstructionOverlay.Box> legacy){
  var mc=Minecraft.getInstance();var level=mc.level;layersDrawn=0;layersCut=0;
  if(level==null||MODEL.empty()){fadesActive=0;return;}
  var cam=e.getCamera().getPosition();var b=MODEL.bounds();
  // Nothing is drawn from farther than 96 blocks from the site.
  double dx=Math.max(0,Math.max(b[0]-cam.x,cam.x-b[3]-1)),dy=Math.max(0,Math.max(b[1]-cam.y,cam.y-b[4]-1)),dz=Math.max(0,Math.max(b[2]-cam.z,cam.z-b[5]-1));
  if(dx*dx+dy*dy+dz*dz>96*96)return;
  long t0=System.nanoTime();float partial=e.getPartialTick();long ticks=level.getGameTime();
  int eyeY=Mth.floor(cam.y);boolean inside=MODEL.inside(cam.x,cam.y,cam.z);int targetY=MODEL.targetY();
  for(int y:MODEL.layers())if(SchematicModel.cut(y,eyeY,inside))layersCut++;
  var pose=e.getPoseStack();
  if(!fallback){
   // A failure half way (a shader pack, a driver) must not leave the level's pose stack or the translucent state behind: the vanilla
   // renderer checks the stack is empty, so the fallback would otherwise be a crash.
   boolean state=false,pushed=false;
   try{bakePending(level);
    if(!MESHES.isEmpty()){
     RenderType.translucent().setupRenderState();state=true;RenderSystem.depthMask(false);
     var shader=GameRenderer.getRendertypeTranslucentShader();if(shader==null)throw new IllegalStateException("No translucent shader");if(shader.CHUNK_OFFSET!=null)shader.CHUNK_OFFSET.set(0f,0f,0f);
     pose.pushPose();pushed=true;pose.translate(origin.getX()-cam.x,origin.getY()-cam.y,origin.getZ()-cam.z);var mv=pose.last().pose();
     for(var entry:MESHES.entrySet()){int y=entry.getKey();var mesh=entry.getValue();float a=SchematicModel.alpha(y,mode,targetY,manualY,eyeY,inside);
      if(a<=0||!e.getFrustum().isVisible(mesh.bounds))continue;
      RenderSystem.setShaderColor(1,1,1,a);mesh.vbo.bind();mesh.vbo.drawWithShader(mv,e.getProjectionMatrix(),shader);VertexBuffer.unbind();layersDrawn++;}
    }
   }catch(RuntimeException ex){
    if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("ZimboVillagers schematic: vertex buffers unavailable, falling back to frames",ex);}fallback=true;layersDrawn=0;
   }finally{
    if(pushed)pose.popPose();if(state){RenderSystem.setShaderColor(1,1,1,1);RenderSystem.depthMask(true);VertexBuffer.unbind();RenderType.translucent().clearRenderState();}
    if(fallback)closeMeshes();
   }
  }
  pose.pushPose();pose.translate(-cam.x,-cam.y,-cam.z);
  if(fallback)ConstructionOverlay.draw(pose,buffers);
  else overlays(pose,buffers,level,ticks,partial,eyeY,inside,targetY);
  pose.popPose();
  buffers.endBatch(RenderType.translucent());buffers.endBatch(RenderType.debugFilledBox());buffers.endBatch(RenderType.lines());
  long spent=System.nanoTime()-t0;drawNanosAvg=drawNanosAvg==0?spent:drawNanosAvg*.95+spent*.05;
 }
 private static void overlays(PoseStack pose,MultiBufferSource buffers,ClientLevel level,long ticks,float partial,int eyeY,boolean inside,int targetY){
  float pulse=SchematicModel.pulse(ticks,partial);var scaffold=VillageAstra.TIMBER_SCAFFOLD.get();
  var cells=MODEL.cells().stream().filter(c->SchematicModel.alpha(c.y(),mode,targetY,manualY,eyeY,inside)>0).toList();
  // One batch per render type: the shared buffer is drawn each time the type changes.
  var fills=buffers.getBuffer(RenderType.debugFilledBox());
  for(var c:cells){var pos=BlockPos.of(c.pos());
   if(c.kind()==SchematicModel.Kind.REMOVE||c.kind()==SchematicModel.Kind.REPLACE)for(var a:shape(state(c.before()),level,pos))fill(pose,fills,a,REMOVE,.14f);
   else if(c.kind()==SchematicModel.Kind.CONFLICT)fill(pose,fills,new AABB(pos).inflate(.003),CONFLICT,.25f+.2f*pulse);}
  var target=MODEL.hasTarget()&&SchematicModel.alpha(MODEL.targetY(),mode,targetY,manualY,eyeY,inside)>0?BlockPos.of(MODEL.target()):null;
  var fades=MODEL.fades(ticks);fadesActive=fades.size();
  var ghosts=buffers.getBuffer(RenderType.translucent());
  if(target!=null){var cell=cells.stream().filter(c->c.pos()==MODEL.target()&&(c.kind()==SchematicModel.Kind.PLACE||c.kind()==SchematicModel.Kind.REPLACE)&&!c.afterAir()&&!c.occupied()).findFirst().orElse(null);
   if(cell!=null){pose.pushPose();pose.translate(target.getX(),target.getY(),target.getZ());model(ghosts,pose,level,null,state(cell.after()),target,.45f);pose.popPose();}}
  for(var f:fades){float k=Mth.clamp((ticks+partial-f.start())/SchematicModel.FADE_TICKS,0,1);var p=BlockPos.of(f.pos());
   if(!f.state().isEmpty()&&k<1){pose.pushPose();pose.translate(p.getX(),p.getY(),p.getZ());model(ghosts,pose,level,null,state(f.state()),p,.6f*(1-k));pose.popPose();}}
  var lines=buffers.getBuffer(RenderType.lines());
  for(var c:cells){var pos=BlockPos.of(c.pos());
   switch(c.kind()){
    case REMOVE,REPLACE->{for(var a:shape(state(c.before()),level,pos))box(pose,lines,a.inflate(.004),REMOVE,.9f);}
    case CONFLICT->{var a=new AABB(pos);box(pose,lines,a.inflate(.004),CONFLICT,1);box(pose,lines,a.inflate(.03),CONFLICT,.8f);
     line(pose,lines,a.minX,a.maxY+.01,a.minZ,a.maxX,a.maxY+.01,a.maxZ,CONFLICT,1);line(pose,lines,a.maxX,a.maxY+.01,a.minZ,a.minX,a.maxY+.01,a.maxZ,CONFLICT,1);}
    case SCAFFOLD->{if(c.afterScaffold()||level.getBlockState(pos).is(scaffold))box(pose,lines,new AABB(pos).deflate(.05),SCAFFOLD,.5f);}
    default->{}
   }}
  if(target!=null){box(pose,lines,new AABB(target).inflate(.006),0xFFFFFF,.45f+.55f*pulse);
   for(var c:MODEL.following(3))if(c.pos()!=MODEL.target()&&SchematicModel.alpha(c.y(),mode,targetY,manualY,eyeY,inside)>0)box(pose,lines,new AABB(BlockPos.of(c.pos())).inflate(.004),0xFFFFFF,.35f);}
  for(var f:fades){float k=Mth.clamp((ticks+partial-f.start())/SchematicModel.FADE_TICKS,0,1);box(pose,lines,new AABB(BlockPos.of(f.pos())).inflate(.01),DONE,1-k);}
  if(draft||survey)brackets(pose,lines);
 }
 /** MineColonies-like corner brackets around the whole site: an estimate or a survey not yet confirmed. */
 private static void brackets(PoseStack pose,VertexConsumer lines){
  var b=MODEL.bounds();double x0=b[0]-.02,y0=b[1]-.02,z0=b[2]-.02,x1=b[3]+1.02,y1=b[4]+1.02,z1=b[5]+1.02;
  double lx=Math.min(2,(x1-x0)/3),ly=Math.min(2,(y1-y0)/3),lz=Math.min(2,(z1-z0)/3);
  for(double x:new double[]{x0,x1})for(double y:new double[]{y0,y1})for(double z:new double[]{z0,z1}){
   double sx=x==x0?1:-1,sy=y==y0?1:-1,sz=z==z0?1:-1;
   line(pose,lines,x,y,z,x+sx*lx,y,z,BRACKET,1);line(pose,lines,x,y,z,x,y+sy*ly,z,BRACKET,1);line(pose,lines,x,y,z,x,y,z+sz*lz,BRACKET,1);}
 }
 private static List<AABB> shape(BlockState s,ClientLevel level,BlockPos pos){var shape=s.getShape(level,pos);return (shape.isEmpty()?List.of(new AABB(0,0,0,1,1,1)):shape.toAabbs()).stream().map(a->a.move(pos)).toList();}
 private static void fill(PoseStack pose,VertexConsumer out,AABB a,int c,float alpha){LevelRenderer.addChainedFilledBoxVertices(pose,out,a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ,((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f,alpha);}
 private static void box(PoseStack pose,VertexConsumer out,AABB a,int c,float alpha){LevelRenderer.renderLineBox(pose,out,a,((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f,alpha);}
 private static void line(PoseStack pose,VertexConsumer out,double x0,double y0,double z0,double x1,double y1,double z1,int c,float alpha){
  float nx=(float)(x1-x0),ny=(float)(y1-y0),nz=(float)(z1-z0);float len=Mth.sqrt(nx*nx+ny*ny+nz*nz);if(len==0)return;nx/=len;ny/=len;nz/=len;
  float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;var m=pose.last().pose();var n=pose.last().normal();
  out.vertex(m,(float)x0,(float)y0,(float)z0).color(r,g,b,alpha).normal(n,nx,ny,nz).endVertex();
  out.vertex(m,(float)x1,(float)y1,(float)z1).color(r,g,b,alpha).normal(n,nx,ny,nz).endVertex();
 }

 /** The world as the ghost sees it for face culling: the planned block where one is still to be placed, a solid real block where nothing
  *  is removed, air elsewhere. Light and tint come from the real level. */
 private static final class GhostGetter implements BlockAndTintGetter{
  private final ClientLevel level;private final Map<Long,BlockState> ghosts=new HashMap<>();private final Set<Long> removed=new HashSet<>();
  GhostGetter(ClientLevel level){this.level=level;
   for(int y:MODEL.layers())for(var c:MODEL.models(y))ghosts.put(c.pos(),state(c.after()));
   for(var c:MODEL.cells())if(c.kind()==SchematicModel.Kind.REMOVE||c.kind()==SchematicModel.Kind.REPLACE)removed.add(c.pos());}
  @Override public BlockState getBlockState(BlockPos p){var g=ghosts.get(p.asLong());if(g!=null)return g;if(removed.contains(p.asLong()))return Blocks.AIR.defaultBlockState();var s=level.getBlockState(p);return s.isSolidRender(level,p)?s:Blocks.AIR.defaultBlockState();}
  @Override public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
  @Override public BlockEntity getBlockEntity(BlockPos p){return null;}
  @Override public float getShade(Direction d,boolean shade){return level.getShade(d,shade);}
  @Override public LevelLightEngine getLightEngine(){return level.getLightEngine();}
  @Override public int getBlockTint(BlockPos p,ColorResolver r){return level.getBlockTint(p,r);}
  @Override public int getHeight(){return level.getHeight();}
  @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
 }
 static Vec3 center(){var b=MODEL.bounds();return new Vec3((b[0]+b[3]+1)/2.0,(b[1]+b[4]+1)/2.0,(b[2]+b[5]+1)/2.0);}
}

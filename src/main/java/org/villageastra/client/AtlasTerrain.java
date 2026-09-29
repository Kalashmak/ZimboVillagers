package org.villageastra.client;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
/** AD-056: the settlement map as a model of every remembered block. Each surveyed column is drawn block by block with the block's own model,
 *  so cliffs, trunks, walls, the soil under a canopy and the strata at the edge of the survey are all really there. */
final class AtlasTerrain {
 /** Nothing to draw here, and a block under the remembered bottom that must be assumed solid. */
 static final int AIR=0,SOLID=-1;
 private static final int KNOWN=1,OCCLUDES=2,FLUID=4,MODEL=8,LEAVES=16,CROWN=32,BOX=64,AIRY=128;
 /** Vanilla-like light of each face (down, up, north, south, west, east): tops bright, the far sides darker, so volumes read. */
 private static final float[] SHADE={.5F,1F,.8F,.9F,.68F,.86F};
 private static final Direction[] DIRECTIONS=Direction.values();
 private static int[] flags=new int[0];
 /** One remembered column: the y of its highest block and every state from there down. The leading crown entries are dropped with the canopy. */
 static final class Column{
  final int top;final int[] states;final int canopy;
  Column(int top,int[] states){this.top=top;this.states=states;int k=0;while(k<states.length&&(flags(states[k])&CROWN)!=0)k++;canopy=k;}
  int bottom(){return top-states.length+1;}
 }
 private final Map<Long,Column[]> columns=new HashMap<>();
 private final Map<Long,int[]> surface=new HashMap<>();
 boolean crowns=true;int cut=Integer.MAX_VALUE;long isolate=Long.MIN_VALUE;
 /** ISO-003: the side section — 0 none, 1 a plane across x, 2 across z; everything past the plane is left out of the view, so the cut
  *  face shows every stored layer of the columns standing on it. Like the slice it changes what is drawn, never a block. */
 int sideAxis;int side;
 int quads,blocks,lowest,highest;
 private final RandomSource random=RandomSource.create();
 private final BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
 private final float[] xyz=new float[12],uv=new float[8];

 static int flags(int id){
  if(id<=0)return KNOWN|CROWN|AIRY;
  if(id>=flags.length)flags=Arrays.copyOf(flags,Math.max(id+1,Block.BLOCK_STATE_REGISTRY.size()));
  int f=flags[id];if(f!=0)return f;
  var s=Block.stateById(id);f=KNOWN;
  if(s.isAir())f|=AIRY|CROWN;
  if(s.is(BlockTags.LEAVES))f|=LEAVES|OCCLUDES|CROWN;
  if(s.is(BlockTags.LOGS)||s.is(Blocks.VINE)||s.is(Blocks.SNOW))f|=CROWN;
  try{if(s.isSolidRender(EmptyBlockGetter.INSTANCE,BlockPos.ZERO))f|=OCCLUDES;}catch(RuntimeException ignored){}
  if(!s.getFluidState().isEmpty())f|=FLUID;
  if(s.getRenderShape()==RenderShape.MODEL)f|=MODEL;else if(s.getRenderShape()==RenderShape.ENTITYBLOCK_ANIMATED)f|=BOX;
  flags[id]=f;return f;
 }
 /** Older surveys stored block ids instead of states: they become the default state of that block. */
 private static int legacy(int blockId){return Block.getId(BuiltInRegistries.BLOCK.byId(blockId).defaultBlockState());}
 /** Decodes one surveyed chunk: run-length columns of states, or for the oldest surveys only the roof and the wall material. */
 static Column[] decode(CompoundTag c){
  var out=new Column[256];var heights=c.getIntArray("heights");var index=c.getIntArray("index");var data=c.getIntArray("columns");
  boolean states=c.getBoolean("states");var tops=c.getIntArray("tops");var faces=c.getIntArray("faces");
  for(int cell=0;cell<256;cell++){
   int top=heights.length==256?heights[cell]:0;
   if(index.length==256&&data.length>0&&index[cell]>=0&&index[cell]<data.length){
    int at=index[cell],runs=data[at],total=0;
    for(int r=0;r<runs&&at+2+r*2<data.length;r++)total+=data[at+2+r*2];
    var column=new int[total];int k=0;
    for(int r=0;r<runs&&at+2+r*2<data.length;r++){int id=states?data[at+1+r*2]:legacy(data[at+1+r*2]),len=data[at+2+r*2];
     if((flags(id)&AIRY)!=0)id=AIR;
     for(int i=0;i<len;i++)column[k++]=id;}
    out[cell]=new Column(top,column);
   }else if(tops.length==256){
    int face=legacy(faces.length==256?faces[cell]:tops[cell]);
    out[cell]=new Column(top,new int[]{legacy(tops[cell]),face,face,face});
   }
  }
  return out;
 }
 void clear(){columns.clear();surface.clear();}
 void put(long key,CompoundTag c){columns.put(key,decode(c));surface.remove(key);}
 boolean has(long key){return columns.containsKey(key);}
 Set<Long> keys(){return columns.keySet();}
 /** The remembered column at a world cell, exactly as surveyed, or null outside the opened map. */
 Column column(int x,int z){var cols=columns.get(ChunkPos.asLong(x>>4,z>>4));return cols==null?null:cols[(x&15)*16+(z&15)];}
 /** The state the current view shows at a block: canopy, slice and single-chunk isolation turn blocks into air; below the remembered bottom is solid. */
 int seen(int x,int y,int z){
  long key=ChunkPos.asLong(x>>4,z>>4);
  if(isolate!=Long.MIN_VALUE&&key!=isolate)return AIR;
  var cols=columns.get(key);if(cols==null)return AIR;
  var col=cols[(x&15)*16+(z&15)];if(col==null)return AIR;
  if(y>cut)return AIR;
  if(sideAxis==1&&x>side||sideAxis==2&&z>side)return AIR;
  int i=col.top-y;if(i<0)return AIR;if(i>=col.states.length)return SOLID;
  if(!crowns&&i<col.canopy)return AIR;
  return col.states[i];
 }
 /** Highest block the current view shows in a cell, or null outside the opened map. */
 Integer heightAt(int x,int z){
  long key=ChunkPos.asLong(x>>4,z>>4);var tops=surface.get(key);
  if(tops==null){var cols=columns.get(key);if(cols==null)return null;tops=surfaceOf(key,cols);surface.put(key,tops);}
  int h=tops[(x&15)*16+(z&15)];return h==Integer.MIN_VALUE?null:h;
 }
 private int[] surfaceOf(long key,Column[] cols){
  var cp=new ChunkPos(key);var out=new int[256];
  for(int cell=0;cell<256;cell++){out[cell]=Integer.MIN_VALUE;var col=cols[cell];if(col==null)continue;
   int wx=cp.getMinBlockX()+cell/16,wz=cp.getMinBlockZ()+cell%16;
   for(int i=0;i<col.states.length;i++){int y=col.top-i;if(seen(wx,y,wz)>0){out[cell]=y;break;}}}
  return out;
 }
 /** Sum of the visible heights: dropping the canopy really lowers it, because the ground under the trees is shown. */
 long heightSum(){long sum=0;for(var key:columns.keySet())for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){var cp=new ChunkPos(key);Integer h=heightAt(cp.getMinBlockX()+dx,cp.getMinBlockZ()+dz);if(h!=null)sum+=h;}return sum;}
 void viewChanged(){surface.clear();}
 /** Highest remembered block of the whole map: the slice starts just under it. */
 int top(){int top=Integer.MIN_VALUE;for(var cols:columns.values())for(var c:cols)if(c!=null)top=Math.max(top,c.top);return top==Integer.MIN_VALUE?0:top;}
 /** First block the view shows along a ray from the viewer, walked in small steps; null when the ray misses the map. */
 BlockPos pick(float[] near,float[] far,BlockPos origin){
  float dx=far[0]-near[0],dy=far[1]-near[1],dz=far[2]-near[2];double length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1e-3)return null;
  // Only the stretch of the ray between the highest and the lowest remembered block is walked.
  float from=0,to=1;
  if(Math.abs(dy)>1e-4){float a=(highest+2-origin.getY()-near[1])/dy,b=(lowest-1-origin.getY()-near[1])/dy;from=Math.max(0,Math.min(a,b));to=Math.min(1,Math.max(a,b));}
  if(to<=from)return null;
  int steps=(int)Math.min(20000,length*(to-from)/.05);
  for(int i=0;i<=steps;i++){float t=from+(to-from)*i/(float)Math.max(1,steps);
   int x=Mth.floor(near[0]+dx*t)+origin.getX(),y=Mth.floor(near[1]+dy*t)+origin.getY(),z=Mth.floor(near[2]+dz*t)+origin.getZ();
   if(y<lowest-1||y>highest+2)continue;
   if(seen(x,y,z)>0)return new BlockPos(x,y,z);}
  return null;
 }
 /** Emits every visible face of the view into the buffer, relative to the settlement centre. */
 void mesh(BufferBuilder b,BlockPos o,boolean grid){
  quads=0;blocks=0;lowest=Integer.MAX_VALUE;highest=Integer.MIN_VALUE;surface.clear();
  var level=Minecraft.getInstance().level;
  for(var e:columns.entrySet()){
   long key=e.getKey();if(isolate!=Long.MIN_VALUE&&key!=isolate)continue;
   var cp=new ChunkPos(key);var cols=e.getValue();var tops=surfaceOf(key,cols);surface.put(key,tops);
   for(int cell=0;cell<256;cell++){var col=cols[cell];if(col==null)continue;
    int wx=cp.getMinBlockX()+cell/16,wz=cp.getMinBlockZ()+cell%16;
    lowest=Math.min(lowest,col.bottom());highest=Math.max(highest,col.top);
    for(int i=0;i<col.states.length;i++){int y=col.top-i;int id=seen(wx,y,wz);if(id<=0)continue;
     if(buried(wx,y,wz))continue;
     blocks++;emit(b,id,wx,y,wz,o,grid,tops[cell]==Integer.MIN_VALUE?y:tops[cell],level);}}
  }
  if(lowest==Integer.MAX_VALUE){lowest=0;highest=0;}
 }
 /** AD-067: blocks that are not in the world yet — a planned building seen «after» — drawn with their own models; faces between two planned
  *  full blocks are left out. Each entry is {x, y, z, state} in world coordinates. */
 void models(BufferBuilder b,List<int[]> planned,BlockPos o){
  var level=Minecraft.getInstance().level;var solid=new HashSet<Long>();
  for(var p:planned)if((flags(p[3])&OCCLUDES)!=0)solid.add(BlockPos.asLong(p[0],p[1],p[2]));
  for(var p:planned){
   int id=p[3];if(id<=0)continue;int f=flags(id);var state=Block.stateById(id);
   float px=p[0]-o.getX(),py=p[1]-o.getY(),pz=p[2]-o.getZ();
   if((f&MODEL)==0)continue;
   var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);long seed=Mth.getSeed(p[0],p[1],p[2]);
   for(var dir:DIRECTIONS){
    if(solid.contains(BlockPos.asLong(p[0]+dir.getStepX(),p[1]+dir.getStepY(),p[2]+dir.getStepZ())))continue;
    random.setSeed(seed);for(var q:model.getQuads(state,dir,random,ModelData.EMPTY,null))quad(b,q,state,px,py,pz,p[0],p[1],p[2],1F,false,level);}
   random.setSeed(seed);for(var q:model.getQuads(state,null,random,ModelData.EMPTY,null))quad(b,q,state,px,py,pz,p[0],p[1],p[2],1F,false,level);
  }
 }
 private boolean occluding(int id){return id==SOLID||(id>0&&(flags(id)&OCCLUDES)!=0);}
 /** A block closed in on all six sides can never be seen: its model is not even looked at. */
 private boolean buried(int x,int y,int z){
  return occluding(seen(x+1,y,z))&&occluding(seen(x-1,y,z))&&occluding(seen(x,y+1,z))&&occluding(seen(x,y-1,z))&&occluding(seen(x,y,z+1))&&occluding(seen(x,y,z-1));
 }
 private void emit(BufferBuilder b,int id,int x,int y,int z,BlockPos o,boolean grid,int surfaceTop,ClientLevel level){
  int f=flags(id);var state=Block.stateById(id);
  float px=x-o.getX(),py=y-o.getY(),pz=z-o.getZ();
  // Depth cue: blocks seen in a cliff or at the edge of the survey darken the deeper they lie, so strata read as depth.
  float depth=Math.max(.55F,1-(surfaceTop-y)*.018F);
  if((f&MODEL)!=0){
   var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);long seed=Mth.getSeed(x,y,z);
   for(var dir:DIRECTIONS){
    if(occluding(seen(x+dir.getStepX(),y+dir.getStepY(),z+dir.getStepZ())))continue;
    random.setSeed(seed);
    for(var q:model.getQuads(state,dir,random,ModelData.EMPTY,null))quad(b,q,state,px,py,pz,x,y,z,depth,grid&&(f&LEAVES)==0,level);
   }
   random.setSeed(seed);
   for(var q:model.getQuads(state,null,random,ModelData.EMPTY,null))quad(b,q,state,px,py,pz,x,y,z,depth,false,level);
  }else if((f&BOX)!=0){
   // Chests, beds and the like are drawn by their block entity in the world; on the map they are a small cube of their own material.
   var sprite=Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
   for(var dir:DIRECTIONS)if(!occluding(seen(x+dir.getStepX(),y+dir.getStepY(),z+dir.getStepZ())))face(b,dir,px,py,pz,.08F,.86F,sprite,0xFFFFFF,SHADE[dir.get3DDataValue()]*depth);
  }
  if((f&FLUID)!=0)fluid(b,state,x,y,z,px,py,pz,depth,level);
 }
 private void quad(BufferBuilder b,BakedQuad q,BlockState state,float px,float py,float pz,int x,int y,int z,float depth,boolean grid,ClientLevel level){
  int[] v=q.getVertices();int stride=v.length/4;
  var dir=q.getDirection();
  float shade=(q.isShade()&&dir!=null?SHADE[dir.get3DDataValue()]:1F)*depth;
  int rgb=q.isTinted()?tint(state,q.getTintIndex(),x,y,z,level):0xFFFFFF;
  for(int i=0;i<4;i++){xyz[i*3]=Float.intBitsToFloat(v[i*stride]);xyz[i*3+1]=Float.intBitsToFloat(v[i*stride+1]);xyz[i*3+2]=Float.intBitsToFloat(v[i*stride+2]);
   uv[i*2]=Float.intBitsToFloat(v[i*stride+4]);uv[i*2+1]=Float.intBitsToFloat(v[i*stride+5]);}
  // Every full face keeps its own edge: a dark face under a slightly inset textured one, so each block of a flat field is still told apart.
  if(grid&&dir!=null&&full(dir)){put(b,px,py,pz,rgb,shade*.45F);inset(dir);}
  put(b,px,py,pz,rgb,shade);
 }
 private boolean full(Direction dir){
  float minX=9,maxX=-9,minY=9,maxY=-9,minZ=9,maxZ=-9;
  for(int i=0;i<4;i++){minX=Math.min(minX,xyz[i*3]);maxX=Math.max(maxX,xyz[i*3]);minY=Math.min(minY,xyz[i*3+1]);maxY=Math.max(maxY,xyz[i*3+1]);minZ=Math.min(minZ,xyz[i*3+2]);maxZ=Math.max(maxZ,xyz[i*3+2]);}
  boolean xs=minX<.001F&&maxX>.999F,ys=minY<.001F&&maxY>.999F,zs=minZ<.001F&&maxZ>.999F;
  return switch(dir.getAxis()){case X->ys&&zs;case Y->xs&&zs;case Z->xs&&ys;};
 }
 private void inset(Direction dir){
  float cx=0,cy=0,cz=0,cu=0,cv=0;
  for(int i=0;i<4;i++){cx+=xyz[i*3]/4;cy+=xyz[i*3+1]/4;cz+=xyz[i*3+2]/4;cu+=uv[i*2]/4;cv+=uv[i*2+1]/4;}
  final float k=.9F,lift=.006F;
  for(int i=0;i<4;i++){
   xyz[i*3]=cx+(xyz[i*3]-cx)*k+dir.getStepX()*lift;xyz[i*3+1]=cy+(xyz[i*3+1]-cy)*k+dir.getStepY()*lift;xyz[i*3+2]=cz+(xyz[i*3+2]-cz)*k+dir.getStepZ()*lift;
   uv[i*2]=cu+(uv[i*2]-cu)*k;uv[i*2+1]=cv+(uv[i*2+1]-cv)*k;}
 }
 private void put(BufferBuilder b,float px,float py,float pz,int rgb,float shade){
  int r=Math.min(255,(int)(((rgb>>16)&255)*shade)),g=Math.min(255,(int)(((rgb>>8)&255)*shade)),bl=Math.min(255,(int)((rgb&255)*shade));
  for(int i=0;i<4;i++)b.vertex(px+xyz[i*3],py+xyz[i*3+1],pz+xyz[i*3+2]).uv(uv[i*2],uv[i*2+1]).color(r,g,bl,255).endVertex();
  quads++;
 }
 private int tint(BlockState s,int index,int x,int y,int z,ClientLevel level){
  try{int c=Minecraft.getInstance().getBlockColors().getColor(s,level,level==null?null:cursor.set(x,y,z),index);return c==-1?0xFFFFFF:c&0xFFFFFF;}
  catch(RuntimeException ex){return 0xFFFFFF;}
 }
 private void fluid(BufferBuilder b,BlockState s,int x,int y,int z,float px,float py,float pz,float depth,ClientLevel level){
  var fs=s.getFluidState();var ext=IClientFluidTypeExtensions.of(fs);
  TextureAtlasSprite sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
  int rgb;try{rgb=(level==null?ext.getTintColor():ext.getTintColor(fs,level,cursor.set(x,y,z)))&0xFFFFFF;}catch(RuntimeException ex){rgb=ext.getTintColor()&0xFFFFFF;}
  int above=seen(x,y+1,z);boolean covered=above>0&&Block.stateById(above).getFluidState().getType().isSame(fs.getType());
  float h=covered?1F:.875F;
  for(var dir:DIRECTIONS){int n=seen(x+dir.getStepX(),y+dir.getStepY(),z+dir.getStepZ());
   if(occluding(n))continue;
   if(n>0&&(flags(n)&FLUID)!=0&&Block.stateById(n).getFluidState().getType().isSame(fs.getType()))continue;
   face(b,dir,px,py,pz,0,h,sprite,rgb,SHADE[dir.get3DDataValue()]*depth);}
 }
 /** One face of an axis-aligned box inset from the cell by the given margin and topped at the given height. */
 private void face(BufferBuilder b,Direction dir,float px,float py,float pz,float m,float h,TextureAtlasSprite sprite,int rgb,float shade){
  float a=m,z=1-m;
  float[] p=switch(dir){
   case UP->new float[]{a,h,a, a,h,z, z,h,z, z,h,a};
   case DOWN->new float[]{a,0,a, z,0,a, z,0,z, a,0,z};
   case NORTH->new float[]{a,0,a, a,h,a, z,h,a, z,0,a};
   case SOUTH->new float[]{a,0,z, z,0,z, z,h,z, a,h,z};
   case WEST->new float[]{a,0,a, a,0,z, a,h,z, a,h,a};
   case EAST->new float[]{z,0,a, z,h,a, z,h,z, z,0,z};};
  System.arraycopy(p,0,xyz,0,12);
  float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1();
  uv[0]=u0;uv[1]=v1;uv[2]=u0;uv[3]=v0;uv[4]=u1;uv[5]=v0;uv[6]=u1;uv[7]=v1;
  put(b,px,py,pz,rgb,shade);
 }
}

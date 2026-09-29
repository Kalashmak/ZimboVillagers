package org.villageastra.world;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.*;
import org.villageastra.domain.FramedWindows;
/** AD-142 (owner 2026-09-23): a glass pane in a wooden frame, one block per wood. To building rules it is a wall cell: a full collision and
 *  selection cube with sturdy faces (a door jamb, a torch, a block above stand on it). To light and rendering it is glass: light and skylight
 *  pass through, it casts no ambient-occlusion shade, and it occludes only its frame — the outer sides are wood from edge to edge, so the
 *  neighbours there are culled, while the faces behind the pane stay drawn. AXIS is the line of the wall it stands in (X: the pane faces
 *  north and south). It has the hardness, sound and colour of its wood's planks and drops itself.
 *  <p>Joined windows (owner 2026-09-24): windows of the same wood and axis side by side in one plane read as one big window — the frame bar
 *  between them is glass. UP, DOWN, LEFT, RIGHT say which sides join (LEFT: the negative end of the axis, west on X and north on Z; RIGHT:
 *  the positive end); a corner (UP_LEFT, ...) is glass only when both its sides and the window across the corner join too, so the outline of
 *  any group — a row, a 2x2, an L — stays closed. Designs carry the joins ({@link #join}); in the world they follow the neighbours. */
public final class FramedWindowBlock extends Block {
 public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.HORIZONTAL_AXIS;
 public static final BooleanProperty UP=BlockStateProperties.UP,DOWN=BlockStateProperties.DOWN,LEFT=BooleanProperty.create("left"),RIGHT=BooleanProperty.create("right");
 public static final BooleanProperty UP_LEFT=BooleanProperty.create("up_left"),UP_RIGHT=BooleanProperty.create("up_right"),
  DOWN_LEFT=BooleanProperty.create("down_left"),DOWN_RIGHT=BooleanProperty.create("down_right");
 /** Width of the frame round the pane, in pixels. */
 public static final int FRAME=2;
 /** The occlusion of every state: the frame pieces it shows, full depth (index: axis bit, then the eight joins). */
 private static final VoxelShape[] OCCLUSION=new VoxelShape[512];
 static{for(int i=0;i<512;i++)OCCLUSION[i]=frame(i);}
 private final String wood;
 public FramedWindowBlock(String wood){
  super(BlockBehaviour.Properties.copy(BuiltInRegistries.BLOCK.get(new ResourceLocation(FramedWindows.planks(wood))))
   .isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false).isValidSpawn((s,l,p,type)->false));
  this.wood=wood;registerDefaultState(stateDefinition.any().setValue(AXIS,Direction.Axis.X).setValue(UP,false).setValue(DOWN,false).setValue(LEFT,false).setValue(RIGHT,false)
   .setValue(UP_LEFT,false).setValue(UP_RIGHT,false).setValue(DOWN_LEFT,false).setValue(DOWN_RIGHT,false));
 }
 public String wood(){return wood;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(AXIS,UP,DOWN,LEFT,RIGHT,UP_LEFT,UP_RIGHT,DOWN_LEFT,DOWN_RIGHT);}
 /** The LEFT side of a window along this axis: west on X, north on Z. */
 public static Direction left(Direction.Axis axis){return axis==Direction.Axis.X?Direction.WEST:Direction.NORTH;}
 /** A window beside this one that joins it: the same block (so the same wood — colours never mix) along the same axis. */
 public static boolean joins(BlockState s,BlockState other){return other.getBlock()==s.getBlock()&&other.getValue(AXIS)==s.getValue(AXIS);}
 /** The same window for building rules: the same block along the same axis, whatever its joins (a repair or a level does not re-lay it). */
 public static boolean sameWindow(BlockState a,BlockState b){return a.getBlock() instanceof FramedWindowBlock&&joins(a,b);}
 /** The window's joins read from its neighbours in the plane. */
 public static BlockState connect(BlockState s,BlockPos p,Function<BlockPos,BlockState> at){
  var l=left(s.getValue(AXIS));var r=l.getOpposite();
  boolean up=joins(s,at.apply(p.above())),down=joins(s,at.apply(p.below())),left=joins(s,at.apply(p.relative(l))),right=joins(s,at.apply(p.relative(r)));
  return s.setValue(UP,up).setValue(DOWN,down).setValue(LEFT,left).setValue(RIGHT,right)
   .setValue(UP_LEFT,up&&left&&joins(s,at.apply(p.above().relative(l)))).setValue(UP_RIGHT,up&&right&&joins(s,at.apply(p.above().relative(r))))
   .setValue(DOWN_LEFT,down&&left&&joins(s,at.apply(p.below().relative(l)))).setValue(DOWN_RIGHT,down&&right&&joins(s,at.apply(p.below().relative(r))));
 }
 /** Joins every framed window of a design to the windows beside it in the design: a design is laid without the neighbour updates that join
  *  windows in the world (world generation, flags 2/18), so it carries its joins itself. Returns the same map. */
 public static <M extends Map<BlockPos,BlockState>> M join(M design){
  var air=Blocks.AIR.defaultBlockState();
  for(var cell:design.entrySet())if(cell.getValue().getBlock() instanceof FramedWindowBlock)cell.setValue(connect(cell.getValue(),cell.getKey(),p->design.getOrDefault(p,air)));
  return design;
 }
 /** Set into a gap of a wall, it follows the wall: the axis whose two neighbours are sturdy and the other's are not. Anywhere else the pane
  *  faces the player who places it. It joins the windows beside it. */
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){
  var l=c.getLevel();var p=c.getClickedPos();
  boolean x=sturdy(l,p,Direction.EAST)&&sturdy(l,p,Direction.WEST),z=sturdy(l,p,Direction.NORTH)&&sturdy(l,p,Direction.SOUTH);
  var axis=x!=z?(x?Direction.Axis.X:Direction.Axis.Z):c.getHorizontalDirection().getClockWise().getAxis();
  return connect(defaultBlockState().setValue(AXIS,axis),p,l::getBlockState);
 }
 private static boolean sturdy(BlockGetter l,BlockPos p,Direction d){var n=p.relative(d);return l.getBlockState(n).isFaceSturdy(l,n,d.getOpposite());}
 @Override public BlockState updateShape(BlockState s,Direction d,BlockState other,LevelAccessor l,BlockPos p,BlockPos o){
  return d.getAxis()==Direction.Axis.Y||d.getAxis()==s.getValue(AXIS)?connect(s,p,l::getBlockState):s;
 }
 /** A corner looks across the diagonal, which sends no shape update: a window set or taken away tells the four windows across its corners. */
 @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){
  super.onPlace(s,l,p,old,moving);if(!old.is(s.getBlock())||old.getValue(AXIS)!=s.getValue(AXIS))corners(l,p,s.getValue(AXIS));
 }
 @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moving){
  super.onRemove(s,l,p,now,moving);if(!now.is(s.getBlock())||now.getValue(AXIS)!=s.getValue(AXIS))corners(l,p,s.getValue(AXIS));
 }
 private static void corners(Level l,BlockPos p,Direction.Axis axis){
  var side=left(axis);
  for(var h:new Direction[]{side,side.getOpposite()})for(var v:new Direction[]{Direction.UP,Direction.DOWN}){var q=p.relative(h).relative(v);var s=l.getBlockState(q);
   if(s.getBlock() instanceof FramedWindowBlock){var joined=connect(s,q,l::getBlockState);if(joined!=s)l.setBlock(q,joined,Block.UPDATE_CLIENTS);}}
 }
 @Override public BlockState rotate(BlockState s,Rotation r){return turn(s,r::rotate);}
 @Override public BlockState mirror(BlockState s,Mirror m){return turn(s,m::mirror);}
 /** The window turned or mirrored: its axis follows its LEFT side, and when that side comes to face the positive end, left and right swap. */
 private static BlockState turn(BlockState s,UnaryOperator<Direction> f){
  var l=f.apply(left(s.getValue(AXIS)));var out=s.setValue(AXIS,l.getAxis());
  if(l.getAxisDirection()==Direction.AxisDirection.NEGATIVE)return out;
  return out.setValue(LEFT,s.getValue(RIGHT)).setValue(RIGHT,s.getValue(LEFT)).setValue(UP_LEFT,s.getValue(UP_RIGHT)).setValue(UP_RIGHT,s.getValue(UP_LEFT))
   .setValue(DOWN_LEFT,s.getValue(DOWN_RIGHT)).setValue(DOWN_RIGHT,s.getValue(DOWN_LEFT));
 }
 private static int index(BlockState s){
  return (s.getValue(AXIS)==Direction.Axis.Z?1:0)|(s.getValue(UP)?2:0)|(s.getValue(DOWN)?4:0)|(s.getValue(LEFT)?8:0)|(s.getValue(RIGHT)?16:0)
   |(s.getValue(UP_LEFT)?32:0)|(s.getValue(UP_RIGHT)?64:0)|(s.getValue(DOWN_LEFT)?128:0)|(s.getValue(DOWN_RIGHT)?256:0);
 }
 /** The frame of a state (index bits as {@link #index}): the bars of the sides that do not join and the corners that are not glass, each
  *  through the whole depth. The model draws the same pieces (tools/generate_resources.py framed_windows). */
 private static VoxelShape frame(int i){
  boolean z=(i&1)!=0,up=(i&2)!=0,down=(i&4)!=0,left=(i&8)!=0,right=(i&16)!=0;int f=FRAME,e=16-FRAME;
  var parts=new java.util.ArrayList<int[]>();// {from along, from y, to along, to y}; along runs from LEFT to RIGHT
  if(!left)parts.add(new int[]{0,f,f,e});if(!right)parts.add(new int[]{e,f,16,e});if(!down)parts.add(new int[]{f,0,e,f});if(!up)parts.add(new int[]{f,e,e,16});
  if((i&32)==0)parts.add(new int[]{0,e,f,16});if((i&64)==0)parts.add(new int[]{e,e,16,16});if((i&128)==0)parts.add(new int[]{0,0,f,f});if((i&256)==0)parts.add(new int[]{e,0,16,f});
  VoxelShape out=Shapes.empty();
  for(var b:parts)out=Shapes.or(out,z?Block.box(0,b[1],b[0],16,b[3],b[2]):Block.box(b[0],b[1],0,b[2],b[3],16));
  return out.optimize();
 }
 /** Only the frame occludes: the outer sides are whole where they do not join, the two faces of the pane are a ring or less. */
 @Override public VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p){return OCCLUSION[index(s)];}
 @Override public boolean propagatesSkylightDown(BlockState s,BlockGetter l,BlockPos p){return true;}
 @Override public int getLightBlock(BlockState s,BlockGetter l,BlockPos p){return 0;}
 @Override public float getShadeBrightness(BlockState s,BlockGetter l,BlockPos p){return 1.0F;}
}

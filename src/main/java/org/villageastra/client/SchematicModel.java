package org.villageastra.client;
import java.util.*;
import java.util.function.BiPredicate;
/** AD-126: pure logic of the construction schematic — what each cell of the builder's queue is, which Y layers changed since the last
 *  snapshot (only those are baked again), which finished blocks fade out, and how opaque a layer is in each layer mode.
 *  No Minecraft classes: positions are packed {@code BlockPos} longs, block states are string keys. */
public final class SchematicModel {
 public enum Kind{PLACE,REMOVE,CONFLICT,SCAFFOLD,REPLACE}
 public enum Mode{ALL,FOCUS,BELOW;public Mode next(){return values()[(ordinal()+1)%3];}}
 public static final int NONE=Integer.MIN_VALUE,MAX_FADES=64,FADE_TICKS=16;
 public static final float BASE=.38f,FOCUS_ALPHA=.60f;
 /** One operation of the queue in view. {@code occupied}: the client world holds a solid, non-replaceable block there now (for a
  *  replacement also: the block to be replaced still stands), so the ghost would be hidden inside it. */
 public record Cell(long pos,int op,String before,String after,boolean beforeAir,boolean afterAir,boolean beforeReplaceable,boolean beforeScaffold,boolean afterScaffold,boolean blocked,boolean occupied){
  /** C2 order: a foreign block first, then scaffolds (taking one down is no demolition), then removal, replacement, placement. */
  public Kind kind(){
   if(blocked)return Kind.CONFLICT;
   if(afterScaffold||beforeScaffold)return Kind.SCAFFOLD;
   if(afterAir&&!beforeAir)return Kind.REMOVE;
   if(!beforeAir&&!beforeReplaceable&&!afterAir)return Kind.REPLACE;
   return Kind.PLACE;
  }
  public int y(){return SchematicModel.y(pos);}
  /** Whether this cell may carry a block model at all (its visibility still depends on {@link #occupied}). */
  boolean modelled(){var k=kind();return (k==Kind.PLACE||k==Kind.REPLACE)&&!afterAir;}
 }
 public record Fade(long pos,String state,long start){}
 public record Diff(Set<Integer> dirtyLayers,List<Fade> fades,boolean reset){public boolean isEmpty(){return dirtyLayers.isEmpty()&&fades.isEmpty()&&!reset;}}
 private record Key(long pos,int op){}

 private String project="";private int total=-1,index=-1;private long target=Long.MIN_VALUE;
 private Map<Key,Cell> cells=new LinkedHashMap<>();
 private Map<Integer,Set<String>> signatures=new HashMap<>();
 private Map<Integer,List<Cell>> models=new HashMap<>();
 private final ArrayDeque<Fade> fades=new ArrayDeque<>();
 private int minX,minY,minZ,maxX,maxY,maxZ;

 public static int x(long p){return (int)(p>>38);}
 public static int y(long p){return (int)(p<<52>>52);}
 public static int z(long p){return (int)(p<<26>>38);}
 public static long pack(int x,int y,int z){return ((long)x&0x3FFFFFFL)<<38|((long)z&0x3FFFFFFL)<<12|(long)y&0xFFFL;}
 /** C1: the mayor's survey writes its cells over a project view without taking the queue fields away — it has no target and no ops. */
 public static long targetOf(boolean survey,boolean present,long raw){return survey||!present?Long.MIN_VALUE:raw;}
 public static int opOf(boolean survey,boolean present,int raw){return survey||!present?-1:raw;}

 /** Takes a new snapshot of the same or another project. {@code worldHolds(pos,state)}: the client world holds that state there now. */
 public Diff apply(String projectId,List<Cell> next,int index,long target,int total,long now,BiPredicate<Long,String> worldHolds){
  // C7: a renumbered queue (another total, or the pointer going back) is a new project, so (pos,op) keys cannot fake finished work.
  boolean reset=!projectId.equals(project)||total!=this.total||index<this.index;
  var map=new LinkedHashMap<Key,Cell>();for(var c:next)map.put(new Key(c.pos(),c.op()),c);
  var fresh=new ArrayList<Fade>();
  if(reset)fades.clear();
  else{var last=new LinkedHashMap<Long,Cell>();
   for(var e:cells.entrySet())if(!map.containsKey(e.getKey())){var c=e.getValue();
    // Finished (the pointer passed it, or the world holds its block) — not merely out of sight of the viewer.
    if(c.kind()!=Kind.SCAFFOLD&&(c.op()>=0&&c.op()<index||worldHolds.test(c.pos(),c.after())))last.merge(c.pos(),c,(a,b)->b.op()>=a.op()?b:a);}
   for(var c:last.values()){var f=new Fade(c.pos(),c.afterAir()?"":c.after(),now);fresh.add(f);fades.addLast(f);while(fades.size()>MAX_FADES)fades.removeFirst();}
  }
  // C3: one model per cell — the last placing operation of the queue there.
  var byPos=new LinkedHashMap<Long,Cell>();for(var c:map.values())if(c.modelled())byPos.merge(c.pos(),c,(a,b)->b.op()>=a.op()?b:a);
  var nextModels=new HashMap<Integer,List<Cell>>();for(var c:byPos.values())if(!c.occupied())nextModels.computeIfAbsent(c.y(),k->new ArrayList<>()).add(c);
  var nextSigs=new HashMap<Integer,Set<String>>();
  for(var c:map.values()){boolean model=byPos.get(c.pos())==c&&!c.occupied();nextSigs.computeIfAbsent(c.y(),k->new HashSet<>()).add(c.pos()+"|"+c.kind()+"|"+c.after()+"|"+c.before()+"|"+model);}
  var dirty=new TreeSet<Integer>();
  if(reset){dirty.addAll(signatures.keySet());dirty.addAll(nextSigs.keySet());}
  else{var all=new HashSet<>(signatures.keySet());all.addAll(nextSigs.keySet());for(int y:all)if(!Objects.equals(signatures.get(y),nextSigs.get(y)))dirty.add(y);}
  project=projectId;this.total=total;this.index=index;this.target=target;cells=map;signatures=nextSigs;models=nextModels;
  if(map.isEmpty()){minX=minY=minZ=0;maxX=maxY=maxZ=-1;}
  else{minX=minY=minZ=Integer.MAX_VALUE;maxX=maxY=maxZ=Integer.MIN_VALUE;for(var c:map.values()){long p=c.pos();minX=Math.min(minX,x(p));minY=Math.min(minY,y(p));minZ=Math.min(minZ,z(p));maxX=Math.max(maxX,x(p));maxY=Math.max(maxY,y(p));maxZ=Math.max(maxZ,z(p));}}
  return new Diff(dirty,fresh,reset);
 }
 /** Forgets everything (no project in view, another world). */
 public void clear(){project="";total=-1;index=-1;target=Long.MIN_VALUE;cells=new LinkedHashMap<>();signatures=new HashMap<>();models=new HashMap<>();fades.clear();maxX=maxY=maxZ=-1;minX=minY=minZ=0;}
 public boolean empty(){return cells.isEmpty();}
 public String project(){return project;}
 public int index(){return index;}
 public boolean hasTarget(){return target!=Long.MIN_VALUE;}
 public long target(){return target;}
 public int targetY(){return hasTarget()?y(target):NONE;}
 public Collection<Cell> cells(){return cells.values();}
 public Set<Integer> layers(){return signatures.keySet();}
 public List<Cell> models(int layer){return models.getOrDefault(layer,List.of());}
 public int modelCount(){int n=0;for(var l:models.values())n+=l.size();return n;}
 /** Cells of the next operations after the target (up to {@code count}), in queue order. */
 public List<Cell> following(int count){var out=new ArrayList<Cell>();if(!hasTarget())return out;int targetOp=-1;
  for(var c:cells.values())if(c.pos()==target){targetOp=c.op();break;}
  for(var c:cells.values())if(c.op()>targetOp&&out.size()<count)out.add(c);return out;}
 /** Fades still running at {@code now}; finished ones are dropped. */
 public List<Fade> fades(long now){fades.removeIf(f->now-f.start()>=FADE_TICKS);return List.copyOf(fades);}
 public int[] bounds(){return new int[]{minX,minY,minZ,maxX,maxY,maxZ};}
 /** The eye is inside the project's box grown by one block — the cut-away view applies. */
 public boolean inside(double ex,double ey,double ez){return !cells.isEmpty()&&ex>=minX-1&&ex<maxX+2&&ey>=minY-1&&ey<maxY+2&&ez>=minZ-1&&ez<maxZ+2;}
 /** Opacity of one layer; 0 = not drawn. {@code eyeY} is the block Y of the camera. */
 public static float alpha(int layerY,Mode mode,int targetY,int manualY,int eyeY,boolean cameraInside){
  if(cut(layerY,eyeY,cameraInside))return 0;
  if(manualY!=NONE){if(layerY>manualY)return 0;return layerY==manualY?FOCUS_ALPHA:.18f;}
  if(targetY==NONE||mode==Mode.ALL)return BASE;
  if(mode==Mode.FOCUS)return layerY==targetY?FOCUS_ALPHA:layerY<targetY?.12f:.20f;
  return layerY>targetY?0:layerY==targetY?FOCUS_ALPHA:BASE;
 }
 /** The cut-away: with the camera inside the site, layers more than one block above the eye are not drawn. */
 public static boolean cut(int layerY,int eyeY,boolean cameraInside){return cameraInside&&layerY>eyeY+1;}
 /** 1.2 Hz pulse between 0 and 1. */
 public static float pulse(long ticks,float partial){return (float)(.5+.5*Math.sin(2*Math.PI*1.2*(ticks+partial)/20.0));}
}

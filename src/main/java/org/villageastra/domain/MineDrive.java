package org.villageastra.domain;
import java.util.*;
/** AD-112: the one owner of a mine's cell sequence, for the miner, the machine, cargo recovery and protection. Pure: local cells relative
 *  to the mine's lot (x across, y up, z along the drive), no world. The stair goes down one step per row while its step is at most F, the
 *  floor step of the working level; then a gallery 1 wide and galleryHeight high runs east (x right+1..) and one west (x left-1..) from the
 *  landing at z 7+F, with a beam every beamEvery columns; when both are dug or blocked the mine stands at its floor. A raised level resumes
 *  the stair at F+1 (an unfinished gallery of the old floor is left, still claimed); a level dropped below the dug step digs nothing. */
public final class MineDrive {
 private MineDrive(){}
 public static final int EAST=0,WEST=1,DONE=2;
 public enum Stage{STAIR,EAST,WEST,FLOOR}
 /** Where the drive stands: stair step and cell of its row (top-down, across), then the gallery side and its column (run). */
 public record Drive(int step,int cell,int side,int run){
  public Drive{if(step<0||cell<0||side<EAST||side>DONE||run<0)throw new IllegalArgumentException("Invalid mine drive");}
  public static final Drive START=new Drive(0,0,EAST,0);
 }
 /** The mine's cross-section and gallery shape; the gallery numbers come from core_levels.json. */
 public record Shape(int width,int height,int descent,int galleryLength,int galleryHeight,int beamEvery){
  public Shape{if(width!=1&&width!=3||height<3||height>5||descent<0||galleryLength<1||galleryHeight<2||beamEvery<1)throw new IllegalArgumentException("Invalid mine shape");}
  public static Shape of(int width,int height,int descent){var m=CoreEffects.mine();return new Shape(width,height,descent,m.galleryLength(),galleryHeight(height),m.beamEvery());}
  /** AD-122 (owner): a gallery is as high as its stair — a drive of 5 (one block taller than before) drives 5-high galleries; an older one keeps the table's. */
  public static int galleryHeight(int stairHeight){return Math.max(CoreEffects.mine().galleryHeight(),stairHeight);}
  int left(){return width==1?3:2;}
  int right(){return width==1?3:4;}
 }
 public record Cell(int x,int y,int z){}
 /** Timber to set once a cell is dug: one log per cell (axis X, across the stair and along a gallery), each under its own journal id. */
 public record Beam(List<Cell> cells,List<String> ids){public int count(){return cells.size();}}
 /** The next cell to dig, where the worker stands to dig it, and the beam that follows it (null when none). FLOOR has no cell. */
 public record Target(Cell cell,Cell stand,Stage stage,Beam beam){public boolean floor(){return stage==Stage.FLOOR;}}
 /** Absolute floor Y of a working level. */
 public static int floorY(int level){return CoreEffects.mine().floorY(level);}
 /** F(L): the last stair step of a working level — down to its floor Y, at least minStepsPerLevel·L, and never within 2 blocks of the
  *  bottom of the world. mouthBottomY is the bottom of step 0 (lot Y minus the shaft's descent). */
 public static int floorStep(int level,int mouthBottomY,int minBuild){
  int want=Math.max(mouthBottomY-floorY(level),CoreEffects.mine().minStepsPerLevel()*Math.max(1,Math.min(CoreEffects.LEVELS,level)));
  return Math.max(0,Math.min(want,mouthBottomY-(minBuild+3)));
 }
 public static Stage stage(Drive d,int floorStep){
  if(d.step()<=floorStep)return Stage.STAIR;
  if(d.step()>floorStep+1||d.side()==DONE)return Stage.FLOOR;
  return d.side()==EAST?Stage.EAST:Stage.WEST;
 }
 public static Target next(Drive d,int floorStep,Shape s){
  var stage=stage(d,floorStep);
  if(stage==Stage.FLOOR)return new Target(null,null,stage,null);
  if(stage==Stage.STAIR){
   int w=s.width(),step=d.step(),c=d.cell()%(w*s.height());
   var cell=new Cell(w==1?3:2+c%w,s.height()-1-step-c/w-s.descent(),7+step);var stand=new Cell(3,1-step-s.descent(),6+step);
   Beam beam=null;
   if(c==w*s.height()-1&&(step+1)%s.beamEvery()==0){var cells=new ArrayList<Cell>();var ids=new ArrayList<String>();
    for(int i=0;i<w;i++){cells.add(new Cell(w==1?3:2+i,s.height()-1-step-s.descent(),7+step));ids.add(w==1?"beam":"beam/"+i);}beam=new Beam(List.copyOf(cells),List.copyOf(ids));}
   return new Target(cell,stand,stage,beam);
  }
  int floor=-floorStep-s.descent(),z=7+floorStep,r=d.run(),dx=d.side()==EAST?1:-1,x=d.side()==EAST?s.right()+1+r:s.left()-1-r;
  var cell=new Cell(x,floor+s.galleryHeight()-1-d.cell(),z);var stand=new Cell(x-dx,floor,z);
  Beam beam=null;
  if(d.cell()==s.galleryHeight()-1&&(r+1)%s.beamEvery()==0)beam=new Beam(List.of(new Cell(x,floor+s.galleryHeight()-1,z)),List.of("beam/"+(d.side()==EAST?"east":"west")+"/"+floorStep+"/"+r));
  return new Target(cell,stand,stage,beam);
 }
 /** AD-122 (owner): the stair blocks of a finished stair step — its bottom cells across the width, one per column, rising towards the mouth
  *  (local -z). Stair after stair runs half a block at a time from the mouth down to the tread the miner stands on for the next step, so the
  *  drive is walked up and down without a jump. Only a drive of 5 or more gets them (under a 4-high roof the stair would take the headroom). */
 public static List<Cell> stairs(int step,Shape s){
  if(s.height()<5||step<0)return List.of();
  var out=new ArrayList<Cell>();for(int i=0;i<s.width();i++)out.add(new Cell(s.width()==1?3:2+i,-step-s.descent(),7+step));return List.copyOf(out);
 }
 /** AD-122 (owner): a light of the drive — the cell it hangs in (the top cell, above the walking headroom), where the worker stands to
  *  hang it, and the wall a torch would be fixed to (local dx, dz from the cell). */
 public record Light(Cell cell,Cell stand,int wallX,int wallZ){}
 /** The light of a finished stair step: every `every` steps (the third of each run, never a beam step when every is 6), in the east column's top
  *  cell, hung from the roof, set from the tread of the next step. Only a drive of 5 or more. */
 public static Light stairLight(int step,Shape s,int every){
  if(s.height()<5||every<1||step%every!=Math.min(2,every-1))return null;
  int x=s.width()==1?3:4;return new Light(new Cell(x,s.height()-1-step-s.descent(),7+step),new Cell(3,-step-s.descent(),7+step),s.width()==1?0:1,s.width()==1?1:0);
 }
 /** The light of a finished gallery column: every `every` columns, in its top cell, set from where the column was dug; a torch goes on the far wall. */
 public static Light galleryLight(Drive d,int floorStep,Shape s,int every){
  var stage=stage(d,floorStep);if(s.height()<5||every<1||stage!=Stage.EAST&&stage!=Stage.WEST||d.run()%every!=every/2+1)return null;
  int floor=-floorStep-s.descent(),z=7+floorStep,dx=d.side()==EAST?1:-1,x=d.side()==EAST?s.right()+1+d.run():s.left()-1-d.run();
  return new Light(new Cell(x,floor+s.galleryHeight()-1,z),new Cell(x-dx,floor,z),0,1);
 }
 /** The drive after its target cell is dug (or found open). */
 public static Drive advance(Drive d,int floorStep,Shape s){
  var stage=stage(d,floorStep);
  if(stage==Stage.FLOOR)return d;
  if(stage==Stage.STAIR){int next=d.cell()+1;return next>=s.width()*s.height()?new Drive(d.step()+1,0,EAST,0):new Drive(d.step(),next,EAST,0);}
  if(d.cell()+1<s.galleryHeight())return new Drive(d.step(),d.cell()+1,d.side(),d.run());
  return d.run()+1>=s.galleryLength()?new Drive(d.step(),0,d.side()+1,0):new Drive(d.step(),0,d.side(),d.run()+1);
 }
 /** An unsafe gallery cell (or a fluid beside it) ends that gallery: the drive turns to the other side, or to the floor. The stair has no
  *  other side, so an unsafe stair cell leaves the drive as it is (the worker stops with unsafe_ground). */
 public static Drive blocked(Drive d,int floorStep){
  var stage=stage(d,floorStep);
  return stage==Stage.EAST||stage==Stage.WEST?new Drive(d.step(),0,d.side()+1,0):d;
 }
 /** The gallery claim once this drive's cell is dug (its column counted in), or null on the stair or at the floor. */
 public static MineArea.Gallery dug(Drive d,int floorStep){
  var stage=stage(d,floorStep);
  return stage==Stage.EAST||stage==Stage.WEST?new MineArea.Gallery(floorStep,d.side(),d.run()+1):null;
 }
}

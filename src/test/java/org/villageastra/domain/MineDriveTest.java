package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MineDriveTest {
 private static final int MIN_BUILD=-64;
 @Test void theFloorStepReachesEachLevelsFloorY(){
  int mouth=65;int[] floors={32,8,-8,-24,-40,-56};
  for(int level=1;level<=6;level++){assertEquals(floors[level-1],MineDrive.floorY(level));assertEquals(floors[level-1],mouth-MineDrive.floorStep(level,mouth,MIN_BUILD),"Level "+level+" floor");}
 }
 @Test void aDeepLotStillDigsEightStepsALevel(){
  assertEquals(8,MineDrive.floorStep(1,20,MIN_BUILD));assertEquals(16,MineDrive.floorStep(2,20,MIN_BUILD));assertEquals(28,MineDrive.floorStep(3,20,MIN_BUILD));
 }
 @Test void theFloorStepGrowsWithTheLevelAndStopsAboveTheBottomOfTheWorld(){
  for(int mouth=-60;mouth<=200;mouth+=7){int last=-1;
   for(int level=1;level<=6;level++){int f=MineDrive.floorStep(level,mouth,MIN_BUILD);assertTrue(f>=last,"Monotonic at mouth "+mouth);last=f;
    assertTrue(f==0||mouth-f>MIN_BUILD+2,"Never at the bottom of the world: mouth "+mouth+" level "+level);}}
  assertEquals(11,MineDrive.floorStep(2,-50,MIN_BUILD));assertEquals(11,MineDrive.floorStep(6,-50,MIN_BUILD));assertEquals(0,MineDrive.floorStep(3,-62,MIN_BUILD));
 }
 @Test void theStairThenEastThenWestThenTheFloor(){
  var shape=MineDrive.Shape.of(3,4,0);int f=1;var d=MineDrive.Drive.START;var seen=new java.util.ArrayList<MineDrive.Stage>();int stair=0,east=0,west=0,beams=0;
  for(int n=0;n<1000;n++){var t=MineDrive.next(d,f,shape);if(t.floor())break;
   if(seen.isEmpty()||seen.get(seen.size()-1)!=t.stage())seen.add(t.stage());
   switch(t.stage()){case STAIR->stair++;case EAST->east++;case WEST->west++;default->{}}
   if(t.stage()!=MineDrive.Stage.STAIR&&t.beam()!=null){beams++;assertEquals(1,t.beam().count());assertEquals(new MineDrive.Cell(t.cell().x(),t.cell().y()+3,t.cell().z()),t.beam().cells().get(0),"The gallery beam sits in the top cell of the column just dug");}
   d=MineDrive.advance(d,f,shape);}
  assertEquals(java.util.List.of(MineDrive.Stage.STAIR,MineDrive.Stage.EAST,MineDrive.Stage.WEST),seen);
  assertEquals(24,stair);assertEquals(24*4,east);assertEquals(24*4,west);assertEquals(12,beams);
  assertEquals(MineDrive.Stage.FLOOR,MineDrive.next(d,f,shape).stage());assertEquals(d,MineDrive.advance(d,f,shape),"The floor digs nothing");
  var first=MineDrive.next(new MineDrive.Drive(2,0,MineDrive.EAST,0),f,shape);
  assertEquals(new MineDrive.Cell(5,2,8),first.cell());assertEquals(new MineDrive.Cell(4,-1,8),first.stand());
  var lastEast=MineDrive.next(new MineDrive.Drive(2,3,MineDrive.EAST,23),f,shape);assertEquals(new MineDrive.Cell(28,-1,8),lastEast.cell());
  var firstWest=MineDrive.next(new MineDrive.Drive(2,3,MineDrive.WEST,0),f,shape);assertEquals(new MineDrive.Cell(1,-1,8),firstWest.cell());
  assertEquals(new MineDrive.Cell(-22,-1,8),MineDrive.next(new MineDrive.Drive(2,3,MineDrive.WEST,23),f,shape).cell());
 }
 @Test void theStairKeepsItsCellsAndBeams(){
  var shape=MineDrive.Shape.of(3,4,5);
  var t=MineDrive.next(new MineDrive.Drive(3,11,MineDrive.EAST,0),10,shape);
  assertEquals(new MineDrive.Cell(4,-8,10),t.cell());assertEquals(new MineDrive.Cell(3,-7,9),t.stand());
  assertEquals(3,t.beam().count());assertEquals(new MineDrive.Cell(2,-5,10),t.beam().cells().get(0));assertEquals("beam/2",t.beam().ids().get(2));
  assertNull(MineDrive.next(new MineDrive.Drive(3,10,MineDrive.EAST,0),10,shape).beam());assertNull(MineDrive.next(new MineDrive.Drive(4,11,MineDrive.EAST,0),10,shape).beam());
  assertEquals(new MineDrive.Drive(4,0,MineDrive.EAST,0),MineDrive.advance(new MineDrive.Drive(3,11,MineDrive.EAST,0),10,shape));
 }
 @Test void anUnsafeGalleryCellTurnsTheDrive(){
  int f=4;var east=new MineDrive.Drive(5,2,MineDrive.EAST,5);
  assertEquals(new MineDrive.Drive(5,0,MineDrive.WEST,0),MineDrive.blocked(east,f));
  assertEquals(MineDrive.Stage.FLOOR,MineDrive.stage(MineDrive.blocked(new MineDrive.Drive(5,1,MineDrive.WEST,3),f),f));
  var stair=new MineDrive.Drive(2,4,MineDrive.EAST,0);assertEquals(stair,MineDrive.blocked(stair,f),"An unsafe stair cell stops the drive where it is");
  assertEquals(new MineArea.Gallery(4,MineDrive.EAST,6),MineDrive.dug(east,f));assertNull(MineDrive.dug(stair,f));
 }
 @Test void aRaisedLevelResumesTheStairAndALoweredOneDigsNothing(){
  var shape=MineDrive.Shape.of(3,4,0);var d=new MineDrive.Drive(9,2,MineDrive.WEST,7);
  assertEquals(MineDrive.Stage.WEST,MineDrive.stage(d,8));
  var resumed=MineDrive.next(d,16,shape);assertEquals(MineDrive.Stage.STAIR,resumed.stage());assertEquals(16,resumed.cell().z(),"Step 9 of the stair, at F+1");
  assertEquals(new MineDrive.Drive(9,3,MineDrive.EAST,0),MineDrive.advance(d,16,shape),"The stair forgets the old gallery");
  assertEquals(MineDrive.Stage.FLOOR,MineDrive.stage(d,7),"A level below the dug step digs nothing");
 }
 @Test void aDugGalleryIsProtectedAndTheRockBeyondItIsNot(){
  var area=new MineArea(8,3,4,0).with(new MineArea.Gallery(8,MineDrive.EAST,6));
  assertTrue(area.contains(10,-8,15,0));assertTrue(area.contains(10,-5,15,0));assertTrue(area.contains(11,-8,15,0),"Its end wall");
  assertFalse(area.contains(12,-8,15,0));assertFalse(area.contains(10,-3,15,0));assertFalse(area.contains(10,-8,17,0));
  assertEquals(area,area.with(new MineArea.Gallery(8,MineDrive.EAST,3)),"A claim only grows");
  var s=new Settlement(java.util.UUID.randomUUID());var mine=new Settlement.Building(java.util.UUID.randomUUID(),"mine",0,0,0);s.addBuilding(mine);
  assertThrows(IllegalArgumentException.class,()->s.noteMine(mine.id(),new MineArea.Gallery(8,MineDrive.WEST,2)));
  s.noteMine(mine.id(),8,3,4,0);assertTrue(s.noteMine(mine.id(),new MineArea.Gallery(8,MineDrive.WEST,2)));assertFalse(s.noteMine(mine.id(),new MineArea.Gallery(8,MineDrive.WEST,1)));
  assertTrue(s.noteMine(mine.id(),9,3,4,0));assertEquals(1,s.mineAreas().get(mine.id()).galleries().size(),"A deeper stair keeps its galleries");
 }
}

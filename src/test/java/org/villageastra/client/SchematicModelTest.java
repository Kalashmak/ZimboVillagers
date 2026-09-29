package org.villageastra.client;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.villageastra.client.SchematicModel.*;
/** AD-126: the pure part of the construction schematic. */
class SchematicModelTest {
 private static final String AIR="air",GRASS="grass",STONE="stone",PLANKS="planks",SCAFF="scaffold",GOLD="gold";
 private static Cell cell(int x,int y,int z,int op,String before,String after){return cell(x,y,z,op,before,after,false,false);}
 private static Cell cell(int x,int y,int z,int op,String before,String after,boolean blocked,boolean occupied){
  return new Cell(pack(x,y,z),op,before,after,before.equals(AIR),after.equals(AIR),before.equals(AIR)||before.equals(GRASS),before.equals(SCAFF),after.equals(SCAFF),blocked,occupied);}
 private static List<Cell> house(int from){var out=new ArrayList<Cell>();int op=0;
  for(int y=0;y<4;y++)for(int x=0;x<3;x++){if(op>=from)out.add(cell(x,y,0,op,AIR,PLANKS));op++;}return out;}
 private static final java.util.function.BiPredicate<Long,String> NOTHING=(p,s)->false;

 @Test void fiveKindsInTheOrderOfC2(){
  assertEquals(Kind.CONFLICT,cell(0,0,0,0,AIR,PLANKS,true,false).kind());
  assertEquals(Kind.SCAFFOLD,cell(0,0,0,0,AIR,SCAFF).kind());
  assertEquals(Kind.SCAFFOLD,cell(0,0,0,0,SCAFF,AIR).kind(),"Taking a scaffold down is not a demolition (A07-VIS-001 red is the plan's removal)");
  assertEquals(Kind.REMOVE,cell(0,0,0,0,STONE,AIR).kind());
  assertEquals(Kind.REPLACE,cell(0,0,0,0,STONE,PLANKS).kind());
  assertEquals(Kind.PLACE,cell(0,0,0,0,AIR,PLANKS).kind());
  assertEquals(Kind.PLACE,cell(0,0,0,0,GRASS,PLANKS).kind(),"Grass on the plot is no false demolition");
 }
 @Test void oneModelPerCellFromTheLastPlacingOperation(){
  var m=new SchematicModel();
  m.apply("p",List.of(cell(1,0,1,3,GRASS,AIR),cell(1,0,1,4,AIR,STONE),cell(1,0,1,9,STONE,PLANKS),cell(2,0,1,5,AIR,STONE)),3,pack(1,0,1),20,0,NOTHING);
  var models=m.models(0);assertEquals(2,models.size());
  assertEquals(9,models.stream().filter(c->c.pos()==pack(1,0,1)).findFirst().orElseThrow().op(),"Three operations in one cell give one model, the last one");
 }
 @Test void occupiedCellsCarryNoModel(){
  var m=new SchematicModel();m.apply("p",List.of(cell(0,0,0,0,AIR,PLANKS,false,true),cell(1,0,0,1,AIR,PLANKS)),0,pack(0,0,0),2,0,NOTHING);
  assertEquals(1,m.models(0).size());
 }
 @Test void aFinishedStepFadesAndDirtiesOnlyItsLayer(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  var diff=m.apply("p",house(1),1,pack(1,0,0),12,5,NOTHING);
  assertEquals(1,diff.fades().size());assertEquals(pack(0,0,0),diff.fades().get(0).pos());
  assertTrue(diff.dirtyLayers().size()<=2&&diff.dirtyLayers().contains(0),"Only the changed layer is baked again: "+diff.dirtyLayers());
  assertEquals(1,m.fades(10).size());assertTrue(m.fades(5+FADE_TICKS).isEmpty(),"A fade lasts 16 ticks");
 }
 @Test void walkingAwayIsNoFade(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  var fewer=house(0).subList(0,6);var diff=m.apply("p",fewer,0,pack(0,0,0),12,5,NOTHING);
  assertTrue(diff.fades().isEmpty(),"Cells out of view with op >= index and the world not holding them do not fade");
  assertEquals(Set.of(2,3),diff.dirtyLayers());
 }
 @Test void worldHoldingTheBlockFadesEvenWithoutTheIndex(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  var rest=new ArrayList<>(house(0));rest.remove(5);
  var diff=m.apply("p",rest,0,pack(0,0,0),12,5,(p,s)->p==pack(2,1,0)&&s.equals(PLANKS));
  assertEquals(1,diff.fades().size());
 }
 @Test void theSameSnapshotAgainIsAnEmptyDiff(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  assertTrue(m.apply("p",house(0),0,pack(0,0,0),12,100,NOTHING).isEmpty());
 }
 @Test void anotherProjectDirtiesEverything(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  var diff=m.apply("q",house(0),0,pack(0,0,0),12,5,NOTHING);
  assertTrue(diff.reset());assertEquals(Set.of(0,1,2,3),diff.dirtyLayers());assertTrue(diff.fades().isEmpty());
 }
 @Test void aRenumberedQueueIsANewProject(){
  var m=new SchematicModel();m.apply("p",house(0),4,pack(1,1,0),12,0,NOTHING);
  var other=m.apply("p",house(0),2,pack(2,0,0),12,5,NOTHING);assertTrue(other.reset(),"Index going back");assertTrue(other.fades().isEmpty());
  var longer=m.apply("p",house(1),2,pack(2,0,0),14,6,NOTHING);assertTrue(longer.reset(),"Another total");assertTrue(longer.fades().isEmpty(),"No false fade from renumbered ops");
 }
 @Test void theSurveyHasNoTargetNoOps(){
  assertEquals(Long.MIN_VALUE,targetOf(true,true,pack(3,3,3)),"A survey written over a project view keeps no target");
  assertEquals(-1,opOf(true,true,7));assertEquals(-1,opOf(false,false,7));assertEquals(7,opOf(false,true,7));
  var m=new SchematicModel();m.apply("s/survey",List.of(cell(0,0,0,-1,AIR,PLANKS)),0,targetOf(true,true,pack(0,0,0)),0,0,NOTHING);
  assertFalse(m.hasTarget());assertEquals(NONE,m.targetY());
  var diff=m.apply("s/survey",List.of(),0,Long.MIN_VALUE,0,5,NOTHING);assertTrue(diff.fades().isEmpty(),"Moving the survey is no finished work");
 }
 @Test void layerModesManualLayerAndCutAway(){
  assertEquals(BASE,alpha(5,Mode.ALL,3,NONE,100,false));
  assertEquals(FOCUS_ALPHA,alpha(3,Mode.FOCUS,3,NONE,100,false));assertEquals(.12f,alpha(2,Mode.FOCUS,3,NONE,100,false));assertEquals(.20f,alpha(4,Mode.FOCUS,3,NONE,100,false));
  assertEquals(0f,alpha(4,Mode.BELOW,3,NONE,100,false));assertEquals(BASE,alpha(2,Mode.BELOW,3,NONE,100,false));assertEquals(FOCUS_ALPHA,alpha(3,Mode.BELOW,3,NONE,100,false));
  assertEquals(0f,alpha(6,Mode.ALL,3,5,100,false));assertEquals(FOCUS_ALPHA,alpha(5,Mode.FOCUS,3,5,100,false));assertTrue(alpha(4,Mode.ALL,3,5,100,false)>0);
  assertEquals(BASE,alpha(5,Mode.ALL,NONE,NONE,100,false),"Without a target every mode shows all layers");
  assertEquals(0f,alpha(8,Mode.ALL,3,NONE,6,true),"Inside the site, layers above eye+1 are cut away");assertEquals(BASE,alpha(7,Mode.ALL,3,NONE,6,true));assertEquals(BASE,alpha(8,Mode.ALL,3,NONE,6,false));
  float p=pulse(0,0);assertTrue(p>=0&&p<=1);assertNotEquals(pulse(0,0),pulse(4,0));
 }
 @Test void theCameraInsideTheSite(){
  var m=new SchematicModel();m.apply("p",house(0),0,pack(0,0,0),12,0,NOTHING);
  assertTrue(m.inside(1.5,1.6,.5));assertFalse(m.inside(10,1,10));
  assertArrayEquals(new int[]{0,0,0,2,3,0},m.bounds());
 }
 @Test void packingMatchesBlockPos(){
  long p=pack(-15,-61,-8);assertEquals(-15,x(p));assertEquals(-61,y(p));assertEquals(-8,z(p));
  assertEquals(net.minecraft.core.BlockPos.asLong(-15,-61,-8),p);
 }
}

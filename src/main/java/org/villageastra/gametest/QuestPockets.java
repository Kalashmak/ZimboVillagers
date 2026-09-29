package org.villageastra.gametest;
import java.util.*;
/** Where the quest GameTests look for a pocket of free ground for a quest site: the seven places beside the test's own village first,
 *  then further out on its own side. The village lots grew (layout v6 and later), and a batch shares one world, so the first places are often
 *  inside another test's lot; the pocket then goes further out instead of failing the test. */
final class QuestPockets {
 private QuestPockets(){}
 static List<int[]> offsets(int away){
  var out=new ArrayList<int[]>(List.of(new int[][]{{-away,2},{-away,16},{-away,-12},{-away-14,2},{-away-14,16},{-away,30},{-away-14,-12}}));
  var far=new ArrayList<int[]>();
  for(int dx=0;dx<=56;dx+=14)for(int dz=-40;dz<=58;dz+=14){var o=new int[]{-away-dx,dz+2};if(out.stream().noneMatch(x->x[0]==o[0]&&x[1]==o[1]))far.add(o);}
  far.sort(Comparator.comparingInt(o->o[0]*o[0]+(o[1]-2)*(o[1]-2)));out.addAll(far);return out;
 }
}

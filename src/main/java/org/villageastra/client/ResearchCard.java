package org.villageastra.client;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.villageastra.client.OfficeUi.Tone;
import org.villageastra.world.ResearchEffects;
/** AD-133: the selected research read at a glance. The office card shows a few short rows «было → стало»; this class makes them out of
 *  the very fact lines of {@link ResearchEffects} — the label, both numbers and the unit are the arguments of that same line, and every
 *  row keeps the whole line for its tooltip. Nothing is invented: a line whose shape no rule knows gets no row and stays in «подробнее». */
final class ResearchCard {
 private ResearchCard(){}
 private static final String FACT="research.villageastra.fact.";
 /** A row of the card: what changes, its old value (or none), the new value, the unit, and the whole fact line behind it.
  *  rank orders the rows: what the research opens first, then numbers that change, then single numbers, then plain facts. */
 record Row(Component label,String from,String to,Component unit,Tone tone,Component full,int rank){}
 private static final Map<String,List<Row>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 /** The rows of a node, ordered by rank; the panel shows as many as its column has room for. */
 static List<Row> rows(String id){return CACHE.computeIfAbsent(ResearchEffects.locale()+"/"+id,key->build(id));}
 private static List<Row> build(String id){
  var out=new ArrayList<Row>();for(var line:ResearchEffects.describe(id)){var row=row(line);if(row!=null)out.add(row);}
  out.sort(Comparator.comparingInt(Row::rank));return List.copyOf(out);
 }
 private static Component brief(String key,Object... args){return Component.translatable("research.villageastra.brief."+key,args);}
 private static String text(String key,Object... args){return brief(key,args).getString();}
 private static Component comp(Object[] a,int i){return i<a.length&&a[i] instanceof Component c?c:Component.literal(str(a,i));}
 private static String str(Object[] a,int i){return i<a.length&&a[i]!=null?(a[i] instanceof Component c?c.getString():String.valueOf(a[i])):"";}
 private static String times(Object[] a,int i){return "×"+str(a,i);}
 /** Green when the new number is the larger one, otherwise the neutral tone: the card never claims an improvement it cannot see. */
 private static Tone tone(String from,String to){
  try{if(from!=null&&Double.parseDouble(clean(to))>Double.parseDouble(clean(from)))return Tone.OK;}catch(NumberFormatException|NullPointerException ignored){}
  return Tone.INFO;
 }
 private static String clean(String s){return s.replace("×","").replace(',','.').trim();}
 private static Row num(Component label,String from,String to,Component unit,Component full,int rank){return new Row(label,from,to,unit,tone(from,to),full,rank);}
 /** The rank of the grey «Будет: ...» row (an INTERIM or PLANNED node): the panel always keeps it on the card. */
 static final int FUTURE=4;
 static boolean future(Row row){return row.rank()==FUTURE;}
 private static Row flag(String key,Component full,int rank){return new Row(brief(key),null,null,null,Tone.OK,full,rank);}
 /** One fact line to one row, by the key the line was built with; an unknown key gets no row. */
 private static Row row(Component line){
  if(!(line.getContents() instanceof TranslatableContents t)||!t.getKey().startsWith(FACT))return null;
  String k=t.getKey().substring(FACT.length());Object[] a=t.getArgs();
  return switch(k){
   case "unlock"->new Row(comp(a,0),null,text("level",str(a,1)),null,Tone.OK,line,0);
   case "housing.design"->new Row(brief("design"),null,str(a,0),null,Tone.OK,line,0);
   case "number"->num(comp(a,0),str(a,1),str(a,2),comp(a,3),line,1);
   case "beds"->num(brief("beds"),str(a,0),str(a,1),null,line,1);
   case "housing.capacity"->num(comp(a,0),str(a,2),str(a,3),Component.translatable("research.villageastra.fact.unit.residents"),line,1);
   case "housing.births"->num(brief("births"),str(a,2),str(a,3),brief("days"),line,1);
   case "knob.foundation"->num(brief("foundation"),str(a,0),str(a,1),brief("blocks"),line,1);
   case "knob.drive"->num(brief("drive"),str(a,0),str(a,1),brief("blocks"),line,1);
   case "knob.quarry"->num(brief("quarry"),str(a,0),str(a,1),brief("blocks"),line,1);
   case "surface"->num(comp(a,0),times(a,4),times(a,1),null,line,1);
   case "value"->num(comp(a,0),null,str(a,1),comp(a,2),line,2);
   case "surface_existing"->num(comp(a,0),null,times(a,1),null,line,2);
   case "knob.bone_meal"->num(brief("bone_meal"),null,str(a,0),Component.translatable("research.villageastra.fact.unit.items"),line,2);
   case "lighting"->num(brief("lighting"),null,str(a,0),brief("blocks"),line,2);
   case "queue"->num(brief("queue"),null,str(a,0),brief("topics"),line,2);
   case "trail.order"->num(brief("trail"),null,str(a,0),brief("blocks"),line,2);
   case "machinery.4","machinery.5","machinery.6"->num(brief("auto"),null,str(a,1),brief("ticks"),line,2);
   case "fences"->num(brief("fences"),null,str(a,0),brief("blocks"),line,3);
   case "trail.bridge"->num(brief("bridge"),null,str(a,0),brief("blocks"),line,3);
   case "trail.tunnel"->num(brief("tunnel"),null,str(a,0),brief("blocks"),line,3);
   case "trail.surface"->new Row(brief("trail_surface"),null,str(a,0),null,Tone.INFO,line,3);
   case "trail.caravan"->new Row(brief("caravan"),null,times(a,0),null,Tone.OK,line,3);
   case "width"->flag("width",line,3);
   case "walls"->flag("walls",line,3);
   case "towers"->flag("towers",line,3);
   case "road_maintenance"->flag("road_maintenance",line,3);
   // AD-136: the hall's own branch caps every building; engineering V opens level VI; engineering I/II teach the hall its crafts.
   case "hall_cap"->new Row(brief("hall_cap"),null,str(a,0),null,Tone.OK,line,0);
   case "vi_gate"->new Row(brief("vi_gate"),null,str(a,0),brief("topics"),Tone.OK,line,0);
   case "hall_crafts"->new Row(brief("hall_crafts"),str(a,2),str(a,1),brief("seconds"),Tone.OK,line,1);
   // §4.3: the owner's «Будет: ...» — grey, apart, never an arrow or a number that would read as working.
   case "future","planned"->new Row(line,null,null,null,Tone.OFF,line,FUTURE);
   case "prerequisite_only"->new Row(brief("opens_path"),null,null,null,Tone.OFF,line,5);
   default->null;
  };
 }
}

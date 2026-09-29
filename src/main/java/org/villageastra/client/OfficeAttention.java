package org.villageastra.client;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.Tone;
import org.villageastra.domain.ResearchCatalog;
/** What in the office needs the player now, worst first: the Overview lists it and the tabs carry it as badges.
 *  Pure client code over the snapshot; reads overview{} when the server sends it and falls back to what the snapshot already has. */
final class OfficeAttention {
 private OfficeAttention(){}
 /** One line of the Overview. count feeds the tab badge (0 draws a dot); section and focus say where a click leads. */
 record Need(Tone tone,ItemStack icon,Component text,Component value,int section,String focus,int count){}
 record Badge(int count,Tone tone){}
 private static Component t(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 static double foodDays(CompoundTag o){long need=o.getLong("need");return need<=0?-1:o.getLong("food")/(double)need;}
 static List<Need> needs(CompoundTag s){
  var out=new ArrayList<Need>();if(!s.hasUUID("village"))return out;
  boolean manage=s.getBoolean("canManage");var o=s.getCompound("overview");boolean hasOverview=s.contains("overview");
  // Danger first: people and walls.
  if(o.getBoolean("raid"))out.add(new Need(Tone.BAD,new ItemStack(Items.SKELETON_SKULL),t("need.raid"),Component.empty(),5,"",1));
  if(o.getBoolean("besieged"))out.add(new Need(Tone.BAD,new ItemStack(Items.TNT),t("need.siege"),Component.empty(),5,"",1));
  double days=hasOverview?foodDays(o):-1;
  if(o.getInt("missed")>0||o.getInt("hungry")>0)out.add(new Need(Tone.BAD,new ItemStack(Items.BREAD),t("overview.hunger",Math.max(o.getInt("missed"),o.getInt("hungry"))),days>=0?OfficeUi.days(days):Component.empty(),2,"",1));
  else if(days>=0&&days<1)out.add(new Need(Tone.BAD,new ItemStack(Items.BREAD),t("overview.food",OfficeUi.days(days)),Component.empty(),2,"",1));
  else if(days>=0&&days<2)out.add(new Need(Tone.WAIT,new ItemStack(Items.BREAD),t("overview.food",OfficeUi.days(days)),Component.empty(),2,"",1));
  // Construction: only when there is a project.
  boolean project=s.hasUUID("id")&&!s.getBoolean("survey");
  if(project){String stage=s.getBoolean("draft")?"draft":s.getString("stage");
   if(s.getBoolean("draft")){if(s.getBoolean("confirmable"))out.add(new Need(Tone.ACTION,new ItemStack(Items.PAPER),t("need.draft"),Component.empty(),0,"",1));}
   else{
    int builders=o.contains("builders")?o.getInt("builders"):s.contains("builders")?s.getInt("builders"):-1;
    if(!stage.equals("complete")&&builders==0)out.add(new Need(Tone.BAD,new ItemStack(Items.IRON_SHOVEL),t("need.no_builders"),Component.empty(),0,"",1));
    if(stage.equals("blocked"))out.add(new Need(Tone.BAD,new ItemStack(Items.BARRIER),t("need.blocked"),Component.empty(),0,"",1));
    if(stage.equals("pause"))out.add(new Need(Tone.WAIT,new ItemStack(Items.CLOCK),t("need.paused"),Component.empty(),0,"",1));
    ItemStack first=null,core=null;int kinds=0;for(var raw:s.getList("materials",Tag.TAG_COMPOUND)){var m=(CompoundTag)raw;if(m.getInt("missing")>0){kinds++;if(first==null)first=OfficeUi.icon(m.getString("item"));
     if(core==null&&(org.villageastra.domain.CoreCatalog.isCore(m.getString("item"))||org.villageastra.domain.CoreCatalog.isRing(m.getString("item"))))core=OfficeUi.icon(m.getString("item"));}}
    // AD-112: a missing core or ring is named, for no other item stands in for it; the other kinds follow as '+N'.
    if(core!=null)out.add(new Need(Tone.WAIT,core,t("need.core",core.getHoverName()),kinds>1?Component.literal("+"+(kinds-1)):Component.empty(),0,"",1));
    else if(kinds>0)out.add(new Need(Tone.WAIT,first,t("need.materials",kinds),Component.empty(),0,"",1));
    // AD-137 (addendum): the mayor's project has gained nothing for days — the one construction slot is held, say so.
    if(s.getLong("stalledDays")>0)out.add(new Need(Tone.BAD,first!=null?first:new ItemStack(Items.CLOCK),t("need.stalled",s.getLong("stalledDays")),Component.empty(),0,"",1));
   }}
  // Upgrades: one grouped line, only when the crew is free to take one.
  var up=s.getCompound("upgrades");int ready=0;String firstId="";
  for(var raw:up.getList("buildings",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;if(upgradable(r)){ready++;if(firstId.isEmpty()&&r.hasUUID("id"))firstId=r.getUUID("id").toString();}}
  if(ready>0&&!up.getBoolean("busy"))out.add(new Need(Tone.ACTION,new ItemStack(Items.EXPERIENCE_BOTTLE),t("need.upgrade",ready),Component.empty(),6,ready==1?firstId:"",ready));
  // Research: a target to choose while something can be studied.
  var research=s.getCompound("research");
  if(s.contains("research")&&research.getString("selected").isEmpty()&&researchAvailable(research)!=null)out.add(new Need(Tone.ACTION,new ItemStack(Items.ENCHANTED_BOOK),t("need.research"),Component.empty(),3,researchAvailable(research),0));
  // Elections: the countdown only concerns a candidate.
  var e=s.getCompound("election");
  if(e.getBoolean("candidate")&&e.getLong("remaining")<=2400)out.add(new Need(Tone.WAIT,new ItemStack(Items.GOLDEN_HELMET),t("need.election_soon",OfficeUi.duration(e.getLong("remaining"))),Component.empty(),1,"",1));
  if(e.getBoolean("safe")&&!e.getBoolean("candidate")&&!e.getBoolean("mayor")&&e.getLong("score")>=e.getLong("minimum")&&e.contains("minimum"))out.add(new Need(Tone.ACTION,new ItemStack(Items.GOLDEN_HELMET),t("need.can_run"),Component.empty(),1,"",0));
  // Quests: things to hand in, and people out there in danger.
  int handover=0;boolean danger=false;
  for(var raw:s.getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(q.getBoolean("mine")&&q.getBoolean("handover")&&q.getBoolean("taken"))handover++;for(var p:q.getList("people",Tag.TAG_COMPOUND))if(((CompoundTag)p).getInt("danger")>0)danger=true;}
  if(danger)out.add(new Need(Tone.BAD,new ItemStack(Items.PLAYER_HEAD),t("need.people_danger"),Component.empty(),4,"",1));
  if(handover>0)out.add(new Need(Tone.ACTION,new ItemStack(Items.WRITABLE_BOOK),t("need.quest_handover",handover),Component.empty(),4,"",handover));
  // War: an offer to answer, a campaign ready to strike.
  var war=s.getCompound("war");
  if(war.getBoolean("offered"))out.add(new Need(Tone.ACTION,new ItemStack(Items.GOLD_INGOT),t("need.buyout_offer"),Component.literal(String.valueOf(war.getLong("offerPrice"))),5,"",1));
  if(war.getString("state").equals("ready"))out.add(new Need(Tone.ACTION,new ItemStack(Items.IRON_SWORD),t("need.campaign_ready"),Component.empty(),5,"",1));
  // Workshops: nobody at work or no inputs, one line per kind of trouble.
  Map<String,List<CompoundTag>> trouble=new LinkedHashMap<>();
  for(var raw:s.getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;var st=c.getString("status");if(st.equals("no_worker")||st.equals("missing_input")||st.equals("blocked"))trouble.computeIfAbsent(st,k->new ArrayList<>()).add(c);
   // A short field only matters once the pantry runs low: early fields are always below their target.
   if(c.contains("feedsNow")&&c.getInt("feedsNow")<c.getInt("target")&&days>=0&&days<1)out.add(new Need(Tone.WAIT,new ItemStack(Items.WHEAT),t("need.farm_short",c.getInt("feedsNow"),c.getInt("target")),Component.empty(),2,id(c),1));}
  for(var en:trouble.entrySet()){var c=en.getValue().get(0);int n=en.getValue().size();Tone tone=en.getKey().equals("blocked")?Tone.BAD:Tone.WAIT;
   out.add(new Need(tone,OfficeUi.buildingIcon(c.getString("type")),t(en.getKey().equals("missing_input")?"need.missing_input":en.getKey().equals("blocked")?"need.stopped":"need.no_worker",Component.translatable("building.villageastra."+c.getString("type"))),n>1?Component.literal("×"+n):Component.empty(),2,id(c),n));}
  // A viewer cannot act: decisions become grey information.
  if(!manage)out.replaceAll(n->n.tone==Tone.ACTION?new Need(Tone.OFF,n.icon,n.text,n.value,n.section,n.focus,n.count):n);
  out.sort(Comparator.comparingInt((Need n)->-n.tone.rank()));
  return out;
 }
 private static String id(CompoundTag c){return c.hasUUID("id")?c.getUUID("id").toString():"";}
 /** Ready to order now: nothing refuses it, nothing is missing, and it is not at the top level. */
 static boolean upgradable(CompoundTag r){return r.getString("refusal").isEmpty()&&r.getInt("lack")==0&&r.getInt("kept")<r.getInt("max");}
 /** The first node that can be studied now, or null — by the server's own rule (AD-124: ResearchRules, the hall's next-level research one tier early). */
 static String researchAvailable(CompoundTag r){
  var done=new HashSet<String>();for(var x:r.getList("completed",Tag.TAG_STRING))done.add(x.getAsString());int hall=r.getInt("hall");var next=org.villageastra.world.BuildingTiers.hallResearch(hall+1);
  for(var n:ResearchCatalog.NODES.values())if(org.villageastra.domain.ResearchRules.reason(done,hall,n,next).equals("available"))return n.id();
  return null;
 }
 /** AD-124: the one next step of the Overview, most urgent first: danger, then no research target, then the hall's next level
  *  (its research or its materials), then an upgrade ready to order, then an idle crew with nothing to build. Null when all is well. */
 static Need nextStep(CompoundTag s){
  if(!s.hasUUID("village"))return null;var o=s.getCompound("overview");boolean manage=s.getBoolean("canManage");
  for(var n:needs(s))if(n.tone()==Tone.BAD)return n;
  var research=s.getCompound("research");
  if(s.contains("research")&&research.getString("selected").isEmpty()&&researchAvailable(research)!=null)return new Need(manage?Tone.ACTION:Tone.OFF,new ItemStack(Items.ENCHANTED_BOOK),t("need.research"),Component.empty(),3,researchAvailable(research),0);
  for(var raw:s.getCompound("upgrades").getList("buildings",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;if(!r.getString("type").equals("town_hall")||r.getInt("kept")>=r.getInt("max"))continue;
   var rs=r.getList("research",Tag.TAG_STRING);
   if(!rs.isEmpty())return new Need(Tone.WAIT,new ItemStack(Items.BELL),t("v2.next.hall_research",OfficeUi.roman(r.getInt("next")),OfficeUi.research(rs.getString(0))),Component.empty(),3,rs.getString(0),0);
   if(r.getInt("lack")>0)return new Need(Tone.WAIT,new ItemStack(Items.BELL),t("v2.next.hall_materials",OfficeUi.roman(r.getInt("next")),r.getInt("lack")),Component.empty(),6,r.hasUUID("id")?r.getUUID("id").toString():"",0);}
  for(var n:needs(s))if(n.section()==6&&n.tone()==Tone.ACTION)return n;
  boolean project=s.hasUUID("id")&&!s.getBoolean("survey");
  if(!project&&!s.getCompound("upgrades").getBoolean("busy")&&o.getInt("builders")>0)return new Need(manage?Tone.ACTION:Tone.OFF,new ItemStack(Items.FILLED_MAP),t("v2.next.plan"),Component.empty(),0,"",0);
  return null;
 }
 static Map<Integer,Badge> byTab(List<Need> needs){
  var out=new HashMap<Integer,Badge>();
  for(var n:needs){if(n.tone==Tone.OK||n.tone==Tone.OFF)continue;var b=out.get(n.section);out.put(n.section,b==null?new Badge(n.count,n.tone):new Badge(b.count+n.count,Tone.worst(b.tone,n.tone)));}
  return out;
 }
}

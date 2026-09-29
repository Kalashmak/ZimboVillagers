package org.villageastra.client;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.villageastra.server.ConstructionNetwork;
import org.villageastra.world.BuildingBlueprints;
/** Small field palette. The actual geometry remains in the world after closing it. */
public final class MayorSurveyScreen extends Screen {
 public static final int WIDTH=330,HEIGHT=252;
 private final boolean road;private int page;private Button lay,build,surface,light,fence;private final List<Button> designs=new ArrayList<>();
 public MayorSurveyScreen(boolean road){super(text(road?"road":"building"));this.road=road;}
 private static Component text(String key,Object... args){return Component.translatable("survey.villageastra."+key,args);}
 private void order(String design,int variant,int action){var t=ConstructionOverlay.snapshot();if(t.getBoolean("survey"))ConstructionNetwork.sendSurvey(new ConstructionNetwork.SurveyOrder(t.getUUID("id"),design,variant,action));}
 private void action(int action){var t=ConstructionOverlay.snapshot();order(t.getString("design"),t.getInt("variant"),action);}
 @Override protected void init(){int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;
  if(road){for(int i=0;i<3;i++){final int route=i;addRenderableWidget(Button.builder(text("route_short_"+i),b->{int v=ConstructionOverlay.snapshot().getInt("variant");order("home",(v&~3)|route,0);}).bounds(x+16+i*100,y+52,98,21).build());}
   // AD-038: surface tier and lamps are part of the same road selection (variant bits 2-3 and 4).
   surface=addRenderableWidget(Button.builder(Component.empty(),b->{int v=ConstructionOverlay.snapshot().getInt("variant");order("home",(v&~12)|((((v>>2)&3)+1)%4<<2),0);}).bounds(x+16,y+77,145,21).build());
   light=addRenderableWidget(Button.builder(Component.empty(),b->{int v=ConstructionOverlay.snapshot().getInt("variant");order("home",v^16,0);}).bounds(x+169,y+77,145,21).build());
   fence=addRenderableWidget(Button.builder(Component.empty(),b->{int v=ConstructionOverlay.snapshot().getInt("variant");order("home",v^32,0);}).bounds(x+16,y+102,298,21).build());
   lay=addRenderableWidget(Button.builder(text("lay"),b->{action(1);onClose();}).bounds(x+16,y+139,298,21).build());
  }else{
   for(int i=0;i<8;i++){final int row=i;var button=Button.builder(Component.empty(),b->{var list=BuildingBlueprints.designs().stream().toList();var t=ConstructionOverlay.snapshot();order(list.get(page*8+row).id(),t.getInt("variant"),0);}).bounds(x+16+(i%2)*153,y+51+(i/2)*25,145,21).build();designs.add(button);addRenderableWidget(button);}
   addRenderableWidget(Button.builder(text("more"),b->{page=(page+1)%((BuildingBlueprints.designs().size()+7)/8);tick();}).bounds(x+16,y+154,145,20).build());
   addRenderableWidget(Button.builder(text("rotate"),b->{var t=ConstructionOverlay.snapshot();order(t.getString("design"),(t.getInt("variant")+1)%4,0);}).bounds(x+169,y+154,145,20).build());
   build=addRenderableWidget(Button.builder(text("order"),b->{action(3);onClose();}).bounds(x+16,y+199,298,20).build());
  }
  addRenderableWidget(Button.builder(text("view"),b->onClose()).bounds(x+16,y+221,145,20).build());
  addRenderableWidget(Button.builder(text("cancel"),b->{action(2);onClose();}).bounds(x+169,y+221,145,20).build());tick();
 }
 @Override public void tick(){var t=ConstructionOverlay.snapshot();if(surface!=null){int v=t.getInt("variant");surface.setMessage(text("surface_"+((v>>2)&3)));light.setMessage(text(((v>>4)&1)==1?"lamps_on":"lamps_off"));fence.setMessage(text(((v>>5)&1)==1?"fence_on":"fence_off"));}if(lay!=null)lay.active=t.getBoolean("canLay");if(build!=null){build.visible=t.getBoolean("orderable");build.active=t.getBoolean("canOrder");}var list=BuildingBlueprints.designs().stream().toList();for(int i=0;i<designs.size();i++){int index=page*8+i;var b=designs.get(i);b.visible=index<list.size();if(b.visible){String id=list.get(index).id();b.setMessage(Component.translatable("building.villageastra."+id));b.active=!id.equals(t.getString("design"));}}}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){int x=(width-WIDTH)/2,y=(height-HEIGHT)/2;var t=ConstructionOverlay.snapshot();g.fill(x-1,y-1,x+WIDTH+1,y+HEIGHT+1,0xFFB89B65);g.fill(x,y,x+WIDTH,y+HEIGHT,0xF2202A30);g.fill(x,y,x+WIDTH,y+35,0xFF30443F);g.drawString(font,title,x+16,y+13,0xFFE9CD92);g.drawString(font,text("conflicts",t.getInt("conflicts"),t.getInt("surveyCells")),x+16,y+38,t.getInt("conflicts")>0?0xFFDD9C66:0xFFACD8BD);
  int rv=t.getInt("variant");Component hint=road?(((rv>>2)&3)>0||((rv>>4)&3)!=0?(t.getBoolean("roadBusy")?text("road_busy"):text("road_project",t.getInt("roadItems"))):text("road_hint")):!t.getBoolean("orderable")?text("preview_only"):t.getBoolean("canOrder")?text("order_ready",t.getInt("orderItems")):Component.translatable("order.villageastra."+t.getString("orderReason"));
  g.drawString(font,hint,x+16,y+182,road||t.getBoolean("canOrder")?0xFFC4CAC7:0xFFDD9C66);super.render(g,mx,my,dt);}
 @Override public boolean isPauseScreen(){return false;}
}

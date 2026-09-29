package org.villageastra.dialog;
import net.minecraft.network.chat.Component;
/** AD-146: one answer the player may give: its id ("topic/action"), its words on the button, whether it may be chosen now and why not. */
public record DialogOption(String id,Component label,boolean enabled,Component tooltip){
 public DialogOption{if(id==null||id.indexOf('/')<=0)throw new IllegalArgumentException("Option id is topic/action: "+id);}
 public static DialogOption of(String id,Component label){return new DialogOption(id,label,true,null);}
 public String topic(){return id.substring(0,id.indexOf('/'));}
 public String action(){return id.substring(id.indexOf('/')+1);}
}

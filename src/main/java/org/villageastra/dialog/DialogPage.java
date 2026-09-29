package org.villageastra.dialog;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
/** AD-146: what the window shows: who speaks (a resident's entity id, or null for a window without a portrait), what they say, top to
 *  bottom, and the player's answers. The context stays on the server with the open page and is handed back to the answer's handler. */
public record DialogPage(UUID speaker,List<Component> lines,List<DialogOption> options,CompoundTag context){
 public DialogPage{lines=List.copyOf(lines);options=List.copyOf(options);context=context==null?new CompoundTag():context.copy();}
}

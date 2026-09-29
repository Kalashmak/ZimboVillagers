package org.villageastra.client;
import net.minecraft.client.Minecraft;
import org.villageastra.domain.VillageNames;
/** Owner 2026-09-24: a village's name as this client shows it — as it is for a Russian client, in Latin letters for any other. */
public final class VillageNameText {
 private VillageNameText(){}
 public static String shown(String ru){
  if(ru==null||ru.isEmpty())return "";
  var lang=Minecraft.getInstance().getLanguageManager().getSelected();
  return lang!=null&&lang.startsWith("ru")?ru:VillageNames.en(ru);
 }
}

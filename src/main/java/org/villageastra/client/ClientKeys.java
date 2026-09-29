package org.villageastra.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;

@Mod.EventBusSubscriber(modid = VillageAstra.ID, value = Dist.CLIENT)
public final class ClientKeys {
    private ClientKeys() {}
    @SubscribeEvent public static void open(org.villageastra.server.ConstructionNetwork.Opened event) {
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&event.building!=null){BuildingsPanel.select(event.building);mc.setScreen(new ConstructionScreen(ConstructionScreen.BUILDINGS));return;}
        if(mc.player!=null&&event.section==7){mc.setScreen(new AtlasScreen());return;}
        // The hall workbench (section 0) lands on the Overview: what needs the mayor now comes first.
        if(mc.player!=null)mc.setScreen(event.section>=5?new MayorSurveyScreen(event.section==6):new ConstructionScreen(event.section==0?ConstructionScreen.OVERVIEW:event.section));
    }
}

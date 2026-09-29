package org.villageastra.world;

import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.server.SettlementData;
import java.util.EnumSet;

/** Physical temporary shelter, without assigning a bed or resetting the homeless timer. */
public final class ShelterGoal extends Goal {
    private final ResidentEntity resident;
    private net.minecraft.core.BlockPos shelter;
    public ShelterGoal(ResidentEntity resident) { this.resident=resident;setFlags(EnumSet.of(Flag.MOVE)); }
    @Override public boolean canUse() {
        if(resident.getServer()==null || resident.getServer().getPlayerCount()==0 || resident.settlementId()==null)return false;
        var entry=SettlementData.get(resident.getServer()).entry(resident.settlementId());
        if(entry==null || !entry.dimension().equals(resident.level().dimension().location().toString()))return false;
        var person=entry.settlement().resident(resident.getUUID());
        if(person==null || !person.alive() || person.home()!=null || person.homelessSince()<0)return false;
        shelter=HallSite.homeless(entry);
        return resident.level().hasChunkAt(shelter) && !resident.level().getBlockState(shelter.below()).isAir();
    }
    @Override public void tick() {
        if(resident.distanceToSqr(shelter.getX()+0.5,shelter.getY(),shelter.getZ()+0.5)<4)resident.getNavigation().stop();
        else if(resident.tickCount%20<2)resident.getNavigation().moveTo(shelter.getX()+0.5,shelter.getY(),shelter.getZ()+0.5,0.65);
    }
    @Override public void stop() { resident.getNavigation().stop(); }
}

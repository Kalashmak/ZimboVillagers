package org.villageastra.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.level.block.DoorBlock;

/** Also recognizes a nearby path door when lateral approach skips vanilla's collision node. */
public final class ResidentDoorGoal extends OpenDoorGoal {
    public ResidentDoorGoal(Mob mob){super(mob,true);}
    @Override public boolean canUse(){
        if(super.canUse())return true;
        var path=mob.getNavigation().getPath();
        if(path==null||path.isDone())return false;
        for(int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+3);i++){
            var node=path.getNodePos(i);
            for(var pos:java.util.List.of(node,node.above(),node.below())){
                if(closedDoor(pos))return true;
            }
        }
        // Navigation can advance past a doorway while the resident is still on its outside edge.
        // Look along the short segment to the next node, not just at nodes already beyond that door.
        var from=mob.position();
        // A partial path can end just outside the shut door, while its requested target is inside.
        for(var point:java.util.List.of(path.getNextNodePos(),path.getTarget())){
            var to=net.minecraft.world.phys.Vec3.atBottomCenterOf(point);
            double length=from.distanceTo(to);int steps=(int)Math.ceil(Math.min(2.0,length)*4);
            for(int i=1;i<=steps;i++){
                var at=net.minecraft.core.BlockPos.containing(from.add(to.subtract(from).scale(Math.min(length,i*.25)/Math.max(.001,length))));
                if(closedDoor(at)||closedDoor(at.above())||closedDoor(at.below()))return true;
            }
        }
        return false;
    }
    private boolean closedDoor(net.minecraft.core.BlockPos pos){
        var state=mob.level().getBlockState(pos);
        if(!(state.getBlock() instanceof DoorBlock door)||!door.type().canOpenByHand()||state.getValue(DoorBlock.OPEN))return false;
        if(mob.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>4)return false;
        doorPos=pos;hasDoor=true;return true;
    }
}

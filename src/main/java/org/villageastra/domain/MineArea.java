package org.villageastra.domain;
import java.util.*;

/** AD-074: the drive of one mine — how many steps it has gone, how wide and high it is, and how far below the lot its mouth lies.
 *  A mine dug before the shaft was built keeps descent 0, so an old drive stays where it was dug.
 *  AD-112: the galleries driven east and west from the landing of each floor (MineDrive), protected like the stair. */
public record MineArea(int lastStep,int width,int height,int descent,List<Gallery> galleries) {
    public static final int MAX_GALLERIES=64;
    /** One gallery: the stair step of its landing (z 7+step), its side (MineDrive.EAST/WEST) and how many columns of it are dug. */
    public record Gallery(int step,int side,int length){
        public Gallery{if(step<0||step>4095||(side!=MineDrive.EAST&&side!=MineDrive.WEST)||length<1||length>CoreEffects.mine().galleryLength())throw new IllegalArgumentException("Invalid mine gallery");}
    }
    public MineArea(int lastStep,int width){this(lastStep,width,width==1?3:4,0);}
    public MineArea(int lastStep,int width,int height){this(lastStep,width,height,0);}
    public MineArea(int lastStep,int width,int height,int descent){this(lastStep,width,height,descent,List.of());}
    public MineArea {if(lastStep<0||lastStep>4095||(width!=1&&width!=3)||height<3||height>5||descent<0||descent>16||galleries==null||galleries.size()>MAX_GALLERIES)throw new IllegalArgumentException("Invalid mine area");galleries=List.copyOf(galleries);}
    /** The same drive with this gallery dug to at least its length; the galleries only grow. */
    public MineArea with(Gallery g){
        var out=new ArrayList<Gallery>();boolean found=false;
        for(var old:galleries)if(old.step()==g.step()&&old.side()==g.side()){found=true;out.add(old.length()>=g.length()?old:g);}else out.add(old);
        if(!found)out.add(g);return new MineArea(lastStep,width,height,descent,out);
    }
    public boolean contains(int x,int y,int z,int buffer){
        if(buffer<0||buffer>3)throw new IllegalArgumentException("Invalid building buffer");
        int left=width==1?3:2,right=width==1?3:4;
        int shaft=y+descent;
        for(var g:galleries){
            // A gallery is claimed with its floor, roof and side walls, like the stair.
            int from=g.side()==MineDrive.EAST?right+1:left-g.length(),to=g.side()==MineDrive.EAST?right+g.length():left-1;
            if(x>=from-1-buffer&&x<=to+1+buffer&&Math.abs(z-7-g.step())<=1+buffer&&shaft>=-g.step()-1-buffer&&shaft<=-g.step()+MineDrive.Shape.galleryHeight(height)+buffer)return true;
        }
        if(x<left-1-buffer||x>right+1+buffer)return false;
        int from=Math.max(0,z-7-buffer),to=Math.min(lastStep,z-7+buffer);
        return Math.max(from,-shaft-1-buffer)<=Math.min(to,height+buffer-shaft);
    }
}

package DEFWriter;

import java.util.ArrayList;

public class OutPath {
    String VIA;
    boolean isVIA;
    public Integer x;
    public Integer y;
    public Integer z;
    public Integer dirc;
    public Integer extension;

    public OutPath(Integer x, Integer y, Integer z, Integer ext, Integer dirc){
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirc = dirc;
        this.extension = ext;
        this.isVIA = false;
    }

    public  OutPath(String VIAName,Integer x, Integer y, Integer z, Integer ext){
        this.VIA = VIAName;
        this.z = z;
        this.extension = ext;
        this.isVIA = true;
    }

    public String getAsString(Integer xRatio, Integer yRatio){

        x = x *xRatio;
        y = y * yRatio;

        String s ="( " + x.toString() + " " + y.toString() + " ) ";
        if(dirc == 1){
            extension = extension * xRatio;
            x= x + extension;
        }else if(dirc ==2){
            extension = extension * yRatio;
            y = y+extension;
        }


        return s +"( " + x.toString() + " " + y.toString() + " ) ";
    }

}

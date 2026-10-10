package org.chuhaijian.ime.keyboard;

import java.util.ArrayList;
import java.util.List;

/** 标准四行全键盘几何；第二行视觉缩进半键，触摸范围仍铺满整行。 */
public final class Layout {
    private Layout() {}

    public static List<Key> create(float width, float height, boolean symbols) {
        List<Key> keys=new ArrayList<>();
        float unit=width/10f, row=height/4f;
        String first=symbols?"1234567890":"qwertyuiop";
        String second=symbols?"@#￥%&*+-=":"asdfghjkl";
        String third=symbols?"()!?:;/":"zxcvbnm";
        for(int i=0;i<10;i++) add(keys,first.substring(i,i+1),i*unit,0,(i+1)*unit,row,0,0);
        for(int i=0;i<9;i++) add(keys,second.substring(i,i+1),i==0?0:(i+.5f)*unit,row,i==8?width:(i+1.5f)*unit,2*row,i==0?.5f*unit:0,i==8?.5f*unit:0);
        add(keys,symbols?"ABC":"SHIFT",0,2*row,1.5f*unit,3*row,0,0);
        for(int i=0;i<7;i++) add(keys,third.substring(i,i+1),(i+1.5f)*unit,2*row,(i+2.5f)*unit,3*row,0,0);
        add(keys,"DELETE",8.5f*unit,2*row,width,3*row,0,0);
        String[] values={"MODE",symbols?"ABC":"123",",","SPACE",".","ENTER"};
        float[] weights={1.25f,1.25f,1f,4f,1f,1.5f};
        float x=0;
        for(int i=0;i<values.length;i++) { float next=i==values.length-1?width:x+weights[i]*unit; add(keys,values[i],x,3*row,next,height,0,0); x=next; }
        return keys;
    }

    private static void add(List<Key> keys,String value,float l,float t,float r,float b,float il,float ir) { keys.add(new Key(value,l,t,r,b,il,ir)); }
}

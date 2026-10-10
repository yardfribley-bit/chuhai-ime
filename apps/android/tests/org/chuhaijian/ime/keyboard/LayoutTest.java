package org.chuhaijian.ime.keyboard;
import java.util.List;
/** 检查不同屏宽下每个触点只有一个按键，常用键位保持标准位置。 */
public final class LayoutTest {
    public static void main(String[] args) {
        for(int width:new int[]{320,360,393,411,1080}) for(boolean symbols:new boolean[]{false,true}) {
            List<Key> keys=Layout.create(width,232,symbols);
            for(float y=.5f;y<232;y+=1) for(float x=.5f;x<width;x+=1) {
                int count=0; for(Key key:keys) if(key.contains(x,y)) count++;
                if(count!=1) throw new AssertionError("触摸空隙或重叠："+width+"/"+x+"/"+y);
            }
            if(!symbols) {
                check(keys,width*.1f,87,"a"); check(keys,width*.2f,87,"s");
                check(keys,width*.2f,145,"z"); check(keys,width*.92f,145,"DELETE");
                check(keys,width*.5f,203,"SPACE");
            }
        }
        System.out.println("键位与全区域触摸检查通过（5 种屏宽、字母/符号页）");
    }
    private static void check(List<Key> keys,float x,float y,String expected) {
        for(Key key:keys) if(key.contains(x,y) && key.value.equals(expected)) return;
        throw new AssertionError(expected);
    }
}

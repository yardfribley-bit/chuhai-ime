package org.chuhaijian.ime.keyboard;

/** 键的触摸边界与绘制缩进；两者分开，视觉空隙也能响应按键。 */
public final class Key {
    public final String value;
    public final float left, top, right, bottom;
    public final float insetLeft, insetRight;

    Key(String value, float left, float top, float right, float bottom, float insetLeft, float insetRight) {
        this.value=value; this.left=left; this.top=top; this.right=right; this.bottom=bottom;
        this.insetLeft=insetLeft; this.insetRight=insetRight;
    }

    public boolean contains(float x, float y) { return x>=left && x<right && y>=top && y<bottom; }
}

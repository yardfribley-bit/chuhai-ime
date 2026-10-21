package org.chuhaijian.ime.keyboard;

/** 一次指针动作的判定结果。坐标是这次事件的位置。 */
public final class Stroke {
    public enum Kind {
        DOWN,
        TAP,
        LONG_PRESS,
        SWIPE_UP,
        SWIPE_LEFT,
        SWIPE_RIGHT
    }

    public final Kind kind;

    public final String key;

    public final int pointerId;

    public final float x, y;

    Stroke(Kind kind, String key, int pointerId, float x, float y) {
        this.kind = kind;
        this.key = key;
        this.pointerId = pointerId;
        this.x = x;
        this.y = y;
    }
}

package org.chuhaijian.ime.keyboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 与界面无关的指针跟踪。小幅移动抬起落到最近的键；尚未绑定动作的滑动仍记在按下的键上。
 * 新指针按下时，更早的指针按按下顺序结算，连打不会按抬起顺序颠倒。
 * 上滑超过键高的 {@link #SWIPE_UP_RATIO}，或水平位移达到阈值且大于垂直位移时，另记为滑动。
 */
public final class Tracker {
    public static final float SWIPE_UP_RATIO = 0.3f;

    private final float slop;

    private final float swipeHorizontal;

    private final long longPressMs;

    private final ArrayList<Pointer> pointers = new ArrayList<Pointer>();

    public Tracker(float slop, float swipeHorizontal, long longPressMs) {
        this.slop = slop;
        this.swipeHorizontal = swipeHorizontal;
        this.longPressMs = longPressMs;
    }

    /**
     * 按下一指。更早仍按着、且没被长按消耗的指针按按下顺序排在前面，调用方应立刻提交它们；
     * 最后一项是这一指的 {@link Stroke.Kind#DOWN}。没有键时返回空列表。
     */
    public List<Stroke> down(int id, float x, float y, long time, List<Key> keys) {
        List<Stroke> settled = new ArrayList<Stroke>();
        Key key = Layout.nearest(keys, x, y);
        if (key == null) return settled;
        for (int i = 0; i < pointers.size(); i++) {
            Pointer pointer = pointers.get(i);
            if (!pointer.alive || pointer.consumed) continue;
            settled.add(strokeFor(pointer, pointer.x, pointer.y, keys));
            pointer.consumed = true;
        }
        Pointer pointer = find(id);
        if (pointer == null) {
            pointer = new Pointer();
        } else {
            pointers.remove(pointer);
        }
        pointers.add(pointer);
        pointer.id = id;
        pointer.key = key.value;
        pointer.current = key.value;
        pointer.downX = x;
        pointer.downY = y;
        pointer.x = x;
        pointer.y = y;
        pointer.height = Math.max(1f, key.bottom - key.top);
        pointer.downTime = time;
        pointer.alive = true;
        pointer.consumed = false;
        settled.add(new Stroke(Stroke.Kind.DOWN, key.value, id, x, y));
        return settled;
    }

    public void move(int id, float x, float y, List<Key> keys) {
        Pointer pointer = find(id);
        if (pointer == null) return;
        pointer.x = x;
        pointer.y = y;
        Key key = Layout.nearest(keys, x, y);
        if (key != null) pointer.current = key.value;
    }

    public void collectCurrent(Collection<String> into) {
        for (Pointer pointer : pointers) if (pointer.alive) into.add(pointer.current);
    }

    /** 按住超过长按时间且没滑出容差时返回长按；不消耗这次按下。 */
    public Stroke pollLongPress(int id, long time) {
        Pointer pointer = find(id);
        if (pointer == null || pointer.consumed) return null;
        if (time - pointer.downTime < longPressMs) return null;
        if (Math.abs(pointer.x - pointer.downX) > slop || Math.abs(pointer.y - pointer.downY) > slop) return null;
        return new Stroke(Stroke.Kind.LONG_PRESS, pointer.key, id, pointer.x, pointer.y);
    }

    /** 长按退格已开始连删，抬起不再当成一次点击。 */
    public void consume(int id) {
        Pointer pointer = find(id);
        if (pointer != null) pointer.consumed = true;
    }

    public Stroke up(int id, float x, float y, long time, List<Key> keys) {
        Pointer pointer = find(id);
        if (pointer == null) return null;
        pointer.alive = false;
        pointers.remove(pointer);
        if (pointer.consumed) return null;
        return strokeFor(pointer, x, y, keys);
    }

    public void cancel(int id) {
        Pointer pointer = find(id);
        if (pointer == null) return;
        pointer.alive = false;
        pointers.remove(pointer);
    }

    public void clear() { pointers.clear(); }

    /**
     * 长按和按下不提交。点击提交抬起处最近的键；尚未绑定动作的滑动提交最初按下的键。
     * 滑动的 {@link Stroke#key} 保持按下的那颗键，以后绑定空格移光标等动作时仍认这颗键。
     */
    public static String keyToCommit(Stroke stroke) {
        if (stroke == null || stroke.kind == Stroke.Kind.LONG_PRESS || stroke.kind == Stroke.Kind.DOWN) return null;
        return stroke.key;
    }

    private Stroke strokeFor(Pointer pointer, float x, float y, List<Key> keys) {
        float dx = x - pointer.downX;
        float dy = y - pointer.downY;
        Stroke.Kind kind = Stroke.Kind.TAP;
        String key = pointer.current;
        Key nearest = Layout.nearest(keys, x, y);
        if (nearest != null) key = nearest.value;
        if (dy < -pointer.height * SWIPE_UP_RATIO && Math.abs(dy) >= Math.abs(dx)) {
            kind = Stroke.Kind.SWIPE_UP;
            key = pointer.key;
        } else if (Math.abs(dx) >= swipeHorizontal && Math.abs(dx) > Math.abs(dy)) {
            kind = dx < 0 ? Stroke.Kind.SWIPE_LEFT : Stroke.Kind.SWIPE_RIGHT;
            key = pointer.key;
        }
        return new Stroke(kind, key, pointer.id, x, y);
    }

    private Pointer find(int id) {
        for (Pointer pointer : pointers) if (pointer.alive && pointer.id == id) return pointer;
        return null;
    }

    private static final class Pointer {
        private int id;
        private String key;
        private String current;
        private float downX, downY, x, y, height;
        private long downTime;
        private boolean alive;
        private boolean consumed;
    }
}

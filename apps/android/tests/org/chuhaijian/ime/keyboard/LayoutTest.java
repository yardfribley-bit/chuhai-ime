package org.chuhaijian.ime.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 键位覆盖、最近键、滑动/长按判定、连打顺序，以及默认面板高度。 */
public final class LayoutTest {
    public static void main(String[] args) {
        for (int width : new int[]{320, 360, 393, 411, 1080}) for (boolean symbols : new boolean[]{false, true}) {
            List<Key> keys = Layout.create(width, 232, symbols);
            for (float y = .5f; y < 232; y += 1) for (float x = .5f; x < width; x += 1) {
                int count = 0;
                for (Key key : keys) if (key.contains(x, y)) count++;
                if (count != 1) throw new AssertionError("触摸空隙或重叠：" + width + "/" + x + "/" + y);
            }
            if (!symbols) {
                check(keys, width * .1f, 87, "a");
                check(keys, width * .2f, 87, "s");
                check(keys, width * .2f, 145, "z");
                check(keys, width * .92f, 145, "DELETE");
                check(keys, width * .5f, 203, "SPACE");
            }
            if (Layout.nearest(keys, -8, 20) == null || Layout.nearest(keys, width + 8, 20) == null
                || Layout.nearest(keys, width / 2f, -12) == null || Layout.nearest(keys, width / 2f, 250) == null) {
                throw new AssertionError("键盘外仍应落到最近的键");
            }
        }
        gestures();
        unboundSwipes();
        rolling();
        panel();
        System.out.println("键位、最近键、手势、连打与默认面板高度检查通过");
    }

    private static void gestures() {
        List<Key> keys = Arrays.asList(
            new Key("q", 0, 0, 40, 50, 0, 0),
            new Key("w", 40, 0, 80, 50, 0, 0));
        Tracker tracker = new Tracker(8, 100, 400);
        tracker.down(0, 20, 40, 0, keys);
        Stroke tap = tracker.up(0, 20, 40, 50, keys);
        expect(tap, Stroke.Kind.TAP, "q");

        tracker.down(0, 20, 40, 0, keys);
        tracker.move(0, 55, 40, keys);
        Stroke neighbor = tracker.up(0, 55, 40, 80, keys);
        expect(neighbor, Stroke.Kind.TAP, "w");

        tracker.down(0, 20, 10, 0, keys);
        Stroke outside = tracker.up(0, -6, 10, 40, keys);
        expect(outside, Stroke.Kind.TAP, "q");

        tracker.down(0, 20, 40, 0, keys);
        Stroke edge = tracker.up(0, 20, 25, 80, keys);
        expect(edge, Stroke.Kind.TAP, "q");

        tracker.down(0, -2, 25, 0, keys);
        Stroke jitter = tracker.up(0, -1, 26, 30, keys);
        expect(jitter, Stroke.Kind.TAP, "q");
        if (!"q".equals(Tracker.keyToCommit(jitter))) throw new AssertionError("按下时的最近键应能跟上轻微抖动");

        tracker.down(0, 20, 40, 0, keys);
        Stroke swipeUp = tracker.up(0, 20, 20, 80, keys);
        expect(swipeUp, Stroke.Kind.SWIPE_UP, "q");
        if (!"q".equals(Tracker.keyToCommit(swipeUp))) throw new AssertionError("上滑尚未绑定动作时仍应提交按下的键");

        tracker.down(0, 20, 25, 0, keys);
        tracker.move(0, 130, 25, keys);
        Stroke swipeRight = tracker.up(0, 130, 25, 80, keys);
        expect(swipeRight, Stroke.Kind.SWIPE_RIGHT, "q");
        if (!"w".equals(Layout.nearest(keys, 130, 25).value)) throw new AssertionError("用例应滑到相邻键上");
        if (!"q".equals(Tracker.keyToCommit(swipeRight))) throw new AssertionError("未绑定的横滑应提交按下的键");

        tracker.down(1, 20, 25, 1000, keys);
        Stroke held = tracker.pollLongPress(1, 1400);
        expect(held, Stroke.Kind.LONG_PRESS, "q");
        tracker.consume(1);
        if (tracker.up(1, 20, 25, 1600, keys) != null) throw new AssertionError("长按被消耗后抬起不应再出键");

        tracker.down(2, 20, 25, 0, keys);
        tracker.move(2, 40, 25, keys);
        if (tracker.pollLongPress(2, 5000) != null) throw new AssertionError("滑出容差后不应再记长按");
        expect(tracker.up(2, 40, 25, 5100, keys), Stroke.Kind.TAP, "w");

        tracker.down(3, 10, 10, 0, keys);
        List<Stroke> followed = tracker.down(4, 60, 10, 0, keys);
        expect(followed.get(0), Stroke.Kind.TAP, "q");
        if (followed.size() != 2 || followed.get(1).kind != Stroke.Kind.DOWN || !"w".equals(followed.get(1).key)) {
            throw new AssertionError("新指针按下应先结算旧键，再附带 DOWN");
        }
        if (Tracker.keyToCommit(followed.get(1)) != null) throw new AssertionError("按下本身不应提交");
        if (tracker.pollLongPress(3, 10000) != null) throw new AssertionError("已结算的指针不应再长按");
        if (tracker.up(3, 10, 10, 20, keys) != null) throw new AssertionError("已结算的指针抬起不能再出键");
        expect(tracker.up(4, 60, 10, 20, keys), Stroke.Kind.TAP, "w");
    }

    /** 真实键位上的未绑定滑动：提交按下的键，不提交抬起处的另一颗键。未到滑动阈值时仍跟手。 */
    private static void unboundSwipes() {
        List<Key> keys = Layout.create(1080, 232, false);
        swipe(keys, "SPACE", ".", Stroke.Kind.SWIPE_RIGHT);
        swipe(keys, "DELETE", "b", Stroke.Kind.SWIPE_LEFT);
        swipe(keys, "g", "y", Stroke.Kind.SWIPE_UP);

        Key space = must(keys, "SPACE");
        Key period = must(keys, ".");
        float y = (space.top + space.bottom) / 2f;
        Tracker tracker = new Tracker(8, 100, 400);
        tracker.down(0, space.right - 6, y, 0, keys);
        Stroke slide = tracker.up(0, period.left + 6, y, 30, keys);
        expect(slide, Stroke.Kind.TAP, ".");
        if (!".".equals(Tracker.keyToCommit(slide))) throw new AssertionError("未到滑动阈值时应跟手落到最近的键");

        Key g = must(keys, "g");
        Key yKey = must(keys, "y");
        Key q = must(keys, "q");
        float gx = (g.left + g.right) / 2f, gy = (g.top + g.bottom) / 2f;
        float yx = (yKey.left + yKey.right) / 2f, yy = (yKey.top + yKey.bottom) / 2f;
        float qx = (q.left + q.right) / 2f, qy = (q.top + q.bottom) / 2f;
        Tracker rollingSwipe = new Tracker(8, 100, 400);
        rollingSwipe.down(0, gx, gy, 0, keys);
        rollingSwipe.move(0, yx, yy, keys);
        List<Stroke> next = rollingSwipe.down(1, qx, qy, 20, keys);
        expect(next.get(0), Stroke.Kind.SWIPE_UP, "g");
        if (!"g".equals(Tracker.keyToCommit(next.get(0)))) throw new AssertionError("第二指按下时仍应提交先按下的键");
        if (rollingSwipe.up(0, yx, yy, 30, keys) != null) throw new AssertionError("已结算的滑动不能再出键");
        expect(rollingSwipe.up(1, qx, qy, 40, keys), Stroke.Kind.TAP, "q");
    }

    private static void swipe(List<Key> keys, String pressed, String landed, Stroke.Kind kind) {
        Key from = must(keys, pressed);
        Key to = must(keys, landed);
        float x0 = (from.left + from.right) / 2f;
        float y0 = (from.top + from.bottom) / 2f;
        float x1 = (to.left + to.right) / 2f;
        float y1 = (to.top + to.bottom) / 2f;
        if (!landed.equals(Layout.nearest(keys, x1, y1).value)) throw new AssertionError("抬起点应在 " + landed);
        Tracker tracker = new Tracker(8, 100, 400);
        tracker.down(0, x0, y0, 0, keys);
        tracker.move(0, x1, y1, keys);
        Stroke stroke = tracker.up(0, x1, y1, 40, keys);
        expect(stroke, kind, pressed);
        if (!pressed.equals(Tracker.keyToCommit(stroke))) {
            throw new AssertionError(pressed + " 的未绑定滑动不应提交 " + landed);
        }
    }

    /** 连打按按下顺序提交。取消不补键，已结算的键抬起不重复。 */
    private static void rolling() {
        List<Key> keys = Arrays.asList(
            new Key("a", 0, 0, 30, 40, 0, 0),
            new Key("b", 30, 0, 60, 40, 0, 0),
            new Key("c", 60, 0, 90, 40, 0, 0),
            new Key("DELETE", 90, 0, 130, 40, 0, 0),
            new Key("SPACE", 0, 40, 130, 80, 0, 0));
        Typing typing = new Typing(keys);
        typing.down(7, 15, 20, 0);
        typing.down(3, 45, 20, 10);
        typing.up(3, 45, 20, 20);
        typing.up(7, 15, 20, 30);
        typing.expect("a", "b");

        typing = new Typing(keys);
        typing.down(7, 15, 20, 0);
        typing.down(3, 45, 20, 10);
        typing.up(7, 15, 20, 20);
        typing.up(3, 45, 20, 30);
        typing.expect("a", "b");

        typing = new Typing(keys);
        typing.down(5, 15, 20, 0);
        typing.down(1, 45, 20, 5);
        typing.down(8, 75, 20, 10);
        typing.up(1, 45, 20, 15);
        typing.up(8, 75, 20, 20);
        typing.up(5, 15, 20, 25);
        typing.expect("a", "b", "c");

        typing = new Typing(keys);
        typing.down(0, 15, 20, 0);
        typing.cancelAll();
        typing.up(0, 15, 20, 10);
        typing.expect();

        typing = new Typing(keys);
        typing.down(0, 15, 20, 0);
        typing.cancel(0);
        typing.up(0, 15, 20, 10);
        typing.expect();

        typing = new Typing(keys);
        typing.down(0, 15, 20, 0);
        typing.down(1, 45, 20, 10);
        typing.cancelAll();
        typing.up(1, 45, 20, 20);
        typing.up(0, 15, 20, 20);
        typing.expect("a");

        typing = new Typing(keys);
        typing.down(0, 15, 20, 0);
        typing.down(1, 45, 20, 10);
        typing.cancel(1);
        typing.up(1, 45, 20, 20);
        typing.up(0, 15, 20, 30);
        typing.expect("a");

        typing = new Typing(keys);
        typing.down(0, 110, 20, 0);
        typing.down(1, 15, 20, 10);
        typing.up(1, 15, 20, 20);
        typing.up(0, 110, 20, 30);
        typing.expect("DELETE", "a");

        typing = new Typing(keys);
        typing.down(0, 60, 60, 0);
        typing.down(1, 15, 20, 10);
        typing.up(0, 60, 60, 20);
        typing.up(1, 15, 20, 30);
        typing.expect("SPACE", "a");

        typing = new Typing(keys);
        typing.down(0, 110, 20, 0);
        typing.consume(0);
        typing.down(1, 15, 20, 10);
        typing.up(1, 15, 20, 20);
        typing.up(0, 110, 20, 30);
        typing.expect("a");
    }

    private static Key must(List<Key> keys, String value) {
        for (Key key : keys) if (key.value.equals(value)) return key;
        throw new AssertionError("缺少键 " + value);
    }

    /** 按 KeyboardView 的顺序提交：新指针按下时结算更早的键，抬起只提交还没结算的。 */
    private static final class Typing {
        private final Tracker tracker = new Tracker(8, 100, 400);
        private final List<Key> keys;
        private final List<String> committed = new ArrayList<String>();

        private Typing(List<Key> keys) { this.keys = keys; }

        private void down(int id, float x, float y, long time) {
            for (Stroke stroke : tracker.down(id, x, y, time, keys)) commit(stroke);
        }

        private void up(int id, float x, float y, long time) { commit(tracker.up(id, x, y, time, keys)); }

        private void cancel(int id) { tracker.cancel(id); }

        private void cancelAll() { tracker.clear(); }

        private void consume(int id) { tracker.consume(id); }

        private void commit(Stroke stroke) {
            String value = Tracker.keyToCommit(stroke);
            if (value != null) committed.add(value);
        }

        private void expect(String... values) {
            if (committed.size() != values.length) {
                throw new AssertionError("期望 " + Arrays.toString(values) + "，实际 " + committed);
            }
            for (int i = 0; i < values.length; i++) {
                if (!values[i].equals(committed.get(i))) {
                    throw new AssertionError("期望 " + Arrays.toString(values) + "，实际 " + committed);
                }
            }
        }
    }

    private static void expect(Stroke stroke, Stroke.Kind kind, String key) {
        if (stroke == null || stroke.kind != kind || !key.equals(stroke.key)) {
            throw new AssertionError("期望 " + kind + " " + key + "，实际 " + (stroke == null ? "null" : stroke.kind + " " + stroke.key));
        }
    }

    private static void panel() {
        if (Panel.rowDp(Panel.DEFAULT_ROW_DP, false) != 56) throw new AssertionError("默认行高");
        if (Panel.rowDp(80, false) != 70 || Panel.rowDp(10, false) != 50) throw new AssertionError("行高范围");
        if (Panel.rowDp(70, true) != 44) throw new AssertionError("横屏行高");
        int idle = Panel.totalDp(Panel.DEFAULT_ROW_DP, false);
        int composing = Panel.totalDp(Panel.DEFAULT_ROW_DP, true);
        if (idle != 268 || composing != 286 || composing > Panel.LIMIT_DP || idle > Panel.LIMIT_DP) {
            throw new AssertionError("默认面板高度 " + idle + "/" + composing);
        }
        if (Panel.totalDp(Panel.LANDSCAPE_ROW_DP, true) > Panel.LIMIT_DP) throw new AssertionError("横屏面板高度");
    }

    private static void check(List<Key> keys, float x, float y, String expected) {
        for (Key key : keys) if (key.contains(x, y) && key.value.equals(expected)) return;
        throw new AssertionError(expected);
    }
}

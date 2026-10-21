package org.chuhaijian.ime.keyboard;

import java.util.Arrays;
import java.util.List;

/** 键位覆盖、最近键、滑动/长按判定，以及默认面板高度。 */
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
        panel();
        System.out.println("键位、最近键、手势与默认面板高度检查通过");
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

        tracker.down(0, 20, 40, 0, keys);
        Stroke swipeUp = tracker.up(0, 20, 20, 80, keys);
        expect(swipeUp, Stroke.Kind.SWIPE_UP, "q");
        if (!"q".equals(Tracker.keyToCommit(swipeUp, keys))) throw new AssertionError("上滑尚未绑定动作时仍应落键");

        tracker.down(0, 20, 25, 0, keys);
        Stroke swipeRight = tracker.up(0, 130, 25, 80, keys);
        expect(swipeRight, Stroke.Kind.SWIPE_RIGHT, "q");
        if (!"w".equals(Tracker.keyToCommit(swipeRight, keys))) throw new AssertionError("横滑抬起处应落到最近的键");

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
        tracker.down(4, 60, 10, 0, keys);
        expect(tracker.up(3, 10, 10, 20, keys), Stroke.Kind.TAP, "q");
        expect(tracker.up(4, 60, 10, 20, keys), Stroke.Kind.TAP, "w");
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

package org.chuhaijian.ime.keyboard;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;

/** 把每个键暴露成可点击的无障碍节点，替代原来的一键一个按钮。 */
final class KeyNodes extends AccessibilityNodeProvider {
    private final KeyboardView view;

    KeyNodes(KeyboardView view) { this.view = view; }

    @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
        if (id == HOST_VIEW_ID) {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(view);
            view.onInitializeAccessibilityNodeInfo(info);
            int count = view.keyCount();
            for (int i = 0; i < count; i++) info.addChild(view, i);
            return info;
        }
        if (id < 0 || id >= view.keyCount()) return null;
        Key key = view.keyAt(id);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(view, id);
        info.setClassName(Button.class.getName());
        info.setPackageName(view.getContext().getPackageName());
        info.setSource(view, id);
        info.setParent(view);
        info.setContentDescription(view.describe(key));
        info.setClickable(true);
        info.setFocusable(true);
        info.setVisibleToUser(true);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        Rect bounds = new Rect(Math.round(key.left), Math.round(key.top), Math.round(key.right), Math.round(key.bottom));
        info.setBoundsInParent(bounds);
        int[] origin = new int[2];
        view.getLocationOnScreen(origin);
        info.setBoundsInScreen(new Rect(bounds.left + origin[0], bounds.top + origin[1], bounds.right + origin[0], bounds.bottom + origin[1]));
        return info;
    }

    @Override public boolean performAction(int id, int action, Bundle args) {
        if (id == HOST_VIEW_ID) return view.performAccessibilityAction(action, args);
        if (action == AccessibilityNodeInfo.ACTION_CLICK && id >= 0 && id < view.keyCount()) {
            view.activateKey(view.keyAt(id).value);
            return true;
        }
        return false;
    }
}

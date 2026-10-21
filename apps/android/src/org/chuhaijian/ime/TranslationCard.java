package org.chuhaijian.ime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 盖住键盘上半部分的翻译预览。回车和「替换」使用译文，「取消」保留原文。 */
final class TranslationCard extends LinearLayout {
    enum State { HIDDEN, PENDING, READY, ERROR }

    interface Actions {
        void cancel();
        void replace();
    }

    private final TextView body;
    private final TextView hint;
    private final Button cancel;
    private final Button replace;
    private final Actions actions;
    private State state = State.HIDDEN;

    TranslationCard(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setOrientation(VERTICAL);
        int pad = dp(12);
        setPadding(pad, dp(8), pad, dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        setBackground(background);
        setClickable(true);
        setContentDescription("翻译预览");
        body = text(16, Color.rgb(30, 36, 44));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        hint = text(12, Color.rgb(90, 100, 114));
        addView(hint);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        cancel = button("取消", false);
        replace = button("替换", true);
        row.addView(cancel, new LayoutParams(0, dp(36), 1));
        LayoutParams replaceParams = new LayoutParams(0, dp(36), 1.4f);
        replaceParams.leftMargin = dp(8);
        row.addView(replace, replaceParams);
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(6);
        addView(row, rowParams);
        cancel.setOnClickListener(v -> {
            if (state == State.ERROR) hide();
            else actions.cancel();
        });
        replace.setOnClickListener(v -> actions.replace());
        setVisibility(GONE);
    }

    void showPending() { show(State.PENDING, "翻译中…", "取消保留原文", false); }

    void showReady(String translation) { show(State.READY, translation, "回车或「替换」使用译文，取消保留原文", true); }

    void showError(String message) { show(State.ERROR, message, "", false); }

    boolean isError() { return state == State.ERROR; }

    void hide() {
        state = State.HIDDEN;
        setVisibility(GONE);
    }

    private void show(State next, String message, String note, boolean canReplace) {
        state = next;
        body.setText(message);
        hint.setText(note);
        hint.setVisibility(note.isEmpty() ? GONE : VISIBLE);
        replace.setVisibility(canReplace ? VISIBLE : GONE);
        cancel.setText(next == State.ERROR ? "关闭" : "取消");
        setVisibility(VISIBLE);
        bringToFront();
    }

    private TextView text(int size, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(primary ? Color.rgb(31, 111, 229) : Color.rgb(238, 241, 245));
        button.setBackground(background);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(44, 52, 64));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

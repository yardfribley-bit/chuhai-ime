package org.chuhaijian.ime.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

/** 原生按键保留多指与辅助功能支持；退格可长按，字母按下显示预览。 */
final class KeyButton extends Button {
    private final Handler handler=new Handler();
    private final boolean repeating;
    private final Runnable action;
    private boolean repeated;
    private boolean holding;
    private boolean previewEnabled;
    private PopupWindow preview;
    private final Runnable repeat=new Runnable() {
        public void run() { if(holding) { repeated=true; action.run(); handler.postDelayed(this,70); } }
    };

    KeyButton(Context context,String label,boolean repeating,boolean previewEnabled,Runnable action) {
        super(context); this.repeating=repeating; this.previewEnabled=previewEnabled; this.action=action;
        setText(label); setAllCaps(false); setTextSize(22); setTextColor(Color.rgb(30,36,44));
        setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        setMinWidth(0); setMinimumWidth(0); setMinHeight(0); setMinimumHeight(0);
        setPadding(0,0,0,0); setGravity(Gravity.CENTER); setIncludeFontPadding(false);
        setSoundEffectsEnabled(false); setOnClickListener(v -> { if(!repeated) action.run(); });
        setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
            public void onInitializeAccessibilityNodeInfo(android.view.View host,android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host,info); info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            }
        });
    }

    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    void style(Key key,boolean special,boolean active) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},background(key,Color.rgb(184,205,238)));
        states.addState(new int[]{},background(key,active?Color.rgb(53,107,218):special?Color.rgb(208,214,224):Color.WHITE));
        setBackground(states); setPadding(Math.round(key.insetLeft),0,Math.round(key.insetRight),0); setTextColor(active?Color.WHITE:Color.rgb(30,36,44));
    }

    private InsetDrawable background(Key key,int color) {
        GradientDrawable fill=new GradientDrawable(); fill.setColor(color); fill.setCornerRadius(dp(6));
        return new InsetDrawable(fill,Math.round(key.insetLeft)+dp(3),dp(4),Math.round(key.insetRight)+dp(3),dp(4));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                repeated=false; holding=true;
                if(previewEnabled) showPreview();
                if(repeating) handler.postDelayed(repeat,ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if(event.getX() < -slop || event.getX() > getWidth()+slop || event.getY() < -slop || event.getY() > getHeight()+slop) stop();
                break;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: stop(); break;
        }
        return super.onTouchEvent(event);
    }

    private void showPreview() {
        dismissPreview();
        TextView label=new TextView(getContext()); label.setText(getText()); label.setTextSize(32); label.setGravity(Gravity.CENTER); label.setTextColor(Color.rgb(30,36,44));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(9)); label.setBackground(bg); label.setElevation(dp(4));
        preview=new PopupWindow(label,dp(58),dp(64),false); preview.setTouchable(false); preview.setClippingEnabled(true); preview.setElevation(dp(5));
        preview.showAsDropDown(this,(getWidth()-dp(58))/2,-getHeight()-dp(63));
    }

    private void dismissPreview() { if(preview!=null) { preview.dismiss(); preview=null; } }
    private void stop() { holding=false; handler.removeCallbacks(repeat); dismissPreview(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}

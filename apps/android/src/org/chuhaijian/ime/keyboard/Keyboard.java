package org.chuhaijian.ime.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import java.util.List;

/** 四行全键盘；原生按钮的完整边界负责触摸，内缩背景只影响视觉。 */
public final class Keyboard extends ViewGroup {
    private final Listener listener;
    private boolean symbols,shift,caps,english;
    private long lastShift;
    private String mode="五笔",enter="换行";
    private List<Key> keys;

    public Keyboard(Context context,Listener listener) {
        super(context); this.listener=listener; setMotionEventSplittingEnabled(true);
        setBackgroundColor(android.graphics.Color.rgb(229,232,238)); rebuild();
    }

    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    public void setMode(String mode,boolean english) { this.mode=mode; this.english=english; shift=false; caps=false; rebuild(); }
    public void setEditor(EditorInfo info) {
        if(info==null) return;
        switch(info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: enter="搜索"; break;
            case EditorInfo.IME_ACTION_SEND: enter="发送"; break;
            case EditorInfo.IME_ACTION_DONE: enter="完成"; break;
            case EditorInfo.IME_ACTION_GO: enter="前往"; break;
            case EditorInfo.IME_ACTION_NEXT: enter="下一项"; break;
            default: enter="换行";
        }
        if((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0) enter="换行";
        rebuild();
    }

    private void rebuild() {
        removeAllViews(); keys=Layout.create(Math.max(1,getWidth()),Math.max(1,getHeight()),symbols);
        boolean preview=getContext().getSharedPreferences("keyboard",Context.MODE_PRIVATE).getBoolean("key_preview",true);
        for(Key key:keys) {
            String label=label(key.value);
            KeyButton button=new KeyButton(getContext(),label,key.value.equals("DELETE"),preview && key.value.length()==1 && Character.isLetter(key.value.charAt(0)),()->press(key.value));
            if(key.value.length()>1) button.setTextSize(key.value.equals("SHIFT") || key.value.equals("DELETE")?23:14);
            button.setContentDescription(key.value.equals("DELETE")?"退格":key.value.equals("SHIFT")?"切换大写":key.value.equals("SPACE")?"空格":label);
            addView(button,new ViewGroup.LayoutParams(0,0));
        }
        requestLayout();
    }

    private String label(String value) {
        switch(value) {
            case "SHIFT": return caps?"⇪":"⇧";
            case "DELETE": return "⌫";
            case "SPACE": return mode;
            case "MODE": return english?"英 / 中":"中 / 英";
            case "ENTER": return enter;
            default: return !symbols && shift?value.toUpperCase(java.util.Locale.ROOT):value;
        }
    }

    private void press(String value) {
        if(value.equals("SHIFT")) {
            long now=android.os.SystemClock.uptimeMillis();
            if(caps) { caps=false; shift=false; }
            else if(shift && now-lastShift<350) caps=true;
            else shift=!shift;
            lastShift=now; updateLabels(); return;
        }
        if(value.equals("123") || value.equals("ABC")) { symbols=!symbols; rebuild(); return; }
        if(value.length()==1 && shift && !symbols) {
            listener.onKey(value.toUpperCase(java.util.Locale.ROOT));
            if(!caps) { shift=false; updateLabels(); }
        } else listener.onKey(value);
    }

    private void updateLabels() {
        for(int i=0;i<getChildCount();i++) {
            KeyButton button=(KeyButton)getChildAt(i); Key key=keys.get(i);
            button.setText(label(key.value)); button.style(key,key.value.length()>1,key.value.equals("ENTER") || key.value.equals("SHIFT") && shift);
        }
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec);
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        int row=getContext().getSharedPreferences("keyboard",Context.MODE_PRIVATE).getInt("key_height",58);
        row=Math.max(50,Math.min(70,row));
        setMeasuredDimension(width,dp((landscape?44:row)*4));
        keys=Layout.create(getMeasuredWidth(),getMeasuredHeight(),symbols);
        for(int i=0;i<getChildCount();i++) {
            Key key=keys.get(i);
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.round(key.right)-Math.round(key.left),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(key.bottom)-Math.round(key.top),MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        for(int i=0;i<getChildCount();i++) {
            Key key=keys.get(i); KeyButton button=(KeyButton)getChildAt(i);
            button.layout(Math.round(key.left),Math.round(key.top),Math.round(key.right),Math.round(key.bottom));
            button.style(key,key.value.length()>1,key.value.equals("ENTER") || key.value.equals("SHIFT") && shift);
        }
    }
}

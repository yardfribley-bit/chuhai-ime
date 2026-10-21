package org.chuhaijian.ime.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 自绘四行全键盘。按下立即反馈，抬起提交最近的键，滑出键外也不丢。 */
public final class KeyboardView extends View {
    private static final int REPEAT_MS = 70;
    private static final int PRESSED = Color.rgb(184, 205, 238);
    private static final int ENTER_COLOR = Color.rgb(53, 107, 218);
    private static final int FUNCTION = Color.rgb(208, 214, 224);
    private static final int INK = Color.rgb(30, 36, 44);

    private final Listener listener;
    private final Feedback feedback = new Feedback();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Runnable> longPressTasks = new HashMap<Integer, Runnable>();
    private final HashSet<String> pressed = new HashSet<String>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final KeyNodes nodes;
    private Tracker tracker;
    private List<Key> keys = new ArrayList<Key>();
    private PopupWindow preview;
    private TextView previewLabel;
    private boolean symbols, shift, caps, english, previewEnabled = true;
    private long lastShift;
    private int repeating = -1;
    private String mode = "五笔";
    private String enter = "换行";

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (repeating < 0) return;
            listener.onKey("DELETE");
            handler.postDelayed(this, REPEAT_MS);
        }
    };

    public KeyboardView(Context context, Listener listener) {
        this(context, null, listener);
    }

    private KeyboardView(Context context, AttributeSet attrs, Listener listener) {
        super(context, attrs);
        this.listener = listener;
        nodes = new KeyNodes(this);
        setClickable(true);
        setWillNotDraw(false);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(false);
        setBackgroundColor(Color.rgb(229, 232, 238));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setAntiAlias(true);
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        tracker = new Tracker(slop, slop * 8f, ViewConfiguration.getLongPressTimeout());
    }

    public void setMode(String mode, boolean english) {
        this.mode = mode;
        this.english = english;
        shift = false;
        caps = false;
        rebuild();
    }

    public void setEditor(EditorInfo info) {
        if (info == null) return;
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH: enter = "搜索"; break;
            case EditorInfo.IME_ACTION_SEND: enter = "发送"; break;
            case EditorInfo.IME_ACTION_DONE: enter = "完成"; break;
            case EditorInfo.IME_ACTION_GO: enter = "前往"; break;
            case EditorInfo.IME_ACTION_NEXT: enter = "下一项"; break;
            default: enter = "换行";
        }
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) enter = "换行";
        invalidate();
    }

    int keyCount() { return keys.size(); }

    Key keyAt(int index) { return keys.get(index); }

    String describe(Key key) {
        switch (key.value) {
            case "DELETE": return "退格";
            case "SHIFT": return "切换大写";
            case "SPACE": return "空格";
            case "ENTER": return enter;
            case "MODE": return english ? "切换到中文" : "切换到英文";
            case "123": return "数字符号";
            case "ABC": return "返回字母";
            default: return label(key.value);
        }
    }

    void activateKey(String value) {
        feedback.play(this, value);
        commitKey(value);
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return nodes; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int saved = getContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("key_height", Panel.DEFAULT_ROW_DP);
        int row = Panel.rowDp(saved, landscape);
        setMeasuredDimension(width, dp(row * 4));
        keys = Layout.create(getMeasuredWidth(), getMeasuredHeight(), symbols);
    }

    @Override protected void onDraw(Canvas canvas) {
        float padX = dp(3);
        float padY = dp(4);
        float radius = dp(6);
        for (Key key : keys) {
            boolean down = pressed.contains(key.value);
            boolean active = key.value.equals("ENTER") || key.value.equals("SHIFT") && (shift || caps);
            boolean special = key.value.length() > 1;
            fill.setColor(down ? PRESSED : active ? ENTER_COLOR : special ? FUNCTION : Color.WHITE);
            rect.set(key.left + key.insetLeft + padX, key.top + padY, key.right - key.insetRight - padX, key.bottom - padY);
            canvas.drawRoundRect(rect, radius, radius, fill);
            String shown = label(key.value);
            text.setColor(active && !down ? Color.WHITE : INK);
            float points = shown.length() == 1 ? 22f : key.value.equals("SHIFT") || key.value.equals("DELETE") ? 23f : 14f;
            text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, points, getResources().getDisplayMetrics()));
            float baseline = (rect.top + rect.bottom) / 2f - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(shown, rect.centerX(), baseline, text);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                beginPointer(id, event.getX(index), event.getY(index));
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) tracker.move(event.getPointerId(i), event.getX(i), event.getY(i), keys);
                refreshPressed();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                finishPointer(id, event.getX(index), event.getY(index));
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                return true;
            default:
                return true;
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelAll();
        super.onDetachedFromWindow();
    }

    private void beginPointer(int id, float x, float y) {
        previewEnabled = getContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getBoolean("key_preview", true);
        Stroke down = tracker.down(id, x, y, SystemClock.uptimeMillis(), keys);
        if (down == null) return;
        feedback.play(this, down.key);
        Runnable task = new Runnable() {
            @Override public void run() {
                Stroke held = tracker.pollLongPress(id, SystemClock.uptimeMillis());
                if (held == null || !"DELETE".equals(held.key)) return;
                tracker.consume(id);
                repeating = id;
                listener.onKey("DELETE");
                handler.postDelayed(repeat, REPEAT_MS);
            }
        };
        longPressTasks.put(id, task);
        handler.postDelayed(task, ViewConfiguration.getLongPressTimeout());
        refreshPressed();
    }

    private void finishPointer(int id, float x, float y) {
        Runnable task = longPressTasks.remove(id);
        if (task != null) handler.removeCallbacks(task);
        if (repeating == id) {
            repeating = -1;
            handler.removeCallbacks(repeat);
        }
        Stroke stroke = tracker.up(id, x, y, SystemClock.uptimeMillis(), keys);
        refreshPressed();
        String value = Tracker.keyToCommit(stroke, keys);
        if (value != null) commitKey(value);
    }

    private void cancelAll() {
        tracker.clear();
        handler.removeCallbacksAndMessages(null);
        longPressTasks.clear();
        repeating = -1;
        pressed.clear();
        dismissPreview();
        invalidate();
    }

    private void refreshPressed() {
        pressed.clear();
        tracker.collectCurrent(pressed);
        updatePreview();
        invalidate();
    }

    void commitKey(String value) {
        if (value.equals("SHIFT")) {
            long now = SystemClock.uptimeMillis();
            if (caps) { caps = false; shift = false; }
            else if (shift && now - lastShift < 350) caps = true;
            else shift = !shift;
            lastShift = now;
            invalidate();
            return;
        }
        if (value.equals("123") || value.equals("ABC")) { symbols = !symbols; rebuild(); return; }
        if (value.length() == 1 && shift && !symbols) {
            listener.onKey(value.toUpperCase(Locale.ROOT));
            if (!caps) { shift = false; invalidate(); }
        } else listener.onKey(value);
    }

    private void rebuild() {
        if (getWidth() > 0 && getHeight() > 0) keys = Layout.create(getWidth(), getHeight(), symbols);
        invalidate();
        if (isShown()) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private String label(String value) {
        switch (value) {
            case "SHIFT": return caps ? "⇪" : "⇧";
            case "DELETE": return "⌫";
            case "SPACE": return mode;
            case "MODE": return english ? "英 / 中" : "中 / 英";
            case "ENTER": return enter;
            default: return !symbols && shift ? value.toUpperCase(Locale.ROOT) : value;
        }
    }

    private void updatePreview() {
        if (!previewEnabled) { dismissPreview(); return; }
        Key shown = null;
        for (Key key : keys) {
            if (!pressed.contains(key.value)) continue;
            String shownLabel = label(key.value);
            if (shownLabel.length() == 1 && Character.isLetter(shownLabel.charAt(0))) shown = key;
        }
        if (shown == null) dismissPreview();
        else showPreview(shown, label(shown.value));
    }

    private void showPreview(Key key, String shown) {
        if (preview == null) {
            previewLabel = new TextView(getContext());
            previewLabel.setTextSize(32);
            previewLabel.setGravity(Gravity.CENTER);
            previewLabel.setTextColor(INK);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE);
            background.setCornerRadius(dp(9));
            previewLabel.setBackground(background);
            preview = new PopupWindow(previewLabel, dp(54), dp(62), false);
            preview.setTouchable(false);
            preview.setClippingEnabled(false);
        }
        previewLabel.setText(shown);
        int[] origin = new int[2];
        getLocationInWindow(origin);
        int x = origin[0] + Math.round((key.left + key.right) / 2f) - dp(27);
        int y = origin[1] + Math.round(key.top) - dp(54);
        if (!isAttachedToWindow()) return;
        if (preview.isShowing()) preview.update(x, y, dp(54), dp(62));
        else preview.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
    }

    private void dismissPreview() {
        if (preview != null && preview.isShowing()) preview.dismiss();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

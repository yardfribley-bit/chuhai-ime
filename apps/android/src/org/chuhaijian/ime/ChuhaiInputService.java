package org.chuhaijian.ime;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.chuhaijian.ime.keyboard.KeyboardView;
import org.chuhaijian.ime.keyboard.Panel;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android 系统键盘；拼音和五笔的候选生成均由共用内核完成。 */
public final class ChuhaiInputService extends InputMethodService {
    private KeyboardView keyboard;
    private Button modeButton;
    private TextView strip;
    private LinearLayout tools;
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidates;
    private TranslationCard card;
    private NativeBridge nativeEngine;
    private String composition = "";
    private String notice = "";
    private String loadError;
    private boolean english, privateField;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private int generation;
    private int modeIndex = 0, lastChineseMode = 0;
    private String original, source, translated;
    private JSONArray terms;
    private int originalCursor;
    private InputConnection translationClient;

    public void onCreate() {
        super.onCreate();
        try {
            nativeEngine = new NativeBridge(this);
            modeIndex = getSharedPreferences("keyboard", MODE_PRIVATE).getInt("input_mode_v2", getSharedPreferences("keyboard", MODE_PRIVATE).getInt("mode", 0) == 1 ? 1 : 0);
            if (modeIndex < 0 || modeIndex > 2) modeIndex = 0;
            lastChineseMode = getSharedPreferences("keyboard", MODE_PRIVATE).getInt("chinese_mode", 0);
            if (lastChineseMode != 1) lastChineseMode = 0;
            english = modeIndex == 2;
            nativeEngine.frame(command("mode").put("mode", new String[]{"wubi", "pinyin", "english"}[modeIndex]));
        } catch (Exception error) {
            loadError = "词库加载失败：" + error.getMessage();
        }
    }

    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(229, 232, 238));
        strip = new TextView(this);
        strip.setTextSize(12);
        strip.setTextColor(Color.rgb(74, 85, 99));
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(12), 0, dp(12), 0);
        strip.setSingleLine(true);
        strip.setBackgroundColor(Color.rgb(246, 247, 249));
        root.addView(strip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(Panel.STRIP_DP)));
        strip.setVisibility(View.GONE);
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.rgb(246, 247, 249));
        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        modeButton = toolButton(new String[]{"五笔", "拼音", "英文"}[modeIndex]);
        modeButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, modeButton);
            String[] names = {"五笔", "拼音", "英文"};
            for (int i = 0; i < names.length; i++) {
                final int mode = i;
                menu.getMenu().add(names[i]).setOnMenuItemClickListener(item -> { selectMode(mode); return true; });
            }
            menu.show();
        });
        tools.addView(modeButton, new LinearLayout.LayoutParams(dp(64), dp(Panel.BAR_DP)));
        Button translate = toolButton("译");
        translate.setTextColor(Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.rgb(31, 111, 229));
        pill.setCornerRadius(dp(15));
        translate.setBackground(pill);
        translate.setOnClickListener(v -> translateParagraph());
        LinearLayout.LayoutParams translateParams = new LinearLayout.LayoutParams(dp(52), dp(30));
        translateParams.gravity = Gravity.CENTER_VERTICAL;
        tools.addView(translate, translateParams);
        tools.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button settings = toolButton("设置");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        tools.addView(settings, new LinearLayout.LayoutParams(dp(56), dp(Panel.BAR_DP)));
        Button hide = toolButton("收起");
        hide.setOnClickListener(v -> requestHideSelf(0));
        tools.addView(hide, new LinearLayout.LayoutParams(dp(56), dp(Panel.BAR_DP)));
        bar.addView(tools, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(Panel.BAR_DP)));
        candidateScroll = new HorizontalScrollView(this);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidates = new LinearLayout(this);
        candidates.setGravity(Gravity.CENTER_VERTICAL);
        candidateScroll.addView(candidates);
        candidateScroll.setVisibility(View.GONE);
        bar.addView(candidateScroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(Panel.BAR_DP)));
        candidateScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> noteVisibleHints());
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(Panel.BAR_DP)));
        FrameLayout stage = new FrameLayout(this);
        keyboard = new KeyboardView(this, this::keyboardKey);
        keyboard.setMode(new String[]{"五笔", "拼音", "英文"}[modeIndex], english);
        keyboard.setEditor(getCurrentInputEditorInfo());
        stage.addView(keyboard, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        card = new TranslationCard(this, new TranslationCard.Actions() {
            public void cancel() { cancelTranslation(); }
            public void replace() { acceptTranslation(); }
        });
        stage.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0));
        keyboard.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> placeCard());
        root.addView(stage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateFrame(new JSONObject());
        return root;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private Button toolButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void placeCard() {
        if (keyboard == null || card == null || keyboard.getHeight() <= 0) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        int height = keyboard.getHeight() * 3 / 4;
        if (params.height == height) return;
        params.height = height;
        params.width = FrameLayout.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP;
        card.setLayoutParams(params);
    }

    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) {
            keyboard.setEditor(info);
            keyboard.requestLayout();
        }
        updateFrame(new JSONObject());
    }

    private void keyboardKey(String key) {
        if (card != null && card.isError()) card.hide();
        switch (key) {
            case "DELETE": delete(); return;
            case "MODE": selectMode(english ? lastChineseMode : 2); return;
            case "SPACE": if (!composition.isEmpty()) accept(0); else type(" "); return;
            case "ENTER":
                if (original != null) { acceptTranslation(); return; }
                if (!composition.isEmpty()) { raw(); return; }
                InputConnection connection = getCurrentInputConnection();
                if (connection == null) return;
                EditorInfo info = getCurrentInputEditorInfo();
                int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
                if (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && connection.performEditorAction(action)) return;
                connection.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                connection.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER));
                return;
            default: type(!english && key.equals(",") ? "，" : !english && key.equals(".") ? "。" : key);
        }
    }

    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        cancelTranslation();
        if (card != null) card.hide();
        clear();
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        privateField = (type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        try { if (nativeEngine != null) nativeEngine.frame(command("privacy").put("private", privateField)); }
        catch (Exception error) { showNotice(error.getMessage()); }
    }

    public void onFinishInput() {
        cancelTranslation();
        if (card != null) card.hide();
        clear();
        super.onFinishInput();
    }

    public void onDestroy() {
        network.shutdownNow();
        if (nativeEngine != null) nativeEngine.close();
        super.onDestroy();
    }

    private JSONObject command(String op) throws Exception { return new JSONObject().put("op", op); }

    private void showNotice(String text) {
        notice = text == null ? "" : text;
        renderStrip();
    }

    private void renderStrip() {
        if (strip == null) return;
        if (original == null && !notice.isEmpty()) {
            strip.setText(notice);
            strip.setVisibility(View.VISIBLE);
            return;
        }
        if (!composition.isEmpty()) {
            strip.setText(composition);
            strip.setVisibility(View.VISIBLE);
            return;
        }
        strip.setVisibility(View.GONE);
    }

    private void renderBar() {
        if (tools == null || candidateScroll == null) return;
        boolean typing = !composition.isEmpty();
        tools.setVisibility(typing ? View.GONE : View.VISIBLE);
        candidateScroll.setVisibility(typing ? View.VISIBLE : View.GONE);
    }

    private void clear() {
        try {
            if (nativeEngine != null) nativeEngine.frame(command("clear"));
            composition = "";
        } catch (Exception error) { showNotice(error.getMessage()); }
    }

    private void updateFrame(JSONObject frame) {
        try {
            if (nativeEngine == null) {
                showNotice(loadError == null ? "词库加载失败" : loadError);
                renderBar();
                return;
            }
            if (!frame.has("compose")) frame = nativeEngine.frame(command("frame"));
            composition = frame.optString("compose");
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                String commit = frame.isNull("commit") ? "" : frame.optString("commit");
                if (!commit.isEmpty()) connection.commitText(commit, 1);
                if (!composition.isEmpty()) connection.setComposingText(composition, 1);
                else connection.finishComposingText();
            }
            if (candidates != null) fillCandidates(frame.optJSONArray("candidates"), frame.optJSONArray("annotations"));
            if (candidates != null) candidates.post(this::noteVisibleHints);
            if (original == null) notice = "";
            renderStrip();
            renderBar();
        } catch (Exception error) { showNotice("输入失败：" + error.getMessage()); }
    }

    private void fillCandidates(JSONArray list, JSONArray hints) throws Exception {
        int count = list == null ? 0 : list.length();
        while (candidates.getChildCount() > count) candidates.removeViewAt(candidates.getChildCount() - 1);
        for (int i = 0; i < count; i++) {
            final int index = i;
            Button button;
            if (i < candidates.getChildCount()) button = (Button) candidates.getChildAt(i);
            else {
                button = new Button(this);
                button.setAllCaps(false);
                button.setSingleLine(true);
                button.setTextSize(16);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setPadding(dp(11), 0, dp(11), 0);
                candidates.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(Panel.BAR_DP)));
            }
            String word = list.getString(i);
            String gloss = hints == null ? "" : hints.optString(i).replace('\n', ' ').trim();
            button.setText(gloss.isEmpty() ? word : word + "  " + gloss);
            if (i == 0) {
                GradientDrawable highlight = new GradientDrawable();
                highlight.setColor(Color.rgb(230, 239, 253));
                highlight.setCornerRadius(dp(8));
                button.setBackground(highlight);
                button.setTextColor(Color.rgb(31, 111, 229));
            } else {
                button.setBackgroundColor(Color.TRANSPARENT);
                button.setTextColor(Color.rgb(30, 36, 44));
            }
            button.setOnClickListener(v -> accept(index));
        }
    }

    private void noteVisibleHints() {
        if (nativeEngine == null || candidates == null || privateField || candidateScroll == null || candidateScroll.getVisibility() != View.VISIBLE) return;
        try {
            JSONArray indices = new JSONArray();
            android.graphics.Rect rect = new android.graphics.Rect();
            for (int i = 0; i < candidates.getChildCount(); i++) if (candidates.getChildAt(i).getGlobalVisibleRect(rect) && rect.width() > 0 && rect.height() > 0) indices.put(i);
            nativeEngine.call(command("visible").put("indices", indices));
        } catch (Exception error) { showNotice(error.getMessage()); }
    }

    private void type(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (original != null) cancelTranslation();
        if (text.equals("/") && composition.isEmpty() && !privateField && trigger(connection)) return;
        if (!english && text.length() == 1 && text.charAt(0) >= 'a' && text.charAt(0) <= 'z') {
            try { updateFrame(nativeEngine.frame(command("push").put("text", text))); }
            catch (Exception error) { showNotice(error.getMessage()); }
        } else {
            if (!composition.isEmpty()) accept(0);
            connection.commitText(text, 1);
        }
    }

    private void accept(int index) { try { updateFrame(nativeEngine.frame(command("accept").put("index", index))); } catch (Exception error) { showNotice(error.getMessage()); } }
    private void raw() { if (composition.isEmpty()) return; try { updateFrame(nativeEngine.frame(command("raw"))); } catch (Exception error) { showNotice(error.getMessage()); } }
    private void delete() {
        cancelTranslation();
        if (!composition.isEmpty()) { try { updateFrame(nativeEngine.frame(command("delete"))); } catch (Exception error) { showNotice(error.getMessage()); } }
        else { InputConnection connection = getCurrentInputConnection(); if (connection != null) connection.deleteSurroundingTextInCodePoints(1, 0); }
    }

    private void selectMode(int mode) {
        cancelTranslation();
        raw();
        modeIndex = mode;
        english = mode == 2;
        if (!english) lastChineseMode = mode;
        if (keyboard != null) keyboard.setMode(new String[]{"五笔", "拼音", "英文"}[modeIndex], english);
        if (modeButton != null) modeButton.setText(new String[]{"五笔", "拼音", "英文"}[modeIndex]);
        getSharedPreferences("keyboard", MODE_PRIVATE).edit().putInt("input_mode_v2", modeIndex).putInt("chinese_mode", lastChineseMode).apply();
        try { updateFrame(nativeEngine.frame(command("mode").put("mode", new String[]{"wubi", "pinyin", "english"}[modeIndex]))); }
        catch (Exception error) { showNotice(error.getMessage()); }
    }

    private void translateParagraph() {
        if (privateField) { showCardError("密码框不翻译"); return; }
        if (original != null || nativeEngine == null) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) { showCardError("无法读取当前段落"); return; }
        try {
            ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
            if (extracted != null && extracted.selectionStart != extracted.selectionEnd) {
                showCardError("请先取消选区，再翻译光标前的段落");
                return;
            }
            CharSequence before = connection.getTextBeforeCursor(500, 0);
            if (before == null) { showCardError("无法读取当前段落"); return; }
            String text = before.toString();
            boolean slash = text.endsWith("/");
            int cursor = -1;
            boolean truncated = true;
            if (extracted != null && extracted.selectionStart >= 0) {
                cursor = extracted.startOffset + extracted.selectionStart;
                truncated = cursor > before.length();
            }
            if (!startTranslation(connection, slash ? text : text + "/", truncated, cursor, slash) && (card == null || !card.isError())) {
                showCardError("当前段落不能翻译");
            }
        } catch (Exception error) { showCardError("无法读取当前段落：" + error.getMessage()); }
    }

    private boolean trigger(InputConnection connection) {
        try {
            ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
            if (extracted == null || extracted.selectionStart != extracted.selectionEnd || extracted.selectionStart < 0) return false;
            CharSequence before = connection.getTextBeforeCursor(500, 0);
            if (before == null) return false;
            String text = before.toString();
            int cursor = extracted.startOffset + extracted.selectionStart;
            return startTranslation(connection, text, cursor > before.length(), cursor, text.endsWith("/"));
        } catch (Exception error) {
            showCardError("无法读取当前段落：" + error.getMessage());
            return false;
        }
    }

    private boolean startTranslation(InputConnection connection, String before, boolean truncated, int cursor, boolean slashInDocument) throws Exception {
        android.content.SharedPreferences config = getSharedPreferences("cloud", MODE_PRIVATE);
        JSONObject job = nativeEngine.frame(command("trigger").put("before", before).put("truncated", truncated).put("translation_language", config.getString("translation_language", "en")));
        if (!job.has("source")) return false;
        if (!config.getBoolean("enabled", false)) { showCardError("请先在设置中启用云翻译"); return false; }
        String endpoint = config.getString("url", "");
        if (!endpoint.startsWith("https://")) { showCardError("翻译接口需要 HTTPS"); return false; }
        source = job.getString("source");
        original = slashInDocument ? source + "/" : source;
        originalCursor = cursor;
        translationClient = connection;
        translated = null;
        terms = new JSONArray();
        int token = ++generation;
        notice = "";
        renderStrip();
        placeCard();
        if (card != null) card.showPending();
        final String input = source, system = job.getString("system"), target = job.optString("target", "en"), model = config.getString("model", ""), key = config.getString("key", "");
        network.execute(() -> translate(token, endpoint, model, key, input, system, target));
        return true;
    }

    private void showCardError(String message) {
        placeCard();
        if (card != null) card.showError(message);
        else showNotice(message);
    }

    private void translate(int token, String endpoint, String model, String key, String input, String system, String target) {
        try {
            CloudChat result = CloudChat.request(endpoint, model, key, input, system, target);
            new android.os.Handler(getMainLooper()).post(() -> {
                if (generation != token || original == null) return;
                translated = result.sentence;
                terms = result.terms;
                placeCard();
                if (card != null) card.showReady(result.sentence);
            });
        } catch (Exception error) {
            new android.os.Handler(getMainLooper()).post(() -> {
                if (generation != token) return;
                cancelTranslation();
                showCardError("翻译失败，原文已保留：" + error.getMessage());
            });
        }
    }

    private void acceptTranslation() {
        if (translated == null) return;
        InputConnection current = getCurrentInputConnection();
        ExtractedText extracted = current == null ? null : current.getExtractedText(new ExtractedTextRequest(), 0);
        CharSequence before = current == null ? null : current.getTextBeforeCursor(original.length(), 0);
        boolean positionOk = current == translationClient && before != null && before.toString().equals(original);
        if (positionOk && originalCursor >= 0) {
            positionOk = extracted != null && extracted.selectionStart == extracted.selectionEnd && extracted.startOffset + extracted.selectionStart == originalCursor;
        }
        if (!positionOk) {
            cancelTranslation();
            showCardError("输入位置已变化，原文已保留");
            return;
        }
        current.beginBatchEdit();
        current.deleteSurroundingText(original.length(), 0);
        current.commitText(translated, 1);
        current.endBatchEdit();
        try { nativeEngine.frame(command("record").put("source", source).put("translation", translated).put("terms", terms == null ? new JSONArray() : terms)); }
        catch (Exception error) {
            cancelTranslation();
            showCardError("词条保存失败：" + error.getMessage());
            return;
        }
        cancelTranslation();
    }

    private void cancelTranslation() {
        generation++;
        original = null;
        source = null;
        translated = null;
        terms = null;
        translationClient = null;
        if (card != null && !card.isError()) card.hide();
    }
}

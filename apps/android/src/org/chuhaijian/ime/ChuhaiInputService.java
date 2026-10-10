package org.chuhaijian.ime;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android 系统键盘；拼音和五笔的候选生成均由共用内核完成。 */
public final class ChuhaiInputService extends InputMethodService {
    private NativeBridge nativeEngine;
    private TextView status;
    private LinearLayout candidates;
    private String composition = "";
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
        try { nativeEngine = new NativeBridge(this); modeIndex=getSharedPreferences("keyboard",MODE_PRIVATE).getInt("input_mode_v2",getSharedPreferences("keyboard",MODE_PRIVATE).getInt("mode",0)==1?1:0); if(modeIndex<0 || modeIndex>2) modeIndex=0; lastChineseMode=getSharedPreferences("keyboard",MODE_PRIVATE).getInt("chinese_mode",0); if(lastChineseMode!=1) lastChineseMode=0; english=modeIndex==2; nativeEngine.frame(command("mode").put("mode",new String[]{"wubi","pinyin","english"}[modeIndex])); }
        catch (Exception error) { composition = "词库加载失败：" + error.getMessage(); }
    }

    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this); status.setTextSize(17); status.setPadding(12,8,12,8); root.addView(status);
        LinearLayout modes = new LinearLayout(this);
        addKey(modes, "五笔", () -> selectMode(0)); addKey(modes, "拼音", () -> selectMode(1)); addKey(modes, "英文", () -> selectMode(2)); root.addView(modes);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        candidates = new LinearLayout(this); scroll.addView(candidates); root.addView(scroll); scroll.setOnScrollChangeListener((view,x,y,oldX,oldY) -> noteVisibleHints());
        String[] rows = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (String row : rows) {
            LinearLayout line = new LinearLayout(this);
            for (char letter : row.toCharArray()) addKey(line, String.valueOf(letter), () -> type(String.valueOf(letter)));
            root.addView(line);
        }
        LinearLayout tools = new LinearLayout(this);
        addKey(tools, "五/拼/英", () -> selectMode((modeIndex+1)%3));
        addKey(tools, "中/英", () -> selectMode(english ? lastChineseMode : 2));
        addKey(tools, "删除", this::delete);
        addKey(tools, "设置", () -> startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        root.addView(tools);
        LinearLayout bottom = new LinearLayout(this);
        for (String symbol : new String[]{"，","。","/","空格","回车","取消"}) {
            addKey(bottom, symbol, () -> {
                if (symbol.equals("空格")) { if (!composition.isEmpty()) accept(0); else type(" "); }
                else if (symbol.equals("回车")) { if (original != null) acceptTranslation(); else { raw(); InputConnection c = getCurrentInputConnection(); if (c != null) { c.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)); c.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)); } } }
                else if (symbol.equals("取消")) cancelTranslation();
                else type(symbol);
            });
        }
        root.addView(bottom); updateFrame(new JSONObject()); return root;
    }

    private void addKey(LinearLayout row, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(0,4,0,4);
        row.addView(button, new LinearLayout.LayoutParams(0,(int)(48*getResources().getDisplayMetrics().density),1)); button.setOnClickListener(v -> action.run());
    }

    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting); cancelTranslation(); clear();
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        privateField = (type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        try { if(nativeEngine!=null) nativeEngine.frame(command("privacy").put("private",privateField)); } catch(Exception e) { showStatus(e.getMessage()); }
    }

    public void onFinishInput() { cancelTranslation(); clear(); super.onFinishInput(); }
    public void onDestroy() { network.shutdownNow(); if (nativeEngine != null) nativeEngine.close(); super.onDestroy(); }

    private JSONObject command(String op) throws Exception { return new JSONObject().put("op",op); }
    private void showStatus(String text) { if (status != null) status.setText(text); }
    private void clear() { try { if (nativeEngine != null) nativeEngine.frame(command("clear")); composition = ""; } catch (Exception e) { showStatus(e.getMessage()); } }

    private void updateFrame(JSONObject frame) {
        try {
            if (nativeEngine == null) { showStatus(composition); return; }
            if (!frame.has("compose")) frame = nativeEngine.frame(command("frame"));
            composition = frame.optString("compose");
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                String commit = frame.isNull("commit") ? "" : frame.optString("commit");
                if (!commit.isEmpty()) connection.commitText(commit,1);
                if (!composition.isEmpty()) connection.setComposingText(composition,1);
                else connection.finishComposingText();
            }
            if (candidates != null) {
                candidates.removeAllViews(); JSONArray list = frame.optJSONArray("candidates"); JSONArray hints = frame.optJSONArray("annotations");
                if (list != null) for (int i=0;i<list.length();i++) {
                    final int index=i; Button button=new Button(this); button.setText(list.getString(i)+(hints!=null && !hints.optString(i).isEmpty()?"\n"+hints.optString(i):"")); candidates.addView(button); button.setOnClickListener(v -> accept(index));
                }
            }
            if(candidates!=null) candidates.post(this::noteVisibleHints);
            if (original == null) showStatus(composition.isEmpty() ? new String[]{"五笔","拼音","英文"}[modeIndex]+" · // 翻译" : composition);
        } catch (Exception e) { showStatus("输入失败："+e.getMessage()); }
    }

    private void noteVisibleHints() {
        if(nativeEngine==null || candidates==null || privateField) return;
        try { JSONArray indices=new JSONArray(); android.graphics.Rect rect=new android.graphics.Rect();
            for(int i=0;i<candidates.getChildCount();i++) if(candidates.getChildAt(i).getGlobalVisibleRect(rect) && rect.width()>0 && rect.height()>0) indices.put(i);
            nativeEngine.call(command("visible").put("indices",indices));
        } catch(Exception e) { showStatus(e.getMessage()); }
    }

    private void type(String text) {
        InputConnection connection=getCurrentInputConnection(); if (connection==null) return;
        if (original != null) cancelTranslation();
        if (text.equals("/") && composition.isEmpty() && !privateField && trigger(connection)) return;
        if (!english && text.length()==1 && text.charAt(0)>='a' && text.charAt(0)<='z') {
            try { updateFrame(nativeEngine.frame(command("push").put("text",text))); } catch(Exception e) { showStatus(e.getMessage()); }
        } else { if (!composition.isEmpty()) accept(0); connection.commitText(text,1); }
    }

    private void accept(int index) { try { updateFrame(nativeEngine.frame(command("accept").put("index",index))); } catch(Exception e) { showStatus(e.getMessage()); } }
    private void raw() { if (composition.isEmpty()) return; try { updateFrame(nativeEngine.frame(command("raw"))); } catch(Exception e) { showStatus(e.getMessage()); } }
    private void delete() { cancelTranslation(); if (!composition.isEmpty()) { try { updateFrame(nativeEngine.frame(command("delete"))); } catch(Exception e) { showStatus(e.getMessage()); } } else { InputConnection c=getCurrentInputConnection(); if(c!=null) c.deleteSurroundingTextInCodePoints(1,0); } }

    private void selectMode(int mode) {
        cancelTranslation(); raw(); modeIndex=mode; english=mode==2; if(!english) lastChineseMode=mode;
        getSharedPreferences("keyboard",MODE_PRIVATE).edit().putInt("input_mode_v2",modeIndex).putInt("chinese_mode",lastChineseMode).apply();
        try { updateFrame(nativeEngine.frame(command("mode").put("mode",new String[]{"wubi","pinyin","english"}[modeIndex]))); } catch(Exception e) { showStatus(e.getMessage()); }
    }

    private boolean trigger(InputConnection connection) {
        try {
            ExtractedText extracted=connection.getExtractedText(new ExtractedTextRequest(),0);
            if (extracted==null || extracted.selectionStart!=extracted.selectionEnd || extracted.selectionStart<0) return false;
            CharSequence before=connection.getTextBeforeCursor(500,0); if(before==null) return false;
            JSONObject job=nativeEngine.frame(command("trigger").put("before",before.toString()).put("truncated",extracted.startOffset+extracted.selectionStart>before.length()));
            if(!job.has("source")) return false;
            android.content.SharedPreferences config=getSharedPreferences("cloud",MODE_PRIVATE);
            if(!config.getBoolean("enabled",false)) { showStatus("请先在设置中启用云翻译"); return false; }
            String endpoint=config.getString("url",""); if(!endpoint.startsWith("https://")) { showStatus("翻译接口需要 HTTPS"); return false; }
            source=job.getString("source"); original=source+"/"; originalCursor=extracted.startOffset+extracted.selectionStart; translationClient=connection;
            translated=null; terms=new JSONArray(); int token=++generation;
            showStatus("翻译中… 取消保留原文");
            final String input=source, system=job.getString("system"), model=config.getString("model",""), key=config.getString("key","");
            network.execute(() -> translate(token,endpoint,model,key,input,system)); return true;
        } catch(Exception error) { showStatus("无法读取当前段落："+error.getMessage()); return false; }
    }

    private void translate(int token,String endpoint,String model,String key,String input,String system) {
        HttpURLConnection http=null;
        try {
            String url=endpoint.replaceAll("/+$",""); if(!url.endsWith("/chat/completions")) url+="/chat/completions";
            http=(HttpURLConnection)new URL(url).openConnection(); http.setRequestMethod("POST"); http.setConnectTimeout(15000); http.setReadTimeout(30000); http.setInstanceFollowRedirects(false); http.setDoOutput(true);
            http.setRequestProperty("Authorization","Bearer "+key); http.setRequestProperty("Content-Type","application/json");
            JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content",system)).put(new JSONObject().put("role","user").put("content",new JSONObject().put("text",input).put("target_language","en").toString()));
            byte[] body=new JSONObject().put("model",model).put("messages",messages).put("max_tokens",1500).toString().getBytes(StandardCharsets.UTF_8);
            try(java.io.OutputStream out=http.getOutputStream()) { out.write(body); }
            if(http.getResponseCode()!=200) throw new IllegalStateException("接口返回 "+http.getResponseCode());
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream in=http.getInputStream()) { byte[] buffer=new byte[8192]; int count; while((count=in.read(buffer))!=-1) { if(output.size()+count>1048576) throw new IllegalStateException("接口回复过大"); output.write(buffer,0,count); } }
            JSONObject result=new JSONObject(new JSONObject(output.toString("UTF-8")).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
            String text=result.getString("sentence").trim(); if(text.isEmpty()) throw new IllegalStateException("译文为空");
            android.os.Handler handler=new android.os.Handler(getMainLooper()); handler.post(() -> { if(generation==token && original!=null) { translated=text; terms=result.optJSONArray("terms"); showStatus(text+"\n回车使用 · 取消保留原文"); } });
        } catch(Exception error) { new android.os.Handler(getMainLooper()).post(() -> { if(generation==token) { cancelTranslation(); showStatus("翻译失败，原文已保留："+error.getMessage()); } }); }
        finally { if(http!=null) http.disconnect(); }
    }

    private void acceptTranslation() {
        if(translated==null) return;
        InputConnection current=getCurrentInputConnection();
        ExtractedText extracted=current==null?null:current.getExtractedText(new ExtractedTextRequest(),0);
        CharSequence before=current==null?null:current.getTextBeforeCursor(original.length(),0);
        if(current!=translationClient || extracted==null || extracted.selectionStart!=extracted.selectionEnd || extracted.startOffset+extracted.selectionStart!=originalCursor || before==null || !before.toString().equals(original)) { cancelTranslation(); showStatus("输入位置已变化，原文已保留"); return; }
        current.beginBatchEdit(); current.deleteSurroundingText(original.length(),0); current.commitText(translated,1); current.endBatchEdit();
        try { nativeEngine.frame(command("record").put("source",source).put("translation",translated).put("terms",terms==null?new JSONArray():terms)); } catch(Exception error) { showStatus("词条保存失败："+error.getMessage()); }
        cancelTranslation();
    }

    private void cancelTranslation() { generation++; original=null; source=null; translated=null; terms=null; translationClient=null; if(status!=null) showStatus("出海舰"); }
}

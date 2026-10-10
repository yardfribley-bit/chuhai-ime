package org.chuhaijian.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** 配置与主动复习入口；云服务由用户自行选择并提供密钥。 */
public final class SettingsActivity extends Activity {
    private LinearLayout root;
    private NativeBridge nativeEngine;
    private org.chuhaijian.ime.cloud.CloudSetup cloudSetup;

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); scroll.addView(root); setContentView(scroll);
        text("出海舰",26); text("五笔86 · 拼音 · 英文\n中文输入后 // 翻译，回车确认使用。",17);
        button("启用出海舰键盘",() -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("选择输入法",() -> ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        android.content.SharedPreferences keyboard=getSharedPreferences("keyboard",MODE_PRIVATE);
        text("键盘设置",22);
        TextView height=text("按键高度："+keyboard.getInt("key_height",62)+" dp",16);
        SeekBar size=new SeekBar(this); size.setMax(20); size.setProgress(keyboard.getInt("key_height",62)-50); root.addView(size);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { height.setText("按键高度："+(50+progress)+" dp"); if(user) keyboard.edit().putInt("key_height",50+progress).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        CheckBox preview=new CheckBox(this); preview.setText("按下字母时显示放大预览"); preview.setChecked(keyboard.getBoolean("key_preview",true)); root.addView(preview);
        preview.setOnCheckedChangeListener((view,checked) -> keyboard.edit().putBoolean("key_preview",checked).apply());
        text("设置即时保存；返回输入框后生效。点键盘左上角可选择五笔、拼音、英文；左下角中/英切换，旁边 123 打开数字符号。",15);
        text("整句云翻译",22);
        cloudSetup=new org.chuhaijian.ime.cloud.CloudSetup(this);root.addView(cloudSetup);
        TextView state=text("",15);
        button("今日学词",this::showWords);
        button("开源许可与数据来源",() -> {
            try(java.io.InputStream in=getAssets().open("NOTICE.txt")) {
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count; while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
                new android.app.AlertDialog.Builder(this).setTitle("开源许可与数据来源").setMessage(out.toString("UTF-8")).setPositiveButton("关闭",null).show();
            } catch(Exception error) { state.setText(error.getMessage()); }
        });
        try { nativeEngine=new NativeBridge(this); } catch(Exception error) { state.setText(error.getMessage()); }
    }

    private TextView text(String value,int size) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setPadding(0,12,0,12); root.addView(view); return view; }
    private void button(String label,Runnable action) { Button button=new Button(this); button.setText(label); root.addView(button); button.setOnClickListener(v -> action.run()); }
    private EditText field(String hint,String value,boolean password) { EditText field=new EditText(this); field.setHint(hint); field.setText(value); field.setSingleLine(true); if(password) field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(field); return field; }

    private void showWords() {
        try {
            if(nativeEngine!=null) nativeEngine.close(); nativeEngine=new NativeBridge(this);
            JSONArray words=new JSONArray(nativeEngine.call(new JSONObject().put("op","today")));
            String[] labels=new String[words.length()];
            for(int i=0;i<words.length();i++) { JSONObject word=words.getJSONObject(i); labels[i]=word.getString("word")+" — "+word.getString("meaning")+"\n"+"今日出现："+(word.optJSONObject("daily_counts")!=null && word.optJSONObject("daily_counts").has(java.time.LocalDate.now().toString())?word.optJSONObject("daily_counts").optInt(java.time.LocalDate.now().toString())+" 次":"旧记录未统计")+"\n来源："+word.optJSONArray("origins")+"\n"+word.getString("source")+"\n"+word.getString("translation"); }
            if(labels.length==0) { new android.app.AlertDialog.Builder(this).setTitle("今日学词").setMessage("今天还没有词条，完成中文输入或接受英文译文后再来复习。").setPositiveButton("知道了",null).show(); return; }
            new android.app.AlertDialog.Builder(this).setTitle("今日学词 · 点击可标记已掌握").setItems(labels,(dialog,index) -> {
                try { String word=words.getJSONObject(index).getString("word"); new android.app.AlertDialog.Builder(this).setTitle(word).setMessage("已掌握这个词？").setPositiveButton("已掌握",(d,w) -> { try { nativeEngine.frame(new JSONObject().put("op","mastered").put("word",word)); } catch(Exception error) { text(error.getMessage(),15); } }).setNegativeButton("继续学习",null).show(); } catch(Exception error) { text(error.getMessage(),15); }
            }).show();
        } catch(Exception error) { text("词本读取失败："+error.getMessage(),15); }
    }

    public void onDestroy() { if(cloudSetup!=null) cloudSetup.close(); if(nativeEngine!=null) nativeEngine.close(); super.onDestroy(); }
}

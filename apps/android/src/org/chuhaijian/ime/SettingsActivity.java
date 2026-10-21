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
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import org.chuhaijian.ime.keyboard.Panel;
import org.json.JSONArray;
import org.json.JSONObject;

/** 配置与主动复习入口；云服务由用户自行选择并提供密钥。 */
public final class SettingsActivity extends Activity {
    private LinearLayout root;
    private NativeBridge nativeEngine;

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); scroll.addView(root); setContentView(scroll);
        text("出海舰",26); text("五笔86 · 拼音 · 英文\n中文输入后 // 翻译，回车确认使用。",17);
        button("启用出海舰键盘",() -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("选择输入法",() -> ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        android.content.SharedPreferences keyboard=getSharedPreferences("keyboard",MODE_PRIVATE);
        text("键盘设置",22);
        int keyHeight=keyboard.getInt("key_height",Panel.DEFAULT_ROW_DP);
        TextView height=text("按键高度："+keyHeight+" dp",16);
        SeekBar size=new SeekBar(this); size.setMax(Panel.MAX_ROW_DP-Panel.MIN_ROW_DP); size.setProgress(keyHeight-Panel.MIN_ROW_DP); root.addView(size);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { height.setText("按键高度："+(Panel.MIN_ROW_DP+progress)+" dp"); if(user) keyboard.edit().putInt("key_height",Panel.MIN_ROW_DP+progress).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        CheckBox preview=new CheckBox(this); preview.setText("按下字母时显示放大预览"); preview.setChecked(keyboard.getBoolean("key_preview",true)); root.addView(preview);
        preview.setOnCheckedChangeListener((view,checked) -> keyboard.edit().putBoolean("key_preview",checked).apply());
        CheckBox haptic=new CheckBox(this); haptic.setText("按键震动"); haptic.setChecked(keyboard.getBoolean("haptic",true)); root.addView(haptic);
        int hapticLevel=keyboard.getInt("haptic_level",30);
        TextView hapticValue=text("震动强度："+hapticLevel,16);
        SeekBar hapticBar=new SeekBar(this); hapticBar.setMax(100); hapticBar.setProgress(hapticLevel); root.addView(hapticBar);
        hapticBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { hapticValue.setText("震动强度："+progress); if(user) keyboard.edit().putInt("haptic_level",progress).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        haptic.setOnCheckedChangeListener((view,checked) -> keyboard.edit().putBoolean("haptic",checked).apply());
        CheckBox sound=new CheckBox(this); sound.setText("按键音"); sound.setChecked(keyboard.getBoolean("key_sound",false)); root.addView(sound);
        int soundLevel=keyboard.getInt("sound_level",40);
        TextView soundValue=text("按键音音量："+soundLevel,16);
        SeekBar soundBar=new SeekBar(this); soundBar.setMax(100); soundBar.setProgress(soundLevel); root.addView(soundBar);
        soundBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { soundValue.setText("按键音音量："+progress); if(user) keyboard.edit().putInt("sound_level",progress).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        sound.setOnCheckedChangeListener((view,checked) -> keyboard.edit().putBoolean("key_sound",checked).apply());
        text("设置即时保存。按键高度返回输入框后生效，震动和按键音下次按下即生效。强度或音量为 0 时相当于关闭。顶栏可选五笔、拼音、英文，点「译」翻译当前段落，点「收起」隐藏键盘。123 打开数字符号。",15);
        android.content.SharedPreferences config=getSharedPreferences("cloud",MODE_PRIVATE);
        text("整句云翻译",22); text("仅在你主动触发翻译时，发送当前中文段落给你配置的服务。密码框不翻译。接口费用由服务商收取。",15);
        text("译成",16);
        String[] languageNames={"英语","日语","西班牙语"}; String[] languageCodes={"en","ja","es"};
        Spinner language=new Spinner(this); language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,languageNames));
        String savedLanguage=config.getString("translation_language","en"); int languageIndex=0; for(int i=0;i<languageCodes.length;i++) if(languageCodes[i].equals(savedLanguage)) languageIndex=i; language.setSelection(languageIndex); root.addView(language);
        CheckBox enabled=new CheckBox(this); enabled.setText("启用云翻译"); enabled.setChecked(config.getBoolean("enabled",false)); root.addView(enabled);
        EditText url=field("兼容接口地址，例如 https://服务商/v1",config.getString("url",""),false);
        EditText model=field("模型名称",config.getString("model",""),false);
        EditText key=field("API 密钥",config.getString("key",""),true);
        TextView state=text("",15);
        button("保存设置",() -> {
            String endpoint=url.getText().toString().trim();
            if(enabled.isChecked() && (!endpoint.startsWith("https://") || model.getText().toString().trim().isEmpty() || key.getText().toString().trim().isEmpty())) { state.setText("请填写 HTTPS 接口、模型和密钥"); return; }
            config.edit().putBoolean("enabled",enabled.isChecked()).putString("url",endpoint).putString("model",model.getText().toString().trim()).putString("key",key.getText().toString().trim()).putString("translation_language",languageCodes[language.getSelectedItemPosition()]).apply(); state.setText("已保存");
        });
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

    public void onDestroy() { if(nativeEngine!=null) nativeEngine.close(); super.onDestroy(); }
}

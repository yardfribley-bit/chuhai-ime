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
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** 配置与主动复习入口；云服务由用户自行选择并提供密钥。 */
public final class SettingsActivity extends Activity {
    private LinearLayout root;
    private NativeBridge nativeEngine;

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); scroll.addView(root); setContentView(scroll);
        text("出海舰",26); text("五笔86 · 拼音 · 混输\n中文输入后 // 翻译，回车确认使用。",17);
        button("启用出海舰键盘",() -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("选择输入法",() -> ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        android.content.SharedPreferences config=getSharedPreferences("cloud",MODE_PRIVATE);
        text("整句云翻译",22); text("仅在你主动触发翻译时，发送当前中文段落给你配置的服务。密码框不翻译。接口费用由服务商收取。",15);
        CheckBox enabled=new CheckBox(this); enabled.setText("启用云翻译"); enabled.setChecked(config.getBoolean("enabled",false)); root.addView(enabled);
        EditText url=field("兼容接口地址，例如 https://服务商/v1",config.getString("url",""),false);
        EditText model=field("模型名称",config.getString("model",""),false);
        EditText key=field("API 密钥",config.getString("key",""),true);
        TextView state=text("",15);
        button("保存设置",() -> {
            String endpoint=url.getText().toString().trim();
            if(enabled.isChecked() && (!endpoint.startsWith("https://") || model.getText().toString().trim().isEmpty() || key.getText().toString().trim().isEmpty())) { state.setText("请填写 HTTPS 接口、模型和密钥"); return; }
            config.edit().putBoolean("enabled",enabled.isChecked()).putString("url",endpoint).putString("model",model.getText().toString().trim()).putString("key",key.getText().toString().trim()).apply(); state.setText("已保存");
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
            for(int i=0;i<words.length();i++) { JSONObject word=words.getJSONObject(i); labels[i]=word.getString("word")+" — "+word.getString("meaning")+"\n"+word.getString("source")+"\n"+word.getString("translation"); }
            if(labels.length==0) { new android.app.AlertDialog.Builder(this).setTitle("今日学词").setMessage("今天还没有新增词条，接受英文译文后再来复习。").setPositiveButton("知道了",null).show(); return; }
            new android.app.AlertDialog.Builder(this).setTitle("今日学词 · 点击可标记已掌握").setItems(labels,(dialog,index) -> {
                try { String word=words.getJSONObject(index).getString("word"); new android.app.AlertDialog.Builder(this).setTitle(word).setMessage("已掌握这个词？").setPositiveButton("已掌握",(d,w) -> { try { nativeEngine.frame(new JSONObject().put("op","mastered").put("word",word)); } catch(Exception error) { text(error.getMessage(),15); } }).setNegativeButton("继续学习",null).show(); } catch(Exception error) { text(error.getMessage(),15); }
            }).show();
        } catch(Exception error) { text("词本读取失败："+error.getMessage(),15); }
    }

    public void onDestroy() { if(nativeEngine!=null) nativeEngine.close(); super.onDestroy(); }
}

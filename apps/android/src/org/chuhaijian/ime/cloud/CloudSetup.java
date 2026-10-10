package org.chuhaijian.ime.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 服务商、模型搜索与连接验证；异步结果只应用到发起请求的配置。 */
public final class CloudSetup extends LinearLayout {
    private final Activity activity;
    private final SharedPreferences saved;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final AutoCompleteTextView provider;
    private final EditText address,key;
    private final TextView status,selection;
    private final Button models,connect,pause;
    private final List<String[]> catalog=new ArrayList<>();
    private String selected="",service="";
    private int revision;
    private boolean closed;
    private static final String[] SERVICES={"OpenRouter","DeepSeek 官方","自定义中转"};

    public CloudSetup(Activity activity) {
        super(activity); this.activity=activity; saved=activity.getSharedPreferences("cloud",Context.MODE_PRIVATE);setOrientation(VERTICAL);
        text("选择接入服务，再选择模型。接口地址自动填写。",16);
        provider=new AutoCompleteTextView(activity);provider.setSingleLine(true);provider.setHint("搜索服务商，例如 openrouter、deepseek");provider.setThreshold(0);
        provider.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_dropdown_item_1line,SERVICES));addView(provider);
        provider.setOnClickListener(v->provider.showDropDown());
        address=edit("中转接口地址（服务商提供）",false);address.setVisibility(GONE);
        selection=text("尚未选择模型",16);
        models=button("搜索并选择模型",this::loadModels);
        key=edit("粘贴此接入服务的 API 密钥",true);
        CheckBox visible=new CheckBox(activity);visible.setText("显示密钥");addView(visible);
        visible.setOnCheckedChangeListener((v,on)->{key.setInputType(InputType.TYPE_CLASS_TEXT|(on?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));key.setSelection(key.length());});
        button("高级：手动填写模型 ID",()->{
            EditText field=new EditText(activity);field.setSingleLine(true);field.setText(selected);
            new AlertDialog.Builder(activity).setTitle("手动填写模型 ID").setView(field).setPositiveButton("使用",(d,w)->{selected=field.getText().toString().trim();selection.setText(selected);revision++;}).setNegativeButton("取消",null).show();
        });
        status=text("",15);
        connect=button("连接并启用",this::test);
        pause=button("暂停云翻译",this::pauseCloud);
        pause.setVisibility(saved.getBoolean("enabled",false)?VISIBLE:GONE);
        text("连接验证会发送一条固定测试消息，可能产生少量接口费用。翻译时仅发送你主动提交的段落；密码框不翻译。",14);
        watch(provider,()->choose(provider.getText().toString()));
        watch(address,()->{revision++;catalog.clear();});watch(key,()->revision++);
        String url=saved.getString("url","");
        String name=url.startsWith("https://openrouter.ai/")?SERVICES[0]:url.startsWith("https://api.deepseek.com")?SERVICES[1]:url.isEmpty()?"":SERVICES[2];
        if(!name.isEmpty()) { provider.setText(name,false);choose(name);address.setText(url);key.setText(saved.getString("key",""));selected=saved.getString("model","");selection.setText(selected.isEmpty()?"尚未选择模型":selected); }
        if(saved.getBoolean("enabled",false))status.setText("当前已启用："+saved.getString("model",""));
    }
    private void pauseCloud(){saved.edit().putBoolean("enabled",false).apply();pause.setVisibility(GONE);status.setText("已暂停，配置仍保留");}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setPadding(0,12,0,12);addView(v);return v;}
    private EditText edit(String hint,boolean password){EditText v=new EditText(activity);v.setSingleLine(true);v.setHint(hint);if(password)v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);addView(v);return v;}
    private Button button(String title,Runnable action){Button v=new Button(activity);v.setText(title);v.setOnClickListener(w->action.run());addView(v);return v;}
    private void watch(EditText v,Runnable action){v.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){action.run();}public void afterTextChanged(Editable e){}});}
    private void choose(String text){
        String name=text.trim();if(name.equalsIgnoreCase("deepseek"))name=SERVICES[1];if(name.equalsIgnoreCase("openrouter"))name=SERVICES[0];
        if(service.equals(name))return;revision++;service=name;selected="";catalog.clear();selection.setText("尚未选择模型");key.setText("");
        boolean custom=name.equals(SERVICES[2]);address.setVisibility(custom?VISIBLE:GONE);
        address.setText(name.equals(SERVICES[0])?"https://openrouter.ai/api/v1":name.equals(SERVICES[1])?"https://api.deepseek.com":"");
        if(name.equals(SERVICES[0]))loadModels();
        else if(name.equals(SERVICES[1]))status.setText("粘贴 DeepSeek 官方密钥后，点“搜索并选择模型”。");
    }
    private String endpoint() throws Exception{
        if(!service.equals(SERVICES[0])&&!service.equals(SERVICES[1])&&!service.equals(SERVICES[2]))throw new Exception("请先选择 OpenRouter、DeepSeek 官方或自定义中转");
        String value=address.getText().toString().trim().replaceAll("/+$","");
        if(value.endsWith("/chat/completions"))value=value.substring(0,value.length()-17);
        URI uri=new URI(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)throw new Exception("请填写服务商提供的 HTTPS 接口地址");return value;
    }
    private void loadModels(){
        if(!catalog.isEmpty()){picker();return;}
        final String base,secret;try{base=endpoint();secret=key.getText().toString().trim();}catch(Exception e){status.setText(e.getMessage());return;}
        if(!service.equals(SERVICES[0])&&secret.isEmpty()){status.setText("先粘贴此服务的密钥，再获取模型列表");return;}
        int token=revision;status.setText("正在获取模型…");models.setEnabled(false);
        network.execute(()->{try{
            JSONArray data=request(base+"/models",secret,null).getJSONArray("data");List<String[]> found=new ArrayList<>();
            for(int i=0;i<data.length();i++){JSONObject m=data.getJSONObject(i);String id=m.optString("id");if(id.isEmpty())continue;
                JSONObject arch=m.optJSONObject("architecture");if(arch!=null&&arch.has("output_modalities")&&!arch.getJSONArray("output_modalities").toString().contains("\"text\""))continue;
                found.add(new String[]{id,m.optString("name",id)});
            }
            activity.runOnUiThread(()->{models.setEnabled(true);if(closed||token!=revision)return;catalog.clear();catalog.addAll(found);status.setText(found.isEmpty()?"没有可选模型，可在高级选项中手动填写":"已获取 "+found.size()+" 个模型");picker();});
        }catch(Exception e){activity.runOnUiThread(()->{models.setEnabled(true);if(!closed&&token==revision)status.setText(e.getMessage()+"。可重试或手动填写模型 ID。");});}});
    }
    private void picker(){
        if(catalog.isEmpty())return;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(VERTICAL);EditText query=new EditText(activity);query.setSingleLine(true);query.setHint("搜索模型名称，例如 deepseek");box.addView(query);
        ListView list=new ListView(activity);box.addView(list,new LinearLayout.LayoutParams(-1,Math.round(340*getResources().getDisplayMetrics().density)));
        List<String[]> filtered=new ArrayList<>(catalog);ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1);list.setAdapter(adapter);
        Runnable refresh=()->{String q=query.getText().toString().toLowerCase(Locale.ROOT).trim();filtered.clear();adapter.clear();for(String[] m:catalog)if((m[0]+" "+m[1]).toLowerCase(Locale.ROOT).contains(q)){filtered.add(m);adapter.add(m[1]+"\n"+m[0]);}adapter.notifyDataSetChanged();};watch(query,refresh);refresh.run();
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("选择模型 · "+service).setView(box).setNegativeButton("取消",null).create();
        list.setOnItemClickListener((v,row,pos,id)->{String[] model=filtered.get(pos);selected=model[0];selection.setText(model[1]+"\n"+model[0]);revision++;dialog.dismiss();});dialog.show();
    }
    private void test(){
        final String base,secret,model=selected;try{base=endpoint();secret=key.getText().toString().trim();if(secret.isEmpty())throw new Exception("请粘贴此接入服务的密钥");if(model.isEmpty())throw new Exception("请先选择模型");}catch(Exception e){status.setText(e.getMessage());return;}
        int token=revision;connect.setEnabled(false);status.setText("正在验证连接…");
        network.execute(()->{try{
            JSONObject body=new JSONObject().put("model",model).put("max_tokens",64).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","Reply with OK only.")));
            JSONObject result=request(base+"/chat/completions",secret,body);
            if(result.optJSONArray("choices")==null||result.getJSONArray("choices").length()==0)throw new Exception("服务没有返回有效结果，请换个模型重试");
            activity.runOnUiThread(()->{connect.setEnabled(true);if(closed||token!=revision){if(!closed)status.setText("配置已变化，请重新连接");return;}saved.edit().putString("url",base).putString("model",model).putString("key",secret).putBoolean("enabled",true).apply();pause.setVisibility(VISIBLE);status.setText("已连接并启用 · "+model);});
        }catch(Exception e){activity.runOnUiThread(()->{connect.setEnabled(true);if(!closed&&token==revision)status.setText(e.getMessage()+"，原有配置保持不变");});}});
    }
    private JSONObject request(String endpoint,String secret,JSONObject body) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);
        try{
            if(!secret.isEmpty())c.setRequestProperty("Authorization","Bearer "+secret);
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(java.io.OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code!=200)throw new Exception(code==401?"密钥无效或与接入服务不匹配":code==402?"账户余额不足":code==403?"此密钥没有访问权限":code==429?"请求太频繁，请稍后重试":code==404?"接口或模型未找到":code>=300&&code<400?"地址发生跳转，请核对接口地址":"服务暂不可用（"+code+"）");
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=c.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>8*1024*1024)throw new Exception("服务返回内容过大");out.write(bytes,0,n);}}return new JSONObject(out.toString("UTF-8"));
        }catch(java.io.IOException e){throw new Exception("网络连接失败，请检查网络后重试");}finally{c.disconnect();}
    }
    public void close(){closed=true;revision++;network.shutdownNow();}
}

package org.chuhaijian.ime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** 向兼容接口请求一次整句翻译。密钥只放在请求头里。 */
final class CloudChat {
    final String sentence;

    final JSONArray terms;

    private CloudChat(String sentence, JSONArray terms) {
        this.sentence = sentence;
        this.terms = terms;
    }

    static CloudChat request(String endpoint, String model, String key, String input, String system, String target) throws Exception {
        HttpURLConnection http = null;
        try {
            String url = endpoint.replaceAll("/+$", "");
            if (!url.endsWith("/chat/completions")) url += "/chat/completions";
            http = (HttpURLConnection) new URL(url).openConnection();
            http.setRequestMethod("POST");
            http.setConnectTimeout(15000);
            http.setReadTimeout(30000);
            http.setInstanceFollowRedirects(false);
            http.setDoOutput(true);
            http.setRequestProperty("Authorization", "Bearer " + key);
            http.setRequestProperty("Content-Type", "application/json");
            JSONArray messages = new JSONArray().put(new JSONObject().put("role", "system").put("content", system)).put(new JSONObject().put("role", "user").put("content", new JSONObject().put("text", input).put("target_language", target).toString()));
            byte[] body = new JSONObject().put("model", model).put("messages", messages).put("max_tokens", 1500).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = http.getOutputStream()) { out.write(body); }
            if (http.getResponseCode() != 200) throw new IllegalStateException("接口返回 " + http.getResponseCode());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream in = http.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (output.size() + count > 1048576) throw new IllegalStateException("接口回复过大");
                    output.write(buffer, 0, count);
                }
            }
            JSONObject result = new JSONObject(new JSONObject(output.toString("UTF-8")).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"));
            String text = result.getString("sentence").trim();
            if (text.isEmpty()) throw new IllegalStateException("译文为空");
            return new CloudChat(text, result.optJSONArray("terms"));
        } finally {
            if (http != null) http.disconnect();
        }
    }
}

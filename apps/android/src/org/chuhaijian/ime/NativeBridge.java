package org.chuhaijian.ime;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** 系统壳与共用 Rust 内核之间的 UTF-8 传输。 */
final class NativeBridge implements AutoCloseable {
    static { System.loadLibrary("chuhai"); }
    private long handle;
    private static native long create(byte[] directory);
    private static native byte[] callBytes(long handle, byte[] message);
    private static native void destroy(long handle);

    NativeBridge(Context context) throws Exception {
        File directory = context.getFilesDir();
        for (String name : new String[]{"dict.qj", "wubi86.tsv"}) {
            File file = new File(directory, name);
            if (!file.exists()) {
                File temporary = new File(directory, name + ".tmp");
                try (InputStream input = context.getAssets().open(name); FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                if (!temporary.renameTo(file)) throw new IllegalStateException("词库初始化失败");
            }
        }
        handle = create(directory.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
        if (handle == 0) throw new IllegalStateException("输入内核启动失败，请检查词库");
    }

    String call(JSONObject message) throws Exception {
        if (handle == 0) throw new IllegalStateException("输入内核已关闭");
        byte[] result = callBytes(handle, message.toString().getBytes(StandardCharsets.UTF_8));
        if (result == null) throw new IllegalStateException("输入内核返回失败");
        return new String(result, StandardCharsets.UTF_8);
    }

    JSONObject frame(JSONObject message) throws Exception {
        JSONObject result = new JSONObject(call(message));
        if (result.has("error")) throw new IllegalStateException(result.getString("error"));
        return result;
    }

    public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
}

package org.chuhaijian.ime.keyboard;

import android.content.Context;
import android.media.AudioManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;

/** 按下时的震动和按键音。系统关闭触摸反馈或音效时不再播放。 */
final class Feedback {
    void play(View view, String key) {
        Context context = view.getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE);
        if (prefs.getBoolean("haptic", true) && systemHapticEnabled(context)) vibrate(context, prefs.getInt("haptic_level", 30));
        if (prefs.getBoolean("key_sound", false)) playSound(context, key, prefs.getInt("sound_level", 40));
    }

    private static boolean systemHapticEnabled(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED) != 0;
        } catch (Settings.SettingNotFoundException error) {
            return true;
        }
    }

    private static void vibrate(Context context, int level) {
        if (level <= 0) return;
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int amplitude = VibrationEffect.DEFAULT_AMPLITUDE;
        if (vibrator.hasAmplitudeControl()) amplitude = Math.max(1, Math.min(255, level * 255 / 100));
        int duration = 15 + level / 4;
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
        } catch (RuntimeException ignored) {
            // 没有震动权限或设备拒绝时，按键本身仍然有效。
        }
    }

    private static void playSound(Context context, String key, int level) {
        if (level <= 0) return;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return;
        int effect = AudioManager.FX_KEYPRESS_STANDARD;
        if ("DELETE".equals(key)) effect = AudioManager.FX_KEYPRESS_DELETE;
        else if ("SPACE".equals(key)) effect = AudioManager.FX_KEYPRESS_SPACEBAR;
        else if ("ENTER".equals(key)) effect = AudioManager.FX_KEYPRESS_RETURN;
        audio.playSoundEffect(effect, Math.max(0f, Math.min(1f, level / 100f)));
    }
}

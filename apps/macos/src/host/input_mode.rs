//! 输入预设切换、按键策略及候选缓冲区收尾。
use super::Host;
use qingjian_platform::{Config, InputMode};

impl Host {
    pub fn select_input_mode(&mut self, mode: InputMode) {
        let Some(path) = self.settings.path() else {
            return;
        };
        match Config::set_input_mode(path, mode) {
            Ok(()) => {
                self.pending_input_mode = true;
                self.end_translation();
                self.settings.reload();
                self.apply_config(false);
                self.indicator.set_input_mode(
                    Some(mode),
                    self.settings.config().general.chinese_input_mode(),
                );
                self.indicator.update();
            }
            Err(error) => self.show_notice(&format!("输入模式保存失败：{error}"), self.anchor),
        }
    }

    pub fn poll_caps_lock(&mut self) {
        self.poll_model_list();
        if self.last_encounter_flush.elapsed() >= std::time::Duration::from_secs(10) {
            self.record_learning_encounters();
            self.last_encounter_flush = std::time::Instant::now();
        }
        if self.indicator.caps_changed() {
            self.toggle_caps_mode();
        }
        let config = self.settings.config();
        self.indicator.set_input_mode(
            config.general.input_mode,
            config.general.chinese_input_mode(),
        );
    }
    /// 直接读取事件修饰键，避免短按时轮询错过或晚于首个字符。
    pub fn observe_caps_event(&mut self, caps: bool) {
        if self.indicator.observe_caps(caps) {
            self.toggle_caps_mode();
        }
    }

    fn toggle_caps_mode(&mut self) {
        let config = self.settings.config();
        let mode = config.shortcut.caps_lock_behavior.next(
            config.general.current_input_mode(),
            config.general.chinese_input_mode(),
        );
        tracing::info!(?mode, "Caps Lock 切换输入模式");
        self.select_input_mode(mode);
    }
}

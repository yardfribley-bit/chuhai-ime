//! Windows 上用户目录的约定，Server / TSF DLL / 设置程序三处共用：
//!
//! - **数据** `%APPDATA%\AgentReins`：配置、密钥、学习数据、统计、输入日志（漫游目录，跟着账户走）。
//! - **日志** `%LOCALAPPDATA%\AgentReins\logs`：三个进程的运行日志都在这一个目录里，
//!   `server.<日期>.log` / `tsf.<日期>.log` / `settings.<日期>.log`，用户反馈问题时整个目录打包即可。
//!   放本机目录有两个原因：日志本来就是这台机器的东西，不该跟账户漫游；DLL 被加载进
//!   AppContainer 应用（任务栏搜索 / 设置）时写不了漫游目录，Server 启动时会给这个目录授权。
//!
//! 只查环境变量、不碰系统 API，其他平台也能编（拿不到为 `None`）。macOS 有自己的 `paths.rs`，不走这里。

use std::path::{Path, PathBuf};

const DIR_NAME: &str = "AgentReins";
const PREVIOUS_DIR_NAME: &str = "Qingjian";

/// 产品数据目录。新目录还不存在、旧目录在时，把旧目录改名过来。
pub fn claim(parent: impl AsRef<Path>) -> PathBuf {
    let current = parent.as_ref().join(DIR_NAME);
    let previous = parent.as_ref().join(PREVIOUS_DIR_NAME);
    if !current.exists() && previous.is_dir() && std::fs::rename(&previous, &current).is_err() {
        return previous;
    }
    current
}

/// 用户数据目录 `%APPDATA%\AgentReins`。
pub fn user_dir() -> Option<PathBuf> {
    std::env::var_os("APPDATA").map(|base| claim(PathBuf::from(base)))
}

/// 配置文件 `%APPDATA%\AgentReins\config.toml`。
pub fn config_path() -> Option<PathBuf> {
    user_dir().map(|dir| dir.join("config.toml"))
}

/// 运行日志目录 `%LOCALAPPDATA%\AgentReins\logs`，不负责创建。
pub fn log_dir() -> Option<PathBuf> {
    std::env::var_os("LOCALAPPDATA").map(|base| claim(PathBuf::from(base)).join("logs"))
}

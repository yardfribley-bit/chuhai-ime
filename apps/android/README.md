# 出海舰 Android 测试版

系统 InputMethodService 键盘，使用共用 Rust 内核提供拼音、五笔86和混输。API 26+，打包 ARM64 和 x86_64 原生库。

键盘“方案”键依次切换五笔86、拼音、混输；中/英键切换直接英文输入。中文完成选词后输入 `//`，显示整句英文预览，回车替换、取消保留原文。“设置 → 今日学词”主动查看新词，可标记已掌握。密码、PIN 和声明不学习的输入框不触发云翻译。

云翻译配置保存在应用私有目录；仅使用 HTTPS，不跟随重定向发送密钥。测试版签名仅供验证，正式发布需替换发布签名。

构建需要 Rust、JDK、Android SDK（platform android-34、build-tools 35.0.0）与 NDK。安装 Rust 目标：

```sh
rustup target add aarch64-linux-android x86_64-linux-android
export ANDROID_SDK_ROOT=/path/to/android/sdk
export ANDROID_NDK_HOME=/path/to/android-ndk
export JAVA_HOME=/path/to/jdk
export CHUHAI_DATA_DIR=/path/to/product/resources
python3 apps/android/build.py
```

数据目录包含 dict.qj、wubi/wubi86.tsv，来源与许可需随包保留。当前脚本的 NDK 主机工具目录为 macOS darwin-x86_64；其他主机需要按 NDK 目录调整。

APK 在 target/android/出海舰-0.1.0-test.apk。手机安装后到系统键盘设置启用“出海舰”，再切换输入法。验证拼音 nihao、五笔 wq、混输、切换应用、密码框、联网失败、取消、光标移动和今日学词。

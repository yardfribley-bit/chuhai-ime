# 出海舰 Android 测试版

系统 InputMethodService 键盘，使用共用 Rust 内核提供拼音、五笔86和直接英文输入。API 26+，打包 ARM64 和 x86_64 原生库。

点左上角模式菜单选择“五笔／拼音／英文”；中/英键在英文和上一次中文模式之间切换。中文完成选词后输入 `//`，显示整句英文预览，回车替换、取消保留原文。“设置 → 今日学词”主动查看候选提示和已接受译文中的词、中文含义、原句及今日出现次数，可标记已掌握。同一输入片段中的候选重绘不重复计数，新的已完成输入片段会重新计数。密码、PIN 和声明不学习的输入框不触发云翻译。

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

数据目录包含 dict.qj、glossary-en.qj、wubi/wubi86.tsv，来源与许可需随包保留。当前脚本的 NDK 主机工具目录为 macOS darwin-x86_64；其他主机需要按 NDK 目录调整。

APK 在 target/android/出海舰-0.1.3.apk。手机安装后到系统键盘设置启用“出海舰”，再切换输入法。验证拼音 nihao、五笔 wq、英文直输、切换应用、密码框、联网失败、取消、光标移动和今日学词。

## 0.1.2 键盘调整

采用常用手机全键盘布局：QWERTY 三行字母、第二行缩进半键、第三行 Shift 与退格、底部大空格和中英切换。123 打开数字符号页，ABC 返回字母。单击 Shift 临时大写，双击锁定，再点关闭；长按退格连续删除，字母按下显示放大预览。设置中可调按键高度 50–70 dp、关闭放大预览。回车跟随输入框显示搜索、发送或换行；有未确认编码时先提交编码。

按键视觉间距不缩小触摸区域。五种屏宽下的字母/符号页面覆盖检查通过，APK 编译和签名校验通过；误触改善仍需真实手机确认，不包含拼音纠错或联想模型改动。新版沿用测试签名，可覆盖安装；建议先在备忘录连续输入并测试 Shift、长按退格、符号及横屏。

独立布局检查（仅需 JDK）：

```sh
javac -d /tmp/chuhai-keyboard-test apps/android/src/org/chuhaijian/ime/keyboard/{Key,Layout}.java apps/android/tests/org/chuhaijian/ime/keyboard/LayoutTest.java
java -cp /tmp/chuhai-keyboard-test org.chuhaijian.ime.keyboard.LayoutTest
```

## 0.1.3 全键盘易用性调整

保持标准 26 键位置，字母统一显示大写键帽（中文编码仍为小写），默认行高由 58 调到 62 dp；已手动设置的高度保留。缩小键帽之间的视觉空隙、增大字母显示，完整触摸边界不变。底行从左到右为中/英、123、逗号、大空格、句号、回车；空格占整行 40%。取消按钮只在翻译期间显示。

纯 Java/UI 调整可以用 `python3 apps/android/build.py --reuse-native` 复用上次构建的两种 ABI 原生库；涉及 Rust 或 JNI 的修改必须完整构建。0.1.3 沿用本机测试签名和包名，可覆盖安装。该版本不包含新的拼音纠错算法，误触效果仍需手机实测。

0.1.3 对外版本名不带 test 后缀，versionCode 5；沿用原有签名用于覆盖安装。

## 0.1.4 模型配置

选择 OpenRouter、DeepSeek 官方或自定义中转，按名称搜索并选择模型，粘贴该服务的密钥后连接并启用。官方地址自动填写；中转地址仅需填写一次。模型列表失败可重试或手动填写模型 ID。失败保留表单内容，配置变化后旧测试结果不会启用新配置。0.1.4（versionCode 6）沿用原包名和签名。

# AgentReins Android 测试版

系统 InputMethodService 键盘，使用共用 Rust 内核提供拼音、五笔86和直接英文输入。API 26+，打包 ARM64 和 x86_64 原生库。

顶栏无编码时是模式、译、设置和收起；有编码时同一行变为候选，编码显示在上方细条。点模式选择“五笔／拼音／英文”；中/英键在英文和上一次中文模式之间切换。中文完成选词后输入 `//`，或点「译」，按设置里所选的英语、日语或西班牙语显示整句预览。复制来的英文在段落末尾输入 `//`，直接预览中文。预览在键盘上的卡片里，回车或「替换」替换，「取消」保留原文。“设置 → 今日学词”主动查看候选提示和已接受译文中的词、中文含义、原句及今日出现次数，可标记已掌握。同一输入片段中的候选重绘不重复计数，新的已完成输入片段会重新计数。密码、PIN 和声明不学习的输入框不触发云翻译。

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

APK 在 target/android/AgentReins-0.1.2-test.apk。手机安装后到系统键盘设置启用“AgentReins”，再切换输入法。验证拼音 nihao、五笔 wq、英文直输、切换应用、密码框、联网失败、取消、光标移动和今日学词。

## 0.1.2 键盘调整

采用常用手机全键盘布局：QWERTY 三行字母、第二行缩进半键、第三行 Shift 与退格、底部大空格和中英切换。123 打开数字符号页，ABC 返回字母。单击 Shift 临时大写，双击锁定，再点关闭；长按退格连续删除，字母按下显示放大预览。设置中可调按键高度 50–70 dp、关闭放大预览。回车跟随输入框显示搜索、发送或换行；有未确认编码时先提交编码。

按键视觉间距不缩小触摸区域。五种屏宽下的字母/符号页面覆盖检查通过，APK 编译和签名校验通过；误触改善仍需真实手机确认，不包含拼音纠错或联想模型改动。新版沿用测试签名，可覆盖安装；建议先在备忘录连续输入并测试 Shift、长按退格、符号及横屏。

## 自绘键盘与顶栏

按键改为一块自绘视图：按下就有视觉反馈；没形成滑动时抬起落到最近的键，尚未绑定动作的滑动仍提交按下的键，两指交替按按下顺序上屏。默认行高 56 dp，顶栏 44 dp，有编码时再加 18 dp 编码条，默认总高不超过 290 dp。震动默认轻开，按键音默认关，都在设置里调节，0 为关。上滑出符号和空格滑动移光标只做了判定，还没有绑定动作。

独立布局与手势检查（仅需 JDK）：

```sh
javac -encoding UTF-8 -d /tmp/chuhai-keyboard-test \
  apps/android/src/org/chuhaijian/ime/keyboard/{Key,Layout,Panel,Stroke,Tracker}.java \
  apps/android/tests/org/chuhaijian/ime/keyboard/LayoutTest.java
java -cp /tmp/chuhai-keyboard-test org.chuhaijian.ime.keyboard.LayoutTest
```

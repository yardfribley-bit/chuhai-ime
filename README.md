# 出海舰 · Chuhai IME

面向中文使用者的开源输入法：用五笔或拼音表达，再把整段中文翻成英文。适合在 X 发帖、跨语言聊天和专业写作。

**先把输入做好，再帮助表达与学习。** 中文完成选词后，输入 `//` 触发英文翻译；查看预览，按回车确认替换。有空时主动打开“今日学词”，查看当天遇到的词和出现次数。

**官网与统一下载入口：[www.cyberstroll.top](https://www.cyberstroll.top/)**

## 下载安装包

推荐前往 [官网下载页](https://www.cyberstroll.top/#download)，选择 Mac、Windows 或 Android 直接下载安装文件，无需编译源码。下表保留 GitHub 备用下载链接。

| 平台 | 直接下载 | 系统要求与测试状态 |
| --- | --- | --- |
| Windows | [下载安装程序（.exe，约 99 MB）](https://github.com/yardfribley-bit/chuhai-ime/releases/download/v0.1.5-dev-crossplatform/chuhai-0.1.5-dev-597b2da-windows-x86_64-setup.exe) | Windows 10 1809+，x64；编译与自动测试通过，待真机验收 |
| Android | [下载安装包（.apk，约 15 MB）](https://github.com/yardfribley-bit/chuhai-ime/releases/download/android-v0.1.2-test/chuhai-ime-0.1.2-test-android.apk) | Android 8.0+，ARM64 / x86_64；已验证基本输入可用 |
| macOS Intel | [下载安装包（.pkg，约 102 MB）](https://github.com/yardfribley-bit/chuhai-ime/releases/download/v0.1.5-dev-build238/chuhai-ime-0.1.5-build238-macos-x86_64.pkg) | Intel Mac，build238 测试包；比当前源码旧，不包含后续三模式切换与次数统计改动 |

Apple Silicon Mac 暂无预编译安装包，需自行编译。以上均为测试版：Windows 包未购买代码签名证书，Mac 包未公证，Android 使用测试签名。

[Windows / Android 发布说明与校验和](https://github.com/yardfribley-bit/chuhai-ime/releases/tag/v0.1.5-dev-crossplatform) · [Mac 发布说明与校验和](https://github.com/yardfribley-bit/chuhai-ime/releases/tag/v0.1.5-dev-build238) · [全部版本](https://github.com/yardfribley-bit/chuhai-ime/releases)

Windows 运行 `.exe` 安装后选择出海舰输入法；Android 安装 `.apk` 后，在系统键盘设置中启用并选择“出海舰”。翻译功能按需配置自己的接口和密钥，安装包不包含个人密钥。

## 快速使用（macOS）

### 1. 安装并选择出海舰

[下载 Mac Intel 测试包（build238）](https://github.com/yardfribley-bit/chuhai-ime/releases/tag/v0.1.5-dev-build238)。Apple Silicon 暂需自行编译，尚未完成本机验收。测试包未公证，安装时按 macOS 的安全提示操作。

安装 Mac 测试包后，在“系统设置 → 键盘 → 文本输入 → 编辑”中添加并选择“出海舰”。若输入源未立即出现，请注销后重新登录。

菜单提供 **五笔／拼音／英文** 三种模式。Mac 的 **Caps Lock** 默认切换中英文，并保留上一次中文方案；通用设置也可选择按 Caps Lock 依次切换三个模式。大写英文按住 Shift。Windows 默认单击 Shift 切换中英文，设置中可选择三种预设；Android 在左上角菜单选择三种模式，底部提供中/英切换键。

- 五笔86：输入 `inim`，选择“漏洞”。
- 拼音：输入 `loudong`，选择“漏洞”。
- 按空格选择首选候选，或按数字选择对应候选。输入方案在“偏好设置 → 通用”中选择。

输入法菜单提供 **“使用指南…”**，应用内随时可查看完整操作步骤。

### 2. 配置翻译服务

在“偏好设置 → 云服务”启用服务，填写兼容接口地址、模型和 API 密钥。每个输入框按回车保存，然后点击“测试连接”。

OpenRouter 配置示例：

| 项目 | 填写内容 |
| --- | --- |
| 接口地址 | `https://openrouter.ai/api/v1` |
| 模型 | `deepseek/deepseek-v3.2`（或服务商提供的其他兼容模型） |
| API 密钥 | 在本机密钥输入框填写自己的密钥 |

密钥需要可用额度。云翻译费用由所选服务商收取；出海舰不提供免费额度或自建翻译服务器。[OpenRouter 接口说明](https://openrouter.ai/docs/quickstart)

### 3. 中文 → 英文

1. 输入一段中文，先完成选词，让中文出现在文本框里。
2. 将光标放在当前段落末尾，连续输入两个**半角斜杠 `//`**。
3. 等待英文预览，按 **Enter** 确认替换。
4. 按 **Esc** 取消；取消、失败或超时会保留原文。

```text
输入：我们正在修复一个漏洞。//
预览：We are fixing a vulnerability.
按 Enter：英文替换当前中文段落和触发斜杠。
```

译文由所选模型生成，实际措辞可能不同。第一个 `/` 正常输入，第二个 `/` 才触发翻译。等待时不要移动光标或切换应用；长文章请分段输入。部分应用不支持读取或替换前文，仍需逐应用验证兼容性。

翻译提示词包含网络安全术语约束，并要求保留 CVE、IP、域名、命令、代码和产品名。专业内容仍需检查译文。

### 4. 今日学词

从输入法菜单打开 **“今日学词…”**，查看已完成输入中的英文候选提示和已接受译文中的词或短语、中文含义、原句及今日出现次数。同一输入片段的候选重绘不重复计数；新的已完成输入片段重新计数，译文按实际出现次数累计。取消的译文不计入词本，不会在打字时弹出复习提醒。

候选词含义来自本地词典，译文词条含义由配置的翻译服务生成；“新增”表示首次记录，不代表用户此前不认识这个词。

## 常见问题

| 情况 | 检查方式 |
| --- | --- |
| 想直接输入英文 | Mac 按 Caps Lock，确认菜单栏显示“英文” |
| 输入 `/` 没有翻译 | 中文先完成选词，再在段落末尾连续输入 `//` |
| 一直等待或翻译失败 | 在云服务页测试连接，检查网络、模型、密钥限额与服务商状态；按 Esc 退出 |
| 有余额仍然失败 | 请求可能超时；账户余额与单个密钥的限额也可能不同 |
| 不希望替换当前中文 | 按 Esc 取消，保留原文 |
| 找不到今日新词 | 完成带英文提示的中文输入，或接受整句译文，再打开今日学词；没有有效词条时不会凭空新增 |

完整说明见 [快速使用指南](docs/user/getting-started/quick-start.md) 和 [按键说明](docs/user/getting-started/keys.md)。

## 平台状态

| 平台 | 输入方案 | 当前状态 |
| --- | --- | --- |
| macOS | 五笔86、拼音、混输 | 本机已安装，用户已确认中文翻译及英文输出可用；使用指南已加入界面 |
| Windows | 五笔86、拼音、英文预设及高级混输配置 | x64 安装包已通过 Windows 构建、静态检查和测试，待真机验收 |
| Android | 五笔86、拼音、英文 | 已生成 ARM64 / x86_64 测试 APK，仍需手机验收 |

当前是开发测试版。编译通过不等于所有应用兼容，Windows 与 Android 尚不应视为正式可用版本。跨平台工程状态见 [开发记录](docs/notes/chuhai-cross-platform.md)，设备验收见 [测试步骤](docs/notes/chuhai-device-test.md)。

## 从源码构建

需要 Rust。Mac 构建还需 Xcode Command Line Tools；运行以下命令生成安装包：

```sh
tools/release/data-fetch.sh
cargo test --release -p qingjian-macos -p qingjian-predict --locked
apps/macos/scripts/bundle.sh --pkg
```

打包需要产品词库及模型资源；`data/` 为生成数据，不在源码仓库中。`tools/release/data-fetch.sh` 从上游发布渠道下载产品数据并按锁定哈希校验。也可以自行生成，方法及资源许可见 [工程说明](docs/notes/crate-notes.md) 和各 `assets/` 子目录说明。缺少资源时不能仅凭源码直接得到与本机测试包相同的完整安装包。

- [Windows 构建与安装](apps/windows/installer/README.md)
- [Android 构建](apps/android/README.md)
- [开发约定](docs/contributing.md)

## 隐私与开源许可

云翻译会把待译原文发送给你配置的服务商；启用云联想后，组句上下文也会发送给该服务商。密码及私密输入框不发送云请求。今日词本、个人词频和 API 密钥保存在本机。

请勿把 API 密钥、个人配置、输入日志或词本提交到 GitHub。

出海舰基于 [Qingjian 0.1.4](https://github.com/qingjian-team/qingjian) 修改，遵循 **GPL-3.0-or-later**。必要的原作者署名、数据许可及历史内部标识保留，详情见 [NOTICE](NOTICE.md) 和 [LICENSE](LICENSE)。产品界面使用“出海舰”；当前图标仍沿用上游资源，正式发布前计划替换。

### Windows / Android 测试安装包

从 [跨平台测试版下载页](https://github.com/yardfribley-bit/chuhai-ime/releases/tag/v0.1.5-dev-crossplatform) 下载 Windows x64 setup.exe 或 Android APK（新版见上方下载表）。Windows 包包含 32 位应用输入接口和设置界面运行时；Android 支持 Android 8.0+、ARM64 / x86_64，已由用户在手机验证基本输入可用。两个测试包均不包含个人密钥，翻译按需自行配置。Windows 仍需真机验收，编译成功不代表已完成兼容性测试。

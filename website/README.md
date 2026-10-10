# 出海舰官网

原生 HTML / CSS / JavaScript，无构建依赖。域名：www.cyberstroll.top。网页只展示产品、安装说明和下载入口，不读取访客输入，不调用模型服务。

本地预览：`python3 -m http.server 8768 --directory website`。

安装包与页面分开部署：页面位于 `/var/www/chuhai-ime/current`，安装包位于 `/var/www/chuhai-ime/downloads`，下载前按发布清单核验 SHA256。只发布 index.html 与 assets，不发布部署脚本或 Git 工作目录。Nginx 独立虚拟主机模板见 deploy/nginx-http.conf；上线前 nginx -t，DNS 指向新服务器后签发 www.cyberstroll.top 的 HTTPS 证书。

Mac 当前附件为 build238，早于三模式与每日次数统计改动；Windows 已通过云构建与自动测试，待真机验收；Android 用户确认基本输入可用。更新版本时同时修改首页文案、下载文件名与校验和，不使用 GitHub 临时签名地址。

## 已部署状态（2026-10-10）

用户指定 Ubuntu 服务器 165.154.254.180，页面版本 24c6525，Nginx 配置 `/etc/nginx/conf.d/chuhai-ime.conf`。已验证三个附件的 SHA256、HTTP 200 与 Range 206；Windows 魔数 MZ 正确。原有 AgentReins 虚拟主机保持原配置。域名 www 的单条 A 记录已通过火山引擎 API 更新为 165.154.254.180，TTL 300 秒。独立 Let’s Encrypt 证书已签发并部署，HTTP 自动跳转 HTTPS，证书到期日 2027-01-08，Certbot 自动续期定时器已启用。仅修改 www，不修改根域及其他子域。安装包固定源与哈希见 deploy/packages.json。

证书名称为 `chuhai-www.cyberstroll.top`；维护时显式指定 `--cert-name`，避免覆盖服务器其他证书。续期检查：`sudo certbot renew --cert-name chuhai-www.cyberstroll.top --dry-run --no-random-sleep-on-renew`。SSH 密码、DNS 密钥和模型密钥不得写入仓库。

2026-10-11：Android 更新至 0.1.2-test（标准键位、放大预览、长按退格、可调高度）；沿用旧版测试签名，实际手感待用户验收。固定下载信息见 packages.json。

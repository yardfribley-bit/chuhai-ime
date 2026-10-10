# 出海舰官网

原生 HTML / CSS / JavaScript，无构建依赖。域名：www.cyberstroll.top。网页只展示产品、安装说明和下载入口，不读取访客输入，不调用模型服务。

本地预览：`python3 -m http.server 8768 --directory website`。

安装包与页面分开部署：页面位于 `/var/www/chuhai-ime/current`，安装包位于 `/var/www/chuhai-ime/downloads`，下载前按发布清单核验 SHA256。只发布 index.html 与 assets，不发布部署脚本或 Git 工作目录。Nginx 独立虚拟主机模板见 deploy/nginx-http.conf；上线前 nginx -t，DNS 指向新服务器后签发 www.cyberstroll.top 的 HTTPS 证书。

Mac 当前附件为 build238，早于三模式与每日次数统计改动；Windows 已通过云构建与自动测试，待真机验收；Android 用户确认基本输入可用。更新版本时同时修改首页文案、下载文件名与校验和，不使用 GitHub 临时签名地址。

## 已部署状态（2026-10-10）

用户指定 Ubuntu 服务器 165.154.254.180，页面版本 24c6525，Nginx 配置 `/etc/nginx/conf.d/chuhai-ime.conf`。已验证三个附件的 SHA256、HTTP 200 与 Range 206；Windows 魔数 MZ 正确。原有 AgentReins 虚拟主机保持原配置。域名 www 初始指向 165.154.226.119，需在火山引擎将 www 的 A 记录改至 165.154.254.180 后再签发证书。仅修改 www，不修改根域及其他子域。安装包固定源与哈希见 deploy/packages.json。

DNS 生效后，在服务器执行 `sudo certbot --nginx -d www.cyberstroll.top --redirect`，按提示完成账户设置；随后检查 HTTPS 首页、三个下载链接及 `certbot renew --dry-run`。SSH 密码、DNS 密钥和模型密钥不得写入仓库。

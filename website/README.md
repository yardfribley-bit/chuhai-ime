# 出海舰官网

原生 HTML / CSS / JavaScript，无构建依赖。域名：www.cyberstroll.top。网页只展示产品、安装说明和下载入口，不读取访客输入，不调用模型服务。

本地预览：`python3 -m http.server 8768 --directory website`。

安装包与页面分开部署：页面位于 `/var/www/chuhai-ime/current`，安装包位于 `/var/www/chuhai-ime/downloads`，下载前按发布清单核验 SHA256。只发布 index.html 与 assets，不发布部署脚本或 Git 工作目录。Nginx 独立虚拟主机模板见 deploy/nginx-http.conf；上线前 nginx -t，DNS 指向新服务器后签发 www.cyberstroll.top 的 HTTPS 证书。

Mac 当前附件为 build238，早于三模式与每日次数统计改动；Windows 已通过云构建与自动测试，待真机验收；Android 用户确认基本输入可用。更新版本时同时修改首页文案、下载文件名与校验和，不使用 GitHub 临时签名地址。

# 站点地址核查（2026-10-18）

核查来源：用户的 `site-nav`、`nav-worker`、`blog`、`pengyuyan29.github.io` 与 `CF-Server-Monitor` 仓库，以及公开只读接口。

1. `site-nav` / `nav-worker` README 指定 `88.202991001.xyz` 是站点导航域名，公开接口为 `/api/sites`。
2. 读取该导航接口，CF 探针条目地址是 `https://11.202991001.xyz/`；`66.202991001.xyz` 条目标为「健康检测」。
3. 对 11 的 `/api/config` 实际读取到标题 `Linxiz Control`、公开站点、开启 Turnstile。此检查未登录、未修改线上设置、未绕过人机验证。
4. 导航给出的博客地址为 `https://pengyuyan29.github.io/blog/`；工具箱为 `https://pengyuyan29.github.io/singbox-config-builder/`。
5. `pengyuyan29.github.io` 仓库的 `CNAME` 文件仍为 `88.202991001.xyz`；博客发布工作流部署 GitHub Pages，未指定独立自定义域。
6. 此次访问博客 `vault.json` 得到 `{"error":"not found"}`，而非博客源码规定的 `version/kdf/salt/iv/data` 加密包。用户站点 CNAME 与当前导航 Worker 的路由重叠，是需要排查的部署原因；尚未做网络链路级证明，不能断言只改一个文件就必然恢复。

源码保留网络阅读，并支持原生导入已有博客构建 `dist/` 中的加密包。`tools/package-blog.ps1` 只打包 `vault.json` 与 `articles/`，不需要向 Android 工程提供博客密码，也不包含 Markdown 明文源码。

没有修改 DNS、GitHub Pages 设置、Cloudflare Worker、线上仓库或工作流。域名恢复属于发布环境操作，应在对应站点单独完成。

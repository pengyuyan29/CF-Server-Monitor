# 云控原生整合

2026-10-18：用户选择全部原生，不以 WebView 包装三个站点。

交付要求：源码 ZIP；不安装 Android SDK，不要求在本机生成 APK。

已从 `site-nav` / `nav-worker` 源码定位 `/api/sites`，读取用户提供的 https://88.202991001.xyz/api/sites，确认：

- 88 是站点导航，非博客。
- CF 探针：https://11.202991001.xyz/ 。
- 博客：https://pengyuyan29.github.io/blog/ 。
- sing-box 网页：https://pengyuyan29.github.io/singbox-config-builder/ 。
- 66 是另一套 VPS 健康检测，不是 CF-Server-Monitor，不能共用 CF 接口。

默认地址按以上可核验的导航条目设置，均允许用户修改。导航里的 online/offline 仅是该站扫描记录，不等于此次端到端验证结果。

## 来源

- CF：pengyuyan29/CF-Server-Monitor，main，API.md 与 Vue 前台逐项对照。
- 界面：pengyuyan29/kudo-android，main；玻璃材质来自 Kyant0/AndroidLiquidGlass（Apache-2.0）。
- 博客：pengyuyan29/blog，c9631f1d424442889267d4fe839708f0763c8209。
- 工具：pengyuyan29/singbox-config-builder，0b0b5efa83e546cd6658c1ad2c3bfb36da87e0ae。

## 验收范围

CF 保留终端卡片、等宽指标、用量色阶、价格/到期/标签、区域筛选、条形/环形/表格/地图、网络质量及详情图表；数据遵循服务端显示配置。管理功能包括登录、服务器维护、排序/隐藏、导入导出、采集与通知设置、用量、数据库维护。第三方 HTML/CSS/JavaScript 主题不可直接在原生 Compose 中执行，只能管理网页端主题配置；不能把网页主题管理等同于原生主题适配。

博客直接下载 vault.json 和按 slug 的 SHA-256 前 16 位命名的文章包；原生执行 PBKDF2-HMAC-SHA256（600000 次）、AES-256-GCM。密码、解密密钥、正文只保留内存；不将私有源码或明文文章打包进 APK。主页/搜索/分类/归档/关于、文章目录、代码复制、图片阅读均使用原生组件。

sing-box 对照真实工具注册表的六个工具：链接→配置、订阅转换、配置校验、规则生成器、二维码、节点提取。迁移纯逻辑到 Kotlin，保留协议支持和错误定位；转换不向第三方上传节点。订阅下载须由用户显式发起。配置结构随源仓库规则而定，结构检查不能冒充 sing-box 内核运行验证。

## 平台边界

Cloudflare Turnstile 是网页验证组件；数据、工具和阅读界面原生，验证保留服务商要求的网页验证流程，不伪造原生验证码或绕过验证。

## 本地基线缺口

- 详情月流量误用 MB 单位，实际 API 为字节。
- 流量额度未区分 total/dl/ul/max。
- WebSocket 可先于 REST 返回订阅空列表；增量未更新最后上报时间或详情。
- 缺少服务器显示开关、三网历史、详情的磁盘/负载/GPU/连接/丢包等图表。
- 设置页仅对私有站点显示管理员登录；公开站点也需要管理入口。
- 导航高度未固定，首次未配置面板时无法使用其他模块。

本文件记录目标范围；完成状态以实际代码、测试及后续验收记录为准。

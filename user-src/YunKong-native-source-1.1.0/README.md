# 云控 · 原生工具箱 1.1.0

Android 原生 Kotlin / Jetpack Compose 工程。四个入口：仪表盘、手记、工具箱、我的。数据、阅读和工具界面均为原生实现；只有 Cloudflare Turnstile 使用服务商要求的网页验证组件。

## 内容

- **CF 探针**：保持原项目的终端配色、等宽指标、用量色阶和卡片结构；条形/环形/表格/地图，搜索与筛选，WebSocket 与轮询，实时指标与历史图表，管理员维护入口。
- **手记**：原生兼容现有博客的 PBKDF2-SHA256 / AES-GCM 加密包，搜索、分类、归档、目录、代码复制和图片缩放。支持 HTTPS Pages 读取，也可导入已构建的加密 ZIP 离线阅读。文章明文和密码不随源码包分发。
- **sing-box**：六协议解析、订阅转换、配置结构检查、规则生成、二维码生成/图片识别、节点提取。节点库使用 Android Keystore 加密保存，提供 TUN / Mixed、自动测速和 GUI.for.SingBox 混入导出。
- **玻璃材质**：使用 kudo 同款 Backdrop 库；Android 13+ 折射，Android 12 模糊，旧系统兼容材质，支持手动切换。

## 默认站点

CF：`https://11.202991001.xyz`；博客：`https://pengyuyan29.github.io/blog/`。地址从用户站点导航的 `/api/sites` 核实，均可在 App 内修改。`88` 是导航站，`66` 是另一套健康检测服务。

博客当前发布地址未返回预期的加密包；仓库用户站点 CNAME 与导航 Worker 的域名存在重叠。详情见 [部署核查](docs/DEPLOYMENT_FINDINGS.md)。App 的离线导入入口可读取博客已生成的加密 `dist`，不会修复 DNS 或发布环境。

## 构建

使用 Android Studio 打开本目录，JDK 17 或 21，Gradle 8.13，Android SDK 36。首次同步需下载 Maven 依赖。源码交付过程中按要求没有安装 SDK、构建 APK 或运行 Android 真机。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Release 签名使用自己的密钥，通过 Gradle 属性 `releaseKeystore`、`releaseKeyAlias` 与环境变量 `YUNKONG_STORE_PASSWORD`、`YUNKONG_KEY_PASSWORD` 设置。源码 ZIP 不含本机已有 keystore、签名密码或私有文章。

## 独立核心验证（不需要 Android SDK）

需要 Node.js、JDK，以及包含 Kotlin 编译器和 kotlinx.serialization 的 Gradle 运行库目录：

```powershell
.\tools\test-core.ps1 -GradleLib '你的Gradle安装目录\lib'
```

已在交付环境通过 28 项核心检查。验证包括六种分享协议、Base64/JSON 订阅、错误行号、引用与重复标签、版本预设、规则生成、流量计费方向，以及 Node 加密→JVM 解密、中文密码、错误密码和篡改密文。

## 本地加密博客

在已有博客仓库运行其原有构建流程，得到 `dist/` 后：

```powershell
.\tools\package-blog.ps1 -BlogDist '你的blog\dist' -Output '.\encrypted-blog.zip'
```

把这个 ZIP 交给 App 的「手记 → 导入加密博客 ZIP」，使用博客原密码解锁。不要选择博客明文源码目录。

## 交付与验收边界

功能对照与检查记录见 [交付检查](docs/DELIVERY_CHECKLIST.md)。Kotlin 语法和纯 JVM 核心已检查；Android 依赖链接、Compose 布局、材质表现、Turnstile、管理员写入和完整现场阅读仍需在构建后验收。没有进行线上管理写入。

网页第三方主题的 HTML/CSS/JS 不能在原生 Compose 中运行；App 可管理网站主题配置，原生仪表盘保留 CF 默认风格。配置校验只检查结构与引用，仍需用目标版本的 `sing-box check` 验证实际内核兼容性。与原仓库相同，Clash YAML、SIP003 插件及 Hy2 端口跳跃未实现自动转换，会给出错误或提示。

来源及许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

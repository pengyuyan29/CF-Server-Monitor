# 来源与依赖

- CF-Server-Monitor：<https://github.com/pengyuyan29/CF-Server-Monitor>，上游 <https://github.com/huilang-me/CF-Server-Monitor>，MIT。原生仪表盘的数据协议、终端布局与地图资源依据该项目。
- Kudo：<https://github.com/woliubang256/kudo-android>，Apache-2.0。液态玻璃与悬浮导航的界面参考。
- AndroidLiquidGlass / Backdrop：<https://github.com/Kyant0/AndroidLiquidGlass>，Apache-2.0，通过 Maven 引用 `io.github.kyant0:backdrop:1.0.6`。
- 博客加密协议依据用户的 `pengyuyan29/blog` 私有仓库；源码包不包含私有文章、密码或已解密内容。
- sing-box 工具依据用户的 `pengyuyan29/singbox-config-builder` 纯逻辑，迁移为 Kotlin；不捆绑 sing-box 内核。
- AndroidX / Jetpack Compose：Apache-2.0。
- Kotlin / kotlinx.serialization：Apache-2.0。
- OkHttp：Apache-2.0。
- jsoup：MIT。
- ZXing：Apache-2.0。

第三方依赖由构建系统下载。相应许可证以各依赖发布包为准。

# sing-box 配置版本

原工具 README 写作 1.12+，但其最新源码已经包含 1.14 引入的 HTTP client 字段。原生版显式提供两套预设，避免把新字段交给旧版内核。

- 1.14+：显式 http_clients、route.default_http_client、rule_set.http_client，缓存 store_dns。
- 1.12/1.13：移除上述新字段，规则集使用 download_detour: direct。
- 节点类型使用 shadowsocks，修正原转换器的 ss 类型值。
- 拦截规则使用 action: reject，避免废弃的 block 出站。
- 自定义规则集导出包含相应的顶层 HTTP client 配置。

官方字段依据：

- [Route / default_http_client](https://sing-box.sagernet.org/configuration/route/#default_http_client)
- [Rule-set / http_client 与 download_detour](https://sing-box.sagernet.org/configuration/rule-set/)

结构检查不等于内核运行验证。不同客户端集成的内核版本可能不同，最终以实际目标内核的 sing-box check 为准。

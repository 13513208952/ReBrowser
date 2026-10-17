# 接入与动态生态 · R18–R20 · 设计草案

```mermaid
flowchart LR
  USB[ADB有线] --> ADAPTER[通道适配：不自行授予管理权]
  WIFI[ADB无线] --> ADAPTER
  LOCAL[宿主／子应用内部接口] --> ADAPTER
  IPC[跨应用 Binder／受控IPC] --> ADAPTER
  DYN[运行时插件／辅助包] --> ADAPTER
  HIDDEN[隐藏入口] --> ADAPTER
  ADAPTER --> CALLER[绑定调用者身份／签名／设备／会话]
  CALLER --> POLICY[同一权限计算与审计]
  POLICY --> REGISTRY[全能力注册表]
  REGISTRY --> DOMAIN[领域服务]
  NET[额外LAN／公网直连：未自动批准 Q07] -. 若获准再接入 .-> ADAPTER
```

```mermaid
sequenceDiagram
  participant P as 插件或辅助包
  participant A as 接入认证层
  participant G as 管理授权层
  participant D as 领域服务
  participant O as 机主
  P->>A: 身份证明、设备/应用绑定、请求目标、nonce
  A->>G: 已认证调用上下文，不相信自报等级
  G->>G: 计算权限/作用域/通道/介入风险
  alt 最高危且不在该身份可用名单
    G->>O: 申请范围和期限明确的授权
    O-->>G: 授权或拒绝
  end
  G->>D: 有效等级充分的类型化动作
  D-->>G: 真实结果或进行中/部分失败
  G-->>P: 结果与关联ID；不盲重放副作用
```

| 入口 | 不等于 | 需纳入策略的事实 |
| --- | --- | --- |
| USB／无线ADB | 不自动等于根管理员 | 管理签名、设备绑定、无线配对/链路、风险与新鲜度 |
| 应用内接口 | 不因“内部”而自动免认证 | UI机主动作身份与管理员身份分开；目标范围明确 |
| 跨应用IPC | 不信任自报包名／UID参数 | 系统提供caller UID；包签名／委托／安装密钥绑定 |
| 动态插件 | 不可自行声明高等级 | 委托签发者、上限、范围、期限、撤销、代码来源 |
| 隐藏入口 | 隐藏不构成安全边界 | 必须进入同一个授权策略 |
| APK内嵌私钥 | 不保证秘密或不可复制 | 可提取；需Q03决定可复制凭据模式与签名/设备绑定 |

## 辅助包委托的两个候选

```mermaid
flowchart TD
  KEY[辅助包中级权限需求] --> A[推荐：安装内持钥＋受限签名委托]
  KEY --> B[备选：明确可复制的APK内凭据]
  A --> LIMIT[绑定辅助包签名／设备／作用域／期限／撤销]
  B --> LIMIT
  B --> RISK[不能据此证明秘密管理员身份；有效等级必须反映风险]
  LIMIT --> SAME[等级充分后同能力同执行路径]
```

**Q03未决：** 不是直接否定插件生态，而是不能以“混淆或加密后私钥不会泄漏”作为信任假设。根私钥绝不应作为插件发行材料。具体等级数字尚未批准。

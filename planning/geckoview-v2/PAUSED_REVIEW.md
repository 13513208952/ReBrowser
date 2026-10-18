# 暂停审阅快照 · R9 WIP

机主要求先暂停、推送远端后自行分析。**当前停止推进，整体规划仍未完成；R9为在途设计，不是已实现或已全面审查版本。**

## 从哪里看

| 内容 | 文件 | 状态 |
| --- | --- | --- |
| 最近完整整理的基线 | [R8索引](INDEX.md)／[R8自检](reviews/R8-structural-review.json) | 历史阶段结果；不包含本次全部在途修改 |
| 当前等级/高级边界 | [authority-scale](records/authority-scale.json) | 助手在O18工程委托下选用0–10、H5、最高9、Root10；不是机主新确认数字 |
| 逐动作权限 | [action-registry](records/action-registry.json)／[feature-action-map](records/feature-action-map.json) | 178项动作设计；参数规范与所有派生视图尚未全面同步 |
| 密钥/登记/恢复 | [key-and-enrollment-design](records/key-and-enrollment-design.json) | 方案；没有生成正式密钥、登记设备或核实旧私钥备份 |
| 邮件SQL约束 | [mail-v1.sql](schemas/mail-v1.sql)／[主机检查](evidence/mail-schema-host-review.json) | 主机内存合成库38项检查；不是Android数据库实现 |
| 邮件对象格式 | [mail-object-format](records/mail-object-format.json)／[固定向量结果](evidence/mail-object-format-host-vectors.json) | 主机公开假密钥/标准向量64项检查；不是正式密钥或Android Keystore测试 |
| 权限设计演算 | [review_authority_design.py](review_authority_design.py) | 独立模型已执行；尚未合并进主自检器 |

## 审阅时注意

- `matrices/capabilities.csv`现在只是功能分组覆盖，旧等级列已标记历史示例；R9逐动作数字在`action-registry.json`。
- 旧权限图、部分“Q待确认”文字、完整性账本与总自检尚未全面同步R9，不能把不同修订混成同一结论。
- 新增数值/密钥/恢复选择是工程设计，允许审查修改；≥、来源中立、高级预编译、Root不可禁、名单隔离和四步来源保护仍是机主已确认规则。
- 最新工作仍限于`planning/geckoview-v2/`。没有产品/Gradle/README改动，没有新APK、设备操作、登录播放或真实数据清理。
- 暂停期间不继续推进设计或实施，等待机主分析后的指示。

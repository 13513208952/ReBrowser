# 离线仓库维护工具

在仓库根目录运行，使用 Python 标准库与 Git，不联网、不调用 ADB、不读账号、密钥或仓库外证据。完整边界见[维护指南](../../docs/MAINTENANCE.md)。

## 一致性检查（只读）

```bash
python3 -B tools/maintenance/check_repository.py
```

检查 Git 跟踪和未忽略新文件中的本地 Markdown 内联链接/标题锚点、两份不可变音乐参考摘要、固定源集位置、稳定版构建号对应的发布索引链接/发布记录（不自动修改机主README），以及误入源清单的构建/私有文件。忽略 fenced/inline code 示例和外部链接；不联网检查网址，不是完整 Markdown 解析器、秘密扫描器、APK 裁剪验证或安全审计。文档功能正确性仍需人工对照源码和证据。

## 清理生成输出（默认只预览）

```bash
python3 -B tools/maintenance/clean_generated.py
# Only after reviewing output, archiving required reports and finishing IDE/Gradle jobs:
python3 -B tools/maintenance/clean_generated.py --apply
# Optional: also project-local .gradle, never ~/.gradle or Android SDK caches:
python3 -B tools/maintenance/clean_generated.py --include-local-gradle-cache --apply
```

只允许 `app/build`、根 `build`，以及显式选择的项目 `.gradle`。核对工具所在目录为Git工作树根；拒绝软链接/越界、含跟踪源码的目标或同名普通文件；应用前保守检查 Linux `/proc` 中的 Gradle 进程，不结束任何进程。它不提供任意路径/通配符删除，也不自动备份、验证证据可丢弃或彻底解决与 IDE 启动任务的竞态。**操作者必须确认没有构建在运行，并先保存必要 R8/测试/发布报告。** 不在不支持 `/proc` 的平台强行跳过保护。

不会删除 `tools/build`、wrapper JAR、源码/资源、Git 历史、发布资产、仓库外私有根、Firefox Profile、用户数据或系统缓存。

## 工具自身回归

```bash
export TMPDIR=/home/zhao/temp/re_outlook/maintenance-tests
mkdir -p "$TMPDIR"
python3 -B tools/tests/test_repository_maintenance.py
```

20 项临时夹具检查链接/锚点/代码示例、引用摘要、版本入口、源集否定、清理白名单/跟踪源码/软链接/文件拒绝、默认不删除和忙碌拒绝；apply 仅对可丢弃临时夹具验证，不清真实仓库，不依赖设备或私有资料。`TemporaryDirectory` 遵循上述 TMPDIR。

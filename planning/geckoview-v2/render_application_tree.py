#!/usr/bin/env python3
"""Render/check ONLY application runtime code leaves; auxiliary files are references."""
import argparse
import json
from pathlib import Path, PurePosixPath

P = Path(__file__).resolve().parent
OUTPUT = P / 'APPLICATION_CODE_TREE.md'


def render():
    manifest = json.loads((P / 'records/application-code-files.json').read_text())
    files = manifest['files']
    groups = {g['id']: g for g in manifest['groups']}
    assert len(groups) == len(manifest['groups'])
    assert len({f['path'] for f in files}) == len(files)
    nodes = {}
    for f in files:
        path = PurePosixPath(f['path'])
        assert not path.is_absolute() and '..' not in path.parts
        assert '*' not in str(path) and '...' not in str(path) and '…' not in str(path)
        assert str(path).startswith('app/src/main/')
        assert not any(part in ['test', 'androidTest', 'experiments', 'tools'] for part in path.parts)
        assert path.suffix in ['.java', '.kt', '.js', '.css', '.html', '.sql']
        assert f['group'] in groups and f['testReferenceGroup'] in groups
        assert f['responsibility'] and f['validationRefs'] and f['state'] == 'planned-not-implemented'
        current = nodes
        for part in path.parts[:-1]:
            current = current.setdefault(part, {})
        assert path.name not in current
        current[path.name] = f
    lines = [
        '# 应用本体目标代码总树 · R10首版', '',
        '> O27范围：只逐文件展开应用自身运行代码。测试/验证/构建/维护/文档不混入主树；在树后按关联概括并引用目标位置。',
        '> 这是目标设计，不是已创建的产品源码。已先完成固定Gecko二进制/API编译与隔离Release打包；Android运行/网站兼容尚未验证，`[?]`不得理解为通过。', '',
        f'当前目标：`io.github.rebrowser` · 本体代码 **{len(files)}文件** · {len(groups)}职责分组。源码增加/更名必须先改权威清单，再重新生成；不能用省略号隐藏源码。', '',
        '符号：`[J]`原生代码，`[S]`运行脚本/HTML，`[C]`运行样式，`[Q]`运行SQL，`[?]`需要Android/网站实证；`↪`为树外引用，不算本体代码叶节点。', '',
        '## 本体代码树', '', '```text']
    leaf_count = 0
    def walk(items, prefix=''):
        nonlocal leaf_count
        names = sorted(items)
        for i, name in enumerate(names):
            last = i == len(names) - 1
            branch = '└── ' if last else '├── '
            item = items[name]
            if 'responsibility' in item:
                symbol = {'native-code':'J', 'script-code':'S', 'style-code':'C', 'sql-code':'Q'}[item['kind']]
                lines.append(prefix + branch + name + ' [' + symbol + '][?] — ' + item['responsibility'])
                leaf_count += 1
            else:
                lines.append(prefix + branch + name + '/')
                walk(item, prefix + ('    ' if last else '│   '))
    walk(nodes)
    assert leaf_count == len(files)
    lines += ['```', '', '## ↪ 测试需求与目标位置（辅助代码独立存放，不展开为主树）', '',
              '| 职责 | 测试要求摘要 | 目标文件引用，均为规划路径 |', '| --- | --- | --- |']
    for group in manifest['groups']:
        refs = '<br>'.join('`'+t['path']+'`' for t in group['testReferences'])
        purposes = '；'.join(dict.fromkeys(t['purpose'] for t in group['testReferences']))
        lines.append('| '+group['title']+' | '+purposes+' | '+refs+' |')
    lines += ['', '## ↪ 其他部分的概括引用（不作为源码分支展开）', '',
              '| 关联职责 | 部分 | 位置/说明 |', '| --- | --- | --- |']
    for ref in manifest['references']:
        lines.append('| '+groups[ref['group']]['title']+' | '+ref['kind']+' | '+ref['summary']+'<br>'+'<br>'.join('`'+x+'`' for x in ref['paths'])+' |')
    lines += ['', '### 当前边界', '',
              '- 子应用占用查看/缓存与数据清理按O26搁置，不添加当前运行依赖。专项来源异常旧私有数据保护、邮箱内部引用完整性不是该延期功能。',
              '- 测试/构建/辅助脚本/公开维护资料由独立引用与清单管理；Gecko等第三方内部源码不冒充本项目应用源文件。',
              '- 资源/组件声明/编译政策也是软件必要内容，但按O27以引用表达；本体JS/CSS/HTML/SQL实际参与运行，不能误当“辅助代码”漏掉。',
              '- 权威清单：[application-code-files.json](records/application-code-files.json)。技术证据：[technology-validation.json](records/technology-validation.json)。',
              '- `python3 -B planning/geckoview-v2/render_application_tree.py --check`逐叶核对总树，拒绝混入辅助代码、重复路径、通配省略或失去关联。', '']
    return '\n'.join(lines), {'applicationRuntimeCodeLeaves': leaf_count, 'applicationResponsibilityGroups':len(groups),
                               'auxiliaryTestReferences':sum(len(g['testReferences']) for g in groups.values()),
                               'runtimeFilesImplementedByThisTree':False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    text, result = render()
    if args.check:
        assert OUTPUT.read_text() == text, 'tree not synchronized with complete application source manifest'
    else:
        OUTPUT.write_text(text)
    print(json.dumps(result, ensure_ascii=False, indent=2))

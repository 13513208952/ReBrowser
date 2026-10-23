#!/usr/bin/env python3
"""Render/check ONLY application runtime code leaves; auxiliary files are references."""
import argparse
import json
from pathlib import Path, PurePosixPath

P = Path(__file__).resolve().parent
OUTPUT = P / 'APPLICATION_CODE_TREE.md'


def render(manifest=None):
    if manifest is None:
        manifest = json.loads((P / 'records/application-code-files.json').read_text())
    files = manifest['files']
    groups = {g['id']: g for g in manifest['groups']}
    assert len(groups) == len(manifest['groups'])
    assert len({f['path'] for f in files}) == len(files)
    nodes = {}
    implemented = []
    for f in files:
        path = PurePosixPath(f['path'])
        assert not path.is_absolute() and '..' not in path.parts
        assert str(path) == f['path'] and '\\' not in f['path']
        assert '*' not in str(path) and '...' not in str(path) and '…' not in str(path)
        assert str(path).startswith('app/src/main/')
        assert not any(part in ['test', 'androidTest', 'experiments', 'tools'] for part in path.parts)
        assert path.suffix in ['.java', '.kt', '.js', '.css', '.html', '.sql']
        assert f['group'] in groups and f['testReferenceGroup'] in groups
        assert f['responsibility'] and f['validationRefs']
        assert f['state'] in ['planned-not-implemented', 'implemented-host-tested-not-integrated', 'source-in-progress-api-compiled', 'source-in-progress-static-tested']
        if f['state'] != 'planned-not-implemented':
            assert (P.parents[1] / f['path']).is_file()
            assert f.get('implementationReadinessRef') == 'records/implementation-readiness.json'
            implemented.append(f['path'])
        assert f['kind'] in ['native-code', 'script-code', 'style-code', 'sql-code']
        delegation = f.get('delegation')
        if delegation is not None:
            assert delegation['difficulty'] in ['low', 'moderate']
            assert delegation['taskId'].startswith('L') and len(delegation['taskId']) == 3 and delegation['taskId'][1:].isdigit()
            assert delegation['assignee'] == 'owner-selected-low-cost-agent'
            assert delegation['status'] in ['reserved-not-started', 'in-progress', 'awaiting-main-review', 'delivered-awaiting-main-review', 'reviewed-scoped-host-api', 'reviewed-static-source-inactive', 'review-held-layout-unapproved']
            assert delegation['scope'] in ['whole', 'slice']
            assert delegation['contractRef'] == 'records/low-cost-agent-assignments.json'
            assert (P / delegation['contractRef']).is_file()
        active=f.get('activeDelegation')
        if active:
            assert active['round']=='D03' and active['scope']=='slice' and active['difficulty']=='bounded-code-extension'
            assert active['status'] in ['reserved-not-started','in-progress','delivered-awaiting-main-review','reviewed-scoped-host-api']
            assert active['doesNotReplaceHistoricalAdmission'] and active['assignmentRef']=='records/low-cost-agent-assignments.json'
        if f['state'] == 'planned-not-implemented':
            risk = f.get('riskAssessment')
            assert risk and risk['fullFileRisk'] in ['low', 'medium', 'high']
            assert risk['contractRef'] == 'records/remaining-file-risk-assessment.json'
        current = nodes
        for part in path.parts[:-1]:
            current = current.setdefault(part, {})
        assert path.name not in current
        current[path.name] = f
    lines = [
        '# 应用本体目标代码总树 · R10首版', '',
        '> **当前代码纠偏见[产品体验工作状态](records/product-experience-correction-status.json)。** 下列准入标签/数量仍是截至R52的历史逐文件准入快照，不代表当前物理源码数量，也不沿用旧源码哈希证明R53–R57新代码。总树保留并修正职责，正式准入待按实际源码/消费链重新核对；不能以历史PLAN标签推断文件尚不存在，或以文件已创建推断产品已完成。', '',
        '> O27范围：只逐文件展开应用自身运行代码。测试/验证/构建/维护/文档不混入主树；在树后按关联概括并引用目标位置。',
        '> 总树同时区分规划节点和已准入源码。`[HOST]`仅指该文件已编写并通过所列主机测试，不是Android集成/产品完成；`[?]`仍保留未决运行证据。`[API/WIP]`为Android绑定源码实现中，可能已有合成HOST子集，不等于仅有编译或完整文件验证；逐项看准入记录。`[ASSET/WIP]`仅静态/合成DOM验证的未激活运行资产，不等于APK资源已接线或真实渲染。固定Gecko隔离样机的局部Android结果不自动覆盖产品文件。', '',
        f'R52历史准入快照：**{len(implemented)}文件**；其中HOST测试切片 **{sum(f["state"] == "implemented-host-tested-not-integrated" for f in files)}文件**，API编译/实现中 **{sum(f["state"] == "source-in-progress-api-compiled" for f in files)}文件**，静态资产WIP **{sum(f["state"] == "source-in-progress-static-tested" for f in files)}文件**；其余 **{len(files)-len(implemented)}文件**仍为规划。依据：[逐文件实现记录](records/implementation-readiness.json)。', '',
        f'当前目标：`io.github.rebrowser` · 本体代码 **{len(files)}文件** · {len(groups)}职责分组。源码增加/更名必须先改权威清单，再重新生成；不能用省略号隐藏源码。', '',
        '符号：`[J]`原生代码，`[S]`运行脚本/HTML，`[C]`运行样式，`[Q]`运行SQL，`[?]`需要Android/网站实证；`↪`为树外引用，不算本体代码叶节点。', '',
        '> D02分配时已评估全部126个剩余PLAN节点，本轮42个新目标（36Java+6运行资产）经O80主审后，R48并入36Java+4未激活资产，另2份窗口/桌面CSS无批准参考继续HOLD。旧6交付及主链依赖的有限准入仍保留，当前PLAN数以本页统计为准。`[LOW:Lxx]`为整项低风险；`[LOW-EFFECT:Lxx]`为完整低副作用任务但集成/实现风险仍中等；`[LOW-SLICE:Lxx]`仅允许该文件限定低风险切片。`[RISK:*]`是原完整责任风险，`[REVIEW-PENDING]`表示已有未准入交付。**任务数、解析/展示和交付都不是产品完成或业务授权。**只看[权威任务范围](records/low-cost-agent-assignments.json)／[全部剩余文件风险](records/remaining-file-risk-assessment.json)，共享上下文不另写交接文档。', '',
        '> D03依O83/O85/O86分配25个**既有Java文件的实质扩展**（L49–L73），经O88完成通知后R50主审修正并有限准入，不是25个新增文件/PLAN完成。`[D03:Lxx]`是分工/审查范围，不覆盖历史证据或证明全文件运行。任务开始前上下文全同步，无额外交接/依赖源码副本；原交付保留，主agent独立验证后整合。', '',
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
                status = {'planned-not-implemented':'[PLAN]', 'implemented-host-tested-not-integrated':'[HOST]',
                          'source-in-progress-api-compiled':'[API/WIP]', 'source-in-progress-static-tested':'[ASSET/WIP]'}[item['state']]
                assignment = item.get('delegation')
                risk = item.get('riskAssessment')
                low = ''
                if assignment:
                    tag = 'LOW-SLICE' if assignment['scope']=='slice' else 'LOW' if risk['fullFileRisk']=='low' else 'LOW-EFFECT'
                    low = '['+tag+':' + assignment['taskId'] + ']'
                risk_tag = '[RISK:'+risk['fullFileRisk'].upper()+']' if risk else ''
                review_tag = '[REVIEW-PENDING]' if assignment and assignment['status']=='delivered-awaiting-main-review' else '[REVIEW-HOLD]' if assignment and assignment['status']=='review-held-layout-unapproved' else ''
                lines.append(prefix + branch + name + ' [' + symbol + ']' + status + '[?]' + low + risk_tag + review_tag + ('[D03:'+item['activeDelegation']['taskId']+']' if item.get('activeDelegation') else '') + ' — ' + item['responsibility'])
                leaf_count += 1
            else:
                lines.append(prefix + branch + name + '/')
                walk(item, prefix + ('    ' if last else '│   '))
    walk(nodes)
    assert leaf_count == len(files)
    lines += ['```', '', '## ↪ D02单轮42文件审查（40有限准入，2样式HOLD）', '',
              '| 任务 | 目标文件 | 完整责任风险 / 分配范围 / 状态 |', '| --- | --- | --- |']
    for item in files:
        if item.get('delegation'):
            d = item['delegation']
            lines.append('| ' + d['taskId'] + ' | `' + item['path'] + '` | '+item['riskAssessment']['fullFileRisk']+' / '+d['scope']+' / ' + d['status'] + ' |')
    lines += ['', '## ↪ D03主审：25既有代码文件扩展（不算新增文件，不是全后端/Android资格）', '', '| 任务 | 保留文件 | 范围 / 状态 |', '| --- | --- | --- |']
    for item in files:
        if item.get('activeDelegation'):
            d=item['activeDelegation'];lines.append('| '+d['taskId']+' | `'+item['path']+'` | '+d['scope']+' / '+d['status']+' |')
    lines += ['', '范围/接口/验收以[任务记录](records/low-cost-agent-assignments.json)和[全量风险](records/remaining-file-risk-assessment.json)为准；42新目标不包含原6交付。原其余78目标继续由主agent负责（其中4已有限准入、74仍PLAN），slice之外的高风险责任也保留主agent。不增空实现、不把分工当产品进度。原6交付只按实际模型HOST/API范围准入，不是Android View/backend完成；R48主审证据独立绑定当前源码，R47等历史记录原样保留；不能沿用旧测试声称新代码全部通过。', '', '## ↪ 测试需求与目标位置（辅助代码独立存放，不展开为主树）', '',
              '| 职责 | 测试要求摘要 | 目标文件引用，实施/证据另看准入记录 |', '| --- | --- | --- |']
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
                               'runtimeFilesImplementedByThisTree':bool(implemented),
                               'runtimeSourceFilesCreated':len(implemented),
                               'implementedRuntimeCodeFiles':sum(f['state'] == 'implemented-host-tested-not-integrated' for f in files),
                               'apiCompiledRuntimeSourcesInProgress':sum(f['state'] == 'source-in-progress-api-compiled' for f in files),
                               'staticRuntimeAssetsInProgress':sum(f['state'] == 'source-in-progress-static-tested' for f in files),
                               'productRuntimeIntegrated':False,
                               'delegatedRuntimeTargetsIncludingPriorDelivery':sum(bool(f.get('delegation')) for f in files),
                               'newD02DelegatedRuntimeFiles':sum(f.get('delegation',{}).get('round')=='D02' for f in files),
                               'activeD03ExistingCodeExtensions':sum(f.get('activeDelegation',{}).get('round')=='D03' and f['activeDelegation']['status']!='reviewed-scoped-host-api' for f in files),
                               'reviewedD03ExistingCodeExtensions':sum(f.get('activeDelegation',{}).get('round')=='D03' and f['activeDelegation']['status']=='reviewed-scoped-host-api' for f in files),
                               'remainingFilesRiskAssessed':sum(bool(f.get('riskAssessment')) for f in files)}


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

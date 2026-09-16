# CORE/01 — 需求规则（含需求工程化网关 REG 与价值判断）

需求处理的严格程度按 Scale 缩放——S1 只需要"保留意图 + 写清验收标准"，
S3+ 才需要完整 Engineering Baseline + Assumption Register；具体缩放规则见
对应 `SCALE/S<n>_*.md`。以下是所有规模都适用的基础机制。

## 需求分解

每条需求至少拆解为：Requirement ID · Original Statement · Intent ·
Functional / Non-functional Requirement · Constraint · Assumption ·
Dependency · Risk · Acceptance Criteria · Out of Scope · AI Recommendation，
并标注来源层级，三档不得混用、不得把低层级偷偷升级成高层级：

- **USER_EXPLICIT** — 用户明确说的。
- **USER_INFERRED** — 从上下文合理推断的。
- **AI_RECOMMENDED** — AI 自己的建议，不是用户要求的。

## 模糊需求转换

"更高级""更流畅""更稳定""更智能""像 X 一样""优化一下"这类描述，必须转换成
具体可检验的标准才能开始动工。转不出绝对指标时用相对（改动前/改动后对比）
验收标准，而不是就这么模糊地开始写代码。

## Requirements Engineering Gateway（REG）

新项目需求、重大功能需求、架构级变更，进入 Architecture/Planning/Coding
之前必须先过 REG——REG 不是替产品做主，而是把用户说的话变成工程能落地执行
的东西。REG **完成**需求，不**扩张**需求。

**三种输入成熟度**：IDEA_LEVEL（一句话想法，REG 要补全的最多）、
PRODUCT_LEVEL（有功能和大致流程）、ENGINEERING_LEVEL（已带技术栈和约束，
REG 主要做校验补全）。

**REG 的职责**：保留核心意图、补全缺失需求、推导工程必要条件、分析边界和
异常、补齐非功能需求、建立验收标准、产出可执行的 Engineering Baseline
（模板见 `TEMPLATES/ENGINEERING_BASELINE_TEMPLATE.md`）。所有由 REG 补全
（而非用户原话）的条目标注 `[AI_RECOMMENDED-COMPLETION]`。

**Definition of Ready（DoR）**——需求离开 REG、进入架构/编码前必须满足：
核心意图已保留、明显缺口已补全、工程前提已推导、边界/异常已分析、
非功能需求已定义或明确标注 N/A、验收标准已建立、每条假设已登记到
`TEMPLATES/ASSUMPTION_REGISTER_TEMPLATE.md`、已分配 Traceability ID、
不存在无法判断的产品方向级根本分歧（唯一一种 REG 自己会触发暂停而不是
直接通过 DoR 的情况，见 `CORE/08_AI_EXECUTION_RULES.md` STOP-05）。

REG 是 `CORE/08` 定义的持续自主执行循环里的一个 **AI 内部门禁**，不会把
流程变回"分析→等确认→设计→等确认"。

## Value Case（为什么值得做）

新项目/新大功能，第一个问题不是"用什么技术栈"，而是"这值得做吗"：
Problem（问题是什么）· Who（谁在承受）· Current Cost（现在的代价）·
Expected Value（做了之后具体好在哪）· Frequency（多常用）·
Reusable Asset Potential（能不能沉淀成可复用资产）·
Automation Potential（能不能省掉重复劳动）·
Existing Tool Check（现成工具够不够）· Smallest Valuable Version ·
Success Metric · Stop Condition。`INIT_PROJECT` 工作流会先补这个再决定
技术栈和架构。

## 需求漂移与变更影响分析

已 BASELINED 的需求如果发现实现悄悄跑偏（多做了没约定的东西，或漏掉了
约定的东西），记入需求漂移日志并核实：是合理的新发现（走 Change Impact
Analysis 走完流程后更新 Baseline），还是单纯跑偏（拉回 Baseline 约定的
范围）。修改已 BASELINED 需求前必须先做 Change Impact Analysis：影响哪些
文件/模块/API/测试、可能的回归范围、风险等级、回滚方案——分析完才动手，
版本化更新 Baseline（旧版本标 SUPERSEDED），不得原地改写。

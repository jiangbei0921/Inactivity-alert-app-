# 站一站 · 修改前后区别文档（原始版本 → 当前版本）

> 本文档对「站一站 / Inactivity Alert」项目截至 2026-08-08 的全部要求完成度做最终核对，并记录**最原始版本（首次提交 `8318489`）**与**当前修改后版本**之间的差异。
> 数据来源：`ArchitectureReview.md`（架构审计）、`RefactorReport.md`（合并重构）、`MODIFICATION_REPORT.md`（PM 修改规划）、以及代码实际核验（`fix/remaining-c7-c17` 分支 + `feat/landing-page` 分支）。

---

## 1. ❌ 当前【仍未完成】的要求（重点）

### 1.1 产品增长路线图（PM 诊断 M 项，多数未落地）

| 编号 | 要求 | 优先级 | 状态 | 说明 |
|------|------|--------|------|------|
| M2 | 最小埋点体系（激活/响应/留存） | P0 | ❌ 未做 | 无任何 analytics 模块，对产品健康度仍"失明" |
| M4 | 首启 Onboarding 引导 | P0 | ❌ 未做 | 新装无引导，依赖用户自行配置 |
| M5 | Release 签名 + R8 混淆 + 商店上架 | P1 | ❌ 未做 | `isMinifyEnabled = false`，仅 Debug 包，无法上架 |
| M6 | 习惯机制（目标/徽章/奖励） | P1 | ❌ 未做 | 仅有连续达标天数，无奖励/承诺 |
| M7 | 处方性建议（基于个人数据） | P1 | ❌ 未做 | 统计仅展示历史 |
| M8 | B 端企业健康方案 | P1 | ❌ 未做（远期） | 纯 C 端，无账号无后台 |
| M9 | 接入 Health Connect / 厂商健康 | P1 | ❌ 未做 | 与健康平台零集成 |
| M10 | 智能免打扰自动分类 | P2 | ❌ 未做 | 仍依赖手动黑名单 |
| M11 | 可选端到端加密云同步 | P2 | ❌ 未做 | 换机数据清零 |
| M12 | 品牌与分发运营（ASO/种草） | P2 | ❌ 未做 | 无运营动作 |
| M14 | 无障碍 + i18n | P2 | ❌ 未做 | 无大字/TalkBack/色盲/多语言 |
| M15 | 可穿戴延伸（Wear OS） | P2 | ❌ 未做 | 仅手机端 |

> PM 路线图中仅 **M1（被动站立检测）已落地**，M3（叙事重写，借 README 重写完成）、M13（工程化，测试门禁部分完成）为**部分完成**，其余 12 项均未开始。

### 1.2 架构 / 工程质量残留项

| 编号 | 要求 | 状态 | 说明 |
|------|------|------|------|
| M9（架构） | `NotificationHelper` 仍 `object` 单例 | ❌ | 不可 mock、难测试（仅 `ReminderCopywriter` 可测） |
| M10（架构） | `SmartDetector` 仍 `object` 单例 | ❌ | 不可 mock（已移至 `detector/` 包，但仍是单例） |
| M13（架构） | Service → WorkManager | ❌ | 按设计保留前台 Service，未迁移（可接受但属已知偏离） |
| — | 无依赖注入框架（Hilt） | ❌ | 手动创建依赖，耦合仍在 |
| — | `LICENSE` 文件缺失 | ❌ | README 已标注"未声明许可证"，需补 `MIT` 等 |

### 1.3 低优先级（Low）残留

| 编号 | 要求 | 状态 |
|------|------|------|
| L1 | `SimpleDateFormat` 每次调用新建 | ❌ 未做 |
| L3 | 通知使用系统默认图标（缺应用图标资源） | ❌ 未做 |
| L4 | `ReminderActivity` 用 `Handler` 而非协程 | ❌ 未做 |

### 1.4 刻意保持不变（非缺陷）

- **完全离线 / 无网络权限 / 无第三方 SDK**：隐私是核心信任资产，所有新增能力一律本地优先。
- **不引入广告**。

---

## 2. 📋 需求完成度总表

### 2.1 架构审计（ArchitectureReview / RefactorReport）

| 级别 | 编号 | 要求 | 状态 | 落地依据 |
|------|------|------|------|----------|
| 🔴 Critical | C1 | `SmartDetector` 主线程 `runBlocking` | ✅ | RefactorReport #02 |
| 🔴 Critical | C2 | `NotificationHelper` `runBlocking` | ✅ | RefactorReport #03 |
| 🔴 Critical | C3 | 运行时状态误写 DataStore | ✅ | RefactorReport #01 |
| 🔴 Critical | C4 | `fallbackToDestructiveMigration` 数据丢失 | ✅ | RefactorReport #04（Migration 1→2） |
| 🟠 High | H1 | 缺 Repository 层 | ✅ | `CheckInRepository` 新建 |
| 🟠 High | H2 | `TimerService` God Class | ✅ | 抽取 `ForegroundNotifier` |
| 🟠 High | H3 | `SettingsDataStore` God Class | ✅ | 移除运行时状态属性 |
| 🟠 High | H4 | `ActivityDetailScreen` 协程泄漏 | ✅ | `rememberCoroutineScope` |
| 🟠 High | H5 | `ActivityDetailScreen` 无 ViewModel | ✅ | 新增 `ActivityDetailViewModel`（C7–C17） |
| 🟠 High | H6 | `setSound(null)` 渠道无声音不可恢复 | ✅ | C10 动态渠道重建覆盖 |
| 🟠 High | H7 | 无测试覆盖 | ✅ | 3 个测试类 |
| 🟡 Medium | M1 | `SettingsViewModel` 纯代理 | ✅ | 增加范围/合法性校验 |
| 🟡 Medium | M2 | HomeVM/Service 逻辑重复 | ❌ | 仍两处实现（standUp/snooze） |
| 🟡 Medium | M3 | Room 缺索引 | ✅ | `indices` + Migration |
| 🟡 Medium | M4 | N+1 查询 | ✅ | 批量 `getAllDayCountsByType` |
| 🟡 Medium | M5 | Canvas 重复 `Paint` | ✅ | `remember` 缓存 |
| 🟡 Medium | M6 | `SettingsScreen` 巨型 Composable | ✅ | 拆分 4 个组件 |
| 🟡 Medium | M7 | 无暗色主题 | ✅ | `DarkColorScheme` |
| 🟡 Medium | M8 | Widget 数据硬编码 | ✅ | 读真实数据 |
| 🟡 Medium | M9 | `NotificationHelper` object 单例 | ❌ | 见 1.2 |
| 🟡 Medium | M10 | `SmartDetector` object 单例 | ❌ | 见 1.2 |
| 🟡 Medium | M11 | `loadSettings()` 只读一次 | ✅ | `collect` 持续监听 |
| 🟡 Medium | M12 | `createChannels` 每次删重建 | ✅ | 移除删除逻辑 |
| 🟡 Medium | M13 | Service vs WorkManager | ❌ | 见 1.2 |
| 🟢 Low | L1 | `SimpleDateFormat` 每次新建 | ❌ | 见 1.3 |
| 🟢 Low | L2 | 缺通知分组 | ✅ | `setGroup` |
| 🟢 Low | L3 | 系统默认图标 | ❌ | 见 1.3 |
| 🟢 Low | L4 | `ReminderActivity` Handler | ❌ | 见 1.3 |
| 🟢 Low | L5 | `list.random()` 不可测 | ✅ | 注入 `Random` 参数 |
| 🟢 Low | L6 | `SmartDetector` 目录不当 | ✅ | 移至 `detector/` |
| 🟢 Low | L7 | 缺 `libs.versions.toml` | ✅ | 新建版本目录 |
| 🟢 Low | L8 | ProGuard 规则不全 | ✅ | 补全规则 |
| 🟢 Low | L9 | `StatCard` 全限定引用 | ✅ | 短引用 |

**架构审计合计**：Critical 4/4 ✅｜High 7/7 ✅｜Medium 9/13（4 项未做）｜Low 6/9（3 项未做）。

### 2.2 PM 修改规划（MODIFICATION_REPORT M1–M15）

| 编号 | 要求 | 状态 |
|------|------|------|
| M1 | 被动站立检测 | ✅ 已做（commit `377c073`） |
| M2 | 最小埋点 | ❌ |
| M3 | 定位叙事重写 | 🟡 部分（README 重写完成；`BRAND.md` 未建） |
| M4 | Onboarding | ❌ |
| M5 | Release/R8/上架 | ❌ |
| M6 | 习惯机制 | ❌ |
| M7 | 处方建议 | ❌ |
| M8 | B 端方案 | ❌ |
| M9 | Health Connect | ❌ |
| M10 | 免打扰自动分类 | ❌ |
| M11 | E2EE 云同步 | ❌ |
| M12 | 品牌分发 | ❌ |
| M13 | 工程化/测试门禁 | 🟡 部分（3 测试类；未做模块拆分/CI 门禁） |
| M14 | 无障碍/i18n | ❌ |
| M15 | 可穿戴 | ❌ |

### 2.3 用户显式任务（全部完成）

| 任务 | 状态 | 产出 |
|------|------|------|
| 按开源标准重写 README + 安全审查 | ✅ | `README.md`（300+ 行，含安全与隐私章节） |
| 资深 PM 诊断（R1–R15） | ✅ | 分析文档（仅评估，未改代码） |
| 据 PM 分析出修改报告 | ✅ | `MODIFICATION_REPORT.md` |
| 实现最重要部分（M1 被动站立） | ✅ | commit `377c073` |
| 简历两段 50 字文案 | ✅ | 已输出 |
| 全面代码排查（零 warning / 竞态） | ✅ | commit `51ff24d` |
| 完成 C7–C17 残留缺陷 | ✅ | PR #2（`fix/remaining-c7-c17`） |
| 创建产品官网（下载页 + 二维码） | ✅ | PR #3（`feat/landing-page`：`index.html` + `qrcode.png`） |

---

## 3. 🔁 原始版本 → 修改后版本 区别记录

### 3.1 README（文档）
- **原始**：约 8 行极简说明（仅一句产品描述 + "功能特性"标题）。
- **修改后**：300+ 行，按一流开源标准重塑——项目简介 / 功能特性 / 安装步骤 / 使用说明 / 技术栈 / 目录结构 / 工作原理 / 🔒 安全与隐私 / 贡献指南 / 许可证 / 联系反馈；新增「🌐 产品官网」章节与二维码。

### 3.2 架构与代码修复（Critical / High / 多数 Medium / 多数 Low）
- `runBlocking` **全项目清零**（仅剩 1 处注释提及），消除 ANR 风险。
- `DataStore` 不再承载运行时状态（每 15s 磁盘写入已移除）。
- Room 由 `fallbackToDestructiveMigration` 改为 **Migration(1→2)**，新增 `timestamp`/`type` 索引。
- 新增 **`CheckInRepository`** 仓储层，ViewModel/Service 不再直连 DAO。
- `TimerService` 抽取 `ForegroundNotifier`，`while(true)` 改为协程循环，`onStartCommand` 返回 `START_REDELIVER_INTENT`。
- `ActivityDetailScreen` 协程泄漏修复，并新增 `ActivityDetailViewModel` 接管 DB 写入。
- `SettingsScreen` 巨型 Composable 拆为小组件；新增**暗色主题**；Widget 改为读取真实数据。
- `NotificationHelper` 6 个函数转 `suspend`；通知加分组；`createChannels` 不再删重建；`ReminderCopywriter` 可注入 `Random`。
- 工程化：`libs.versions.toml` 版本目录、补全 `proguard-rules.pro`、`SmartDetector` 移入 `detector/` 包。

### 3.3 C7–C17 残留修复（PR #2）
- **动作总线**：通知按钮由 `BroadcastReceiver` 中转改为 `PendingIntent.getForegroundService()` 直启 `TimerService`（C7）。
- **暂停死锁 / 天文数字**：加载时 `TimeUtils.isToday()` 校验 `sittingStartTime`，非当天重置为 `now`（C8）。
- **周期提醒**：首次提醒后每 5 分钟重复（C9，`SITTING_REMINDER_REPEAT_MS`）。
- **渠道声音/震动**：渠道 ID 编码声音/震动/铃声索引（`sittingChannelId`/`microChannelId`），设置变更自动重建（C10）。
- **提醒通知不取消**：`handleSnooze`/`handlePause` 增加 `cancelReminderNotifications()`（C15）。
- **`BootReceiver`**：移除 `runBlocking`，改 `goAsync()` + 协程。
- **架构规范**：`ActivityDetailScreen` 移除直接 `AppDatabase` 访问；清理重复导入与未用字段。

### 3.4 M1 被动站立检测（commit `377c073`）
- 新增 `ACTIVITY_RECOGNITION` 权限与步数传感器验证「是否真站立」；未授权安全降级。

### 3.5 全面代码排查（commit `51ff24d`）
- `compileDebugKotlin` **零 warning / 零 error**；修复站立验证竞态。

### 3.6 测试
- 新增 3 个 JUnit + MockK 测试：`SettingsDataStoreTest`、`NotificationHelperTest`、`ReminderCopywriterTest`。

### 3.7 产品官网（PR #3，`feat/landing-page`）
- `index.html` 自包含静态官网（Hero/功能/隐私/下载）；`qrcode.png` 扫码直下 APK；README 联动。

### 3.8 验证结果
- `assembleDebug` 成功，APK ≈ 18.2 MB；`testDebugUnitTest` 全部通过。

---

## 4. ✅ 一句话结论

- **架构底线已夯实**：所有 Critical + High 审计项、绝大多数 Medium/Low 已修复；代码零 warning、有测试、可构建。
- **产品增长路线基本未启动**：PM 规划 15 项中仅 M1 落地，12 项（M2/M4–M12/M14/M15）完全未做，M3/M13 部分完成。
- **已知刻意偏离**：保留前台 Service（未迁 WorkManager）、保留 `object` 单例（未引入 DI）、仍发 Debug 包（未做 Release/R8/上架）、`LICENSE` 缺失。
- 这些"未完成项"均为**产品演进与工程增强**，不影响当前可用性与已交付的隐私/稳定性目标。

---

*文档生成时间：2026-08-08 ｜ 核对基线：首次提交 `8318489` → 当前 `fix/remaining-c7-c17`(`8dfbf06`) + `feat/landing-page`(`4705b12`)*

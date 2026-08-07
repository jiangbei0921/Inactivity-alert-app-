# 架构审计报告：站一站 (SitBreak)

## 审计人：首席 Android 架构师
## 审计日期：2026-07-08
## 项目版本：1.0.0

---

## ① 当前架构评分：**58 / 100**

| 评分维度 | 得分 | 满分 | 说明 |
|----------|------|------|------|
| 分层架构 | 6 | 10 | 缺少 Repository 层，View/Service 直接访问数据源 |
| 依赖注入 | 3 | 10 | 无 DI 框架，手动创建依赖，耦合严重 |
| 可测试性 | 2 | 10 | 无测试文件，`object` 单例 + `runBlocking` 极难测试 |
| SOLID 原则 | 6 | 10 | 部分违反 SRP、OCP、DIP |
| 代码复用 | 6 | 10 | 存在重复逻辑（ViewModel ↔ Service 之间） |
| 性能 | 7 | 10 | Compose 重组基本合理，但存在 `runBlocking` 卡顿风险 |
| 安全性 | 7 | 10 | 无明显安全漏洞，但 `fallbackToDestructiveMigration` 有数据丢失风险 |
| 资源管理 | 6 | 10 | 存在 CoroutineScope 泄漏和 Context 潜在泄漏 |
| 官方推荐遵循度 | 8 | 10 | 整体遵循 Jetpack 组件，但缺少 Repository |
| Google Play 就绪度 | 7 | 10 | 功能完整，但缺少 ProGuard 规则、测试覆盖 |

---

## ② 当前项目架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │HomeScreen│ │StatsScreen│ │SettingsScreen│ │HelpScreen│  ...   │
│  └────┬─────┘ └────┬─────┘ └─────┬─────┘ └──────────┘           │
│       │            │             │                               │
│  ┌────▼─────┐ ┌────▼─────┐ ┌─────▼──────┐                       │
│  │HomeVM    │ │StatsVM   │ │SettingsVM  │                       │
│  │(AndroidVM)│ │(AndroidVM)│ │(AndroidVM) │                       │
│  └────┬─────┘ └────┬─────┘ └─────┬──────┘                       │
├───────┼────────────┼─────────────┼───────────────────────────────┤
│       │            │             │         ⚠️ NO Repository!      │
│       ▼            ▼             ▼                               │
│  ┌────────────────────────────────────┐                          │
│  │         Data Layer                  │                          │
│  │  ┌──────────────┐ ┌──────────────┐ │                          │
│  │  │  Room DB     │ │  DataStore   │ │                          │
│  │  │  (CheckInDao)│ │  (Settings)  │ │                          │
│  │  └──────────────┘ └──────────────┘ │                          │
│  └────────────────────────────────────┘                          │
├──────────────────────────────────────────────────────────────────┤
│                    Service / Notification Layer                   │
│  ┌──────────────┐ ┌──────────────────┐ ┌──────────────────┐     │
│  │ TimerService │ │ NotificationHelper│ │ SmartDetector    │     │
│  │ (God Class)  │ │ (object, runBlock)│ │ (object, runBlock)│    │
│  └──────┬───────┘ └──────────────────┘ └──────────────────┘     │
│         │           ⚠️ 直接访问 Room/DataStore                     │
│         ▼                                                        │
│  ┌──────────────┐ ┌──────────────────┐                          │
│  │  Room DB     │ │  DataStore       │                          │
│  └──────────────┘ └──────────────────┘                          │
└──────────────────────────────────────────────────────────────────┘
```

**关键问题：** 没有 Repository 层，ViewModel 和 Service 都直接访问 Room 和 DataStore，形成"意大利面条式"依赖。

---

## ③ 当前目录结构是否合理

```
com.sitbreak.app/
├── MainActivity.kt                    ✅ 根目录，合理
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt             ✅
│   │   ├── CheckInDao.kt              ✅
│   │   └── CheckInRecord.kt           ✅
│   └── SettingsDataStore.kt           ✅
├── navigation/
│   └── NavGraph.kt                    ✅
├── notification/
│   ├── NotificationActionReceiver.kt  ✅
│   ├── NotificationHelper.kt          ✅
│   └── ReminderCopywriter.kt          ✅
├── receiver/
│   └── BootReceiver.kt                ✅
├── service/
│   ├── SmartDetector.kt               ✅
│   └── TimerService.kt                ✅
├── ui/
│   ├── activity/                       ✅ 但放在 ui/ 下不太精确
│   ├── components/                    ✅ 共用组件
│   ├── help/                          ✅
│   ├── home/                          ✅
│   ├── reminder/                      ✅
│   ├── settings/                      ✅
│   ├── splash/                        ✅
│   ├── stats/                         ✅
│   └── theme/                         ✅
└── widget/
    └── SitBreakWidget.kt              ✅
```

**评价：** 目录结构基本合理，采用 feature-based 分包。但建议：

- `ui/activity/` 下的 `HealthCenterScreen` 和 `ActivityDetailScreen` 实际上是一个独立功能模块，建议提取为 `ui/health/` 或 `ui/exercise/`
- `SmartDetector` 放在 `service/` 下不太合适，它更像一个 utility/domain 服务，建议放在 `domain/` 或 `util/` 下
- 缺少 `domain/` 目录（Repository 接口、UseCase 等）
- 缺少 `di/` 目录（依赖注入模块）

---

## ④ 是否符合 Google Android 官方推荐架构

**符合度：60%**

| 官方推荐 | 本项目 | 状态 |
|----------|--------|------|
| UI Layer (Compose + ViewModel) | ✅ 已实现 | ✅ |
| Domain Layer (UseCase) | ❌ 完全缺失 | ❌ |
| Data Layer (Repository) | ❌ 完全缺失 | ❌ |
| Single Source of Truth | ⚠️ 部分实现（DataStore/Room 各自为政） | ⚠️ |
| Unidirectional Data Flow (UDF) | ⚠️ 部分实现 | ⚠️ |
| Dependency Injection | ❌ 无 DI | ❌ |
| 后台任务 (WorkManager) | ❌ 使用 Service 而非 WorkManager | ❌ |

**具体问题：**

1. [官方推荐](https://developer.android.com/topic/architecture#recommended-app-arch) 明确要求 Repository 层，本项目完全缺失
2. 官方推荐使用 **WorkManager** 处理可延迟后台任务，本项目使用 `Service` + `while(true)` 循环
3. 官方推荐使用 **Hilt** 进行依赖注入，本项目手动创建依赖

---

## ⑤ 是否符合 Clean Architecture

**符合度：30%**

```
Clean Architecture 层          本项目实际
─────────────────────────────────────────────
Entities (Domain)              ❌ 无独立 Domain 层
Use Cases                      ❌ 完全缺失
Interface Adapters             ⚠️ ViewModel 部分扮演此角色
Frameworks & Drivers           ✅ Room/DataStore/Compose
```

**依赖规则违反：**

- [依赖规则](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture/) 要求内层不依赖外层，但本项目：
  - `TimerService`（外层）直接操作 `AppDatabase`（也是外层），没有通过接口隔离
  - `ActivityDetailScreen`（UI/外层）直接调用 `AppDatabase.getInstance()` — **严重违反**
  - `SmartDetector` 直接依赖 `SettingsDataStore`（框架层），应通过接口

---

## ⑥ 是否符合 SOLID 原则

| 原则 | 符合度 | 问题 |
|------|--------|------|
| **S** - Single Responsibility | 50% | `TimerService` 承担计时、通知、DB操作、设置读取、前景检测五重职责 |
| **O** - Open/Closed | 40% | `NotificationHelper` 为 `object`，无法扩展新通知类型 |
| **L** - Liskov Substitution | 90% | 无明显违反 |
| **I** - Interface Segregation | 30% | 无接口定义，`SettingsDataStore` 暴露 16+ 个属性/方法 |
| **D** - Dependency Inversion | 20% | 所有依赖都是具体实现，无接口抽象 |

---

## ⑦ 是否存在 MVC/MVVM 混用

**存在混用：**

| 位置 | 问题 | 严重程度 |
|------|------|----------|
| `ActivityDetailScreen.kt:L327-L335` | `recordCompletion()` 直接在 Composable 中访问 `AppDatabase` 并创建协程 | **High** |
| `ReminderActivity.kt:L66-L75` | Activity 直接 `sendBroadcast` 并操作 `Intent`，没有 ViewModel | **Medium** |
| `TimerService.kt:L216` | Service 直接操作 DB 插入记录，绕过了 MVVM 的数据流 | **Medium** |

`ActivityDetailScreen` 没有对应的 ViewModel，直接在 Composable 函数中处理业务逻辑，这是典型的 MVC 混入 MVVM。

---

## ⑧ 是否存在重复代码

**存在：**

1. **`getTodayRange()` / `getTodayStartMillis()`** — `HomeViewModel` 和 `TimerService` 各自实现了一次
   - `HomeViewModel.kt:L157-L165`
   - `TimerService.kt:L370-L376`

2. **目标计算逻辑重复** — 以下三处各自计算 `(workingHours * 60) / interval`：
   - `HomeViewModel.kt:L143-L148`
   - `StatsViewModel.kt:L62-L64`
   - `StatsViewModel.kt:L172-L176`

3. **`buildBarDataList()` 日期计算逻辑与 `getTodayRange()` 重复** — 两者都处理 Calendar 的零点计算

4. **`SettingsViewModel` 和 `SettingsDataStore` 的 getter/setter 是一一对应的代理** — 重复代码

---

## ⑨ 是否存在巨大类（God Class）

**存在 2 个：**

| 类名 | 行数 | 职责 |
|------|------|------|
| `TimerService.kt` | **407 行** | 计时、通知、DB操作、设置读取、前景检测、Service生命周期 |
| `SettingsDataStore.kt` | **200 行** | 16+ 个 Key，16 个 getter + 16 个 setter，职责膨胀 |

**建议：**
- `TimerService` 应拆分为：`TimerEngine`（计时逻辑）+ `ReminderManager`（提醒决策）+ `NotificationSender`（通知发送）
- `SettingsDataStore` 应拆分为：`TimerSettings` + `NotificationSettings` + `ReminderSettings`

---

## ⑩ 是否存在巨大方法（Long Method）

| 方法 | 位置 | 行数 | 问题 |
|------|------|------|------|
| `tick()` | `TimerService.kt:L182-L219` | ~38 行 | 包含久坐、微休息、喝水、护眼四种提醒逻辑 |
| `loadSettingsAndStartTicking()` | `TimerService.kt:L122-L148` | ~27 行 | 读取 13 个设置值并初始化 |
| `SettingsScreen()` | `SettingsScreen.kt` | **500+ 行** | 整个设置页面在一个 Composable 中 |

---

## ⑪ 是否存在重复逻辑

**存在：**

1. **`HomeViewModel` 和 `TimerService` 的 `standUp` 处理逻辑重复** — 两者都操作 `sittingStartTime`、`microBreakStartTime`、插入 DB 记录
   - `HomeViewModel.kt:L120-L130`
   - `TimerService.kt:L221-L234`

2. **`HomeViewModel` 和 `TimerService` 的 `snooze` 逻辑重复** — 两者都执行 `sittingStartTime += 5min`
   - `HomeViewModel.kt:L132-L135`
   - `TimerService.kt:L236-L241`

3. **`HomeViewModel.refreshTodayStats()` 和 `StatsViewModel.loadWeeklyStats()` 逻辑高度相似** — 两者都计算 target、查询 DB、计算完成率

---

## ⑫ 是否存在循环依赖

**无直接循环依赖，但存在隐式循环：**

```
HomeViewModel ──调用──▶ TimerService (静态方法)
     │                       │
     ▼                       ▼
SettingsDataStore ◀──读写── SettingsDataStore
     │                       │
     ▼                       ▼
  (同一实例，但双方各自持有状态副本)
```

不是严格的循环依赖，但 `HomeViewModel` 和 `TimerService` 通过 `SettingsDataStore` 形成了**共享可变状态**，这是一种逻辑上的循环耦合。

---

## ⑬ 是否存在资源泄漏

**存在以下资源泄漏风险：**

| 位置 | 问题 | 严重程度 |
|------|------|----------|
| `ActivityDetailScreen.kt:L327-L335` | `recordCompletion()` 创建新的 `CoroutineScope(Dispatchers.IO)` 但从不取消，随 Composable 重组泄漏 | **High** |
| `TimerService.kt:L27` | `scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` 在 `onDestroy()` 中取消，但依赖系统调用时机 | **Medium** |
| `SettingsScreen.kt` | `MediaPlayer` 在 `SoundPickerSheet` 中创建，若 Composable 快速重组可能未释放 | **Medium** |

---

## ⑭ 是否存在 Context 泄漏

**存在以下 Context 泄漏风险：**

| 位置 | 问题 | 严重程度 |
|------|------|----------|
| `SettingsDataStore.kt:L15` | `SettingsDataStore` 持有 `Context` 引用，但使用 `AndroidViewModel` 的 `Application` Context，**安全** | ✅ |
| `AppDatabase.kt:L17-L28` | 传入 `context.applicationContext`，**安全** | ✅ |
| `TimerService.kt` | `ActivityManager` 和 `TelephonyManager` 在 Service 中使用，Service 生命周期内安全 | ✅ |
| `SmartDetector.kt:L46` | `SettingsDataStore(context)` 每次调用都创建新实例 — 虽然使用 `object` 不会持有引用，但**创建开销大** | **Low** |

**总体评价：Context 泄漏控制较好，主要得益于 `AndroidViewModel` 的 `Application` Context。**

---

## ⑮ 是否存在 Compose 重组性能问题

**存在以下问题：**

| 位置 | 问题 | 严重程度 |
|------|------|----------|
| `StatsScreen.kt:L310-L340` | `BarChart` 和 `MonthlyBarChart` 的 Canvas 中每次绘制都创建新的 `android.graphics.Paint()` 对象 | **Medium** |
| `HomeScreen.kt:L66-L68` | 未使用 `derivedStateOf` 对 `progress` 进行优化，每次重组都重新计算 | **Low** |
| `SettingsScreen.kt` | 大型单一 Composable 函数，任意状态变化导致整页重组 | **Medium** |
| `HomeScreen.kt:L454-L460` | `ActivityRecordItem` 中 `SimpleDateFormat` 每次调用都创建新实例 | **Low** |

**建议：**
- Canvas 中的 `Paint` 对象应使用 `remember` 缓存
- 大型 Composable 应拆分为更小的 `@Composable` 函数，配合 `@Stable`/`@Immutable` 注解
- `SimpleDateFormat` 应作为 `companion object` 或顶层常量

---

## ⑯ 是否存在 Room 查询效率问题

| 问题 | 位置 | 严重程度 |
|------|------|----------|
| 无索引 | `CheckInRecord.kt` | `timestamp` 和 `type` 列频繁用于 WHERE 条件但无索引 | **Medium** |
| `getAllDistinctDays()` 全表扫描 | `CheckInDao.kt:L62-L63` | 使用 `SELECT DISTINCT` + 除法计算，全表扫描 | **Medium** |
| `calculateLongestStreak()` 逐天查询 | `StatsViewModel.kt:L84-L107` | 对每个 distinct day 都执行一次 `getTodayCountByType` 查询，N+1 问题 | **Medium** |
| `fallbackToDestructiveMigration()` | `AppDatabase.kt:L23` | 数据库升级时数据全部丢失 | **High** |

**建议：**
- 在 `CheckInRecord` 的 `timestamp` 和 `type` 列添加索引
- 用单次查询替代 N+1 查询
- 实现 Migration 策略替代 `fallbackToDestructiveMigration()`

---

## ⑰ 是否存在 DataStore 误用

| 问题 | 位置 | 严重程度 |
|------|------|----------|
| 运行时状态存入 DataStore | `SettingsDataStore.kt:L44-L45` | `currentElapsedMinutes` 和 `currentStandCount` 是运行时状态，不应持久化 | **High** |
| `SettingsDataStore(context)` 频繁创建 | `SmartDetector.kt:L46` | 每次调用 `isFullScreenApp()` 都创建新 `SettingsDataStore` 实例 | **Medium** |
| `SettingsViewModel.loadSettings()` 使用 `first()` 而非 `collect` | `SettingsViewModel.kt:L67-L82` | 只读取一次，后续其他组件修改设置不会同步到 ViewModel | **Medium** |

**特别是 `currentElapsedMinutes` 和 `currentStandCount`：**
- 这些值是临时的 UI 状态，每次写入 DataStore 都会触发磁盘 I/O
- 高频写入（每 15 秒一次 via `updateServiceNotification()`）会严重消耗磁盘寿命
- 应使用 `MutableStateFlow` 或 `StateFlow` 管理运行时状态

---

## ⑱ 是否存在 Service 生命周期问题

| 问题 | 位置 | 严重程度 |
|------|------|----------|
| `START_NOT_STICKY` | `TimerService.kt:L79` | 系统杀死 Service 后不会自动重启，虽然有 `BootReceiver` 但杀进程场景不覆盖 | **Medium** |
| `onTaskRemoved` 停止 Service | `TimerService.kt:L83-L89` | 用户滑动清除任务时停止计时，但 `BootReceiver` 会在开机时自动启动 | ⚠️ 设计意图 |
| `while(true)` 循环 | `TimerService.kt:L146-L148` | 无限循环 + 15s delay，如果 Service 停止但协程未取消会导致泄漏 | **Medium** |

**建议：** 使用 `WorkManager` 的 `PeriodicWorkRequest` 替代 Service 的 `while(true)` 循环，更符合 Android 官方推荐。

---

## ⑲ 是否存在 Notification 设计问题

| 问题 | 位置 | 严重程度 |
|------|------|----------|
| 缺少应用图标 | `NotificationHelper.kt:L93` | 使用 `android.R.drawable.ic_dialog_info`（系统默认图标）而非应用自定义图标 | **Medium** |
| 每次 build 都调用 `runBlocking` | `NotificationHelper.kt:L251-L253` | `resolveNotificationSoundUri()` 使用 `runBlocking` 读取 DataStore | **High** |
| `createChannels` 先删除再创建 | `NotificationHelper.kt:L38-L40` | 每次调用都删除重建渠道，用户自定义设置会丢失 | **Medium** |
| 缺少通知分组 | `NotificationHelper.kt` | 多种通知类型（久坐、微休息、喝水、护眼）未使用 `setGroup()` 分组 | **Low** |

---

## ⑳ 所有问题分类汇总

### 🔴 Critical（严重，必须立即修复）

| # | 问题 | 位置 | 说明 |
|---|------|------|------|
| C1 | **`runBlocking` 在主线程调用** | `SmartDetector.kt:L46` | `isFullScreenApp()` 在 `tick()` 中调用，`runBlocking` 阻塞主线程，可能导致 ANR |
| C2 | **`runBlocking` 在通知构建中使用** | `NotificationHelper.kt:L251-L253` | `resolveNotificationSoundUri()` 每次构建通知都阻塞线程读取 DataStore |
| C3 | **运行时状态错误写入 DataStore** | `SettingsDataStore.kt:L191-L199` | `currentElapsedMinutes`/`currentStandCount` 每 15 秒写入磁盘，严重消耗 I/O |
| C4 | **数据库迁移策略导致数据丢失** | `AppDatabase.kt:L23` | `fallbackToDestructiveMigration()` 在版本升级时销毁所有用户数据 |

### 🟠 High（高优先级，应尽快修复）

| # | 问题 | 位置 |
|---|------|------|
| H1 | **缺少 Repository 层** — ViewModel 和 Service 直接访问 DAO/DataStore | 全局 |
| H2 | **缺少依赖注入框架** — 手动创建依赖，耦合严重，不可测试 | 全局 |
| H3 | **TimerService 是 God Class** — 407 行，承担 5 种以上职责 | `TimerService.kt` |
| H4 | **CoroutineScope 泄漏** — `ActivityDetailScreen.recordCompletion()` 创建未管理的 Scope | `ActivityDetailScreen.kt:L327` |
| H5 | **`ActivityDetailScreen` 无 ViewModel** — Compose 中直接访问 DB（MVC 混入 MVVM） | `ActivityDetailScreen.kt:L327` |
| H6 | **`setSound(null, null)` 后通知渠道无声音** — 之后无法通过代码恢复声音 | `NotificationHelper.kt:L48` |
| H7 | **无测试覆盖** — 整个项目零测试文件 | 全局 |

### 🟡 Medium（中优先级，应计划修复）

| # | 问题 | 位置 |
|---|------|------|
| M1 | **`SettingsViewModel` 是纯代理** — 无业务逻辑，只转发调用到 DataStore | `SettingsViewModel.kt` |
| M2 | **`HomeViewModel` 和 `TimerService` 逻辑重复** — standUp/snooze 逻辑两处实现 | `HomeViewModel.kt` / `TimerService.kt` |
| M3 | **Room 缺少索引** — `timestamp` 和 `type` 列频繁查询但无索引 | `CheckInRecord.kt` |
| M4 | **N+1 查询问题** — `calculateLongestStreak()` 对每个 distinct day 单独查询 | `StatsViewModel.kt:L84-L107` |
| M5 | **Canvas 中重复创建 Paint 对象** — 每次绘制都 new | `StatsScreen.kt:L310` |
| M6 | **`SettingsScreen` 巨大 Composable** — 500+ 行，全页重组 | `SettingsScreen.kt` |
| M7 | **无暗色主题** — `SitBreakTheme` 接受 `darkTheme` 参数但忽略 | `Theme.kt:L40` |
| M8 | **Widget 数据硬编码** — 显示"今日站立 0 次"纯静态 | `SitBreakWidget.kt:L57-L66` |
| M9 | **`NotificationHelper` 为 `object` 单例** — 无法测试，无法 mock | `NotificationHelper.kt` |
| M10 | **`SmartDetector` 为 `object` 单例** — 无法测试，无法 mock | `SmartDetector.kt` |
| M11 | **`SettingsViewModel.loadSettings()` 只读一次** — 不响应其他组件修改 | `SettingsViewModel.kt:L67-L82` |
| M12 | **`createChannels()` 每次都删除重建通知渠道** | `NotificationHelper.kt:L38-L40` |
| M13 | **使用 Service 而非 WorkManager** — 不符合 Android 官方推荐 | `TimerService.kt` |

### 🟢 Low（低优先级，后续优化）

| # | 问题 | 位置 |
|---|------|------|
| L1 | `SimpleDateFormat` 每次创建新实例 | `HomeScreen.kt:L454` |
| L2 | 缺少通知分组 | `NotificationHelper.kt` |
| L3 | 使用系统默认图标 `android.R.drawable.ic_dialog_info` | `NotificationHelper.kt` |
| L4 | `ReminderActivity` 使用 `Handler` 而非协程 | `ReminderActivity.kt:L53` |
| L5 | `ReminderCopywriter` 使用 `list.random()` 不可测试 | `ReminderCopywriter.kt:L69` |
| L6 | `SmartDetector` 放在 `service/` 目录不合适 | `SmartDetector.kt` |
| L7 | 缺少 `libs.versions.toml` 版本目录 | 全局 |
| L8 | `proguard-rules.pro` 文件未找到 | 全局 |
| L9 | `StatCard` 全限定引用 `androidx.compose.ui.graphics.Color.Black` | `StatCard.kt:L32-L33` |

---

## 📊 总结

这是一个**功能完整但架构需要重构**的项目。核心问题可归纳为：

1. **架构层面**：缺少 Repository 层和 DI 框架，导致各层直接耦合，难以测试和维护
2. **性能层面**：`runBlocking` 在主线程使用、DataStore 存储运行时状态频繁 I/O
3. **代码质量**：God Class（TimerService）、重复代码、MVC/MVVM 混用
4. **可维护性**：无测试、无接口抽象、无状态管理模式

**建议优先修复的 Top 5：**
1. 移除所有 `runBlocking` 调用
2. 将 `currentElapsedMinutes`/`currentStandCount` 改为内存状态
3. 添加 Repository 层统一数据访问
4. 为 `ActivityDetailScreen` 添加 ViewModel
5. 拆分 `TimerService` 职责
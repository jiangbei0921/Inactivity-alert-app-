# 架构决策记录（ADR）

本文件记录「站一站」在实现过程中做过的、**有取舍成本**的技术选择。
每条决策包含：上下文（为什么要做决定）、可选方案、最终选择与代价。
不记录没有争议的选择（例如「用 Kotlin」「用 Compose」）。

---

## ADR-001 计时状态的真源放在前台 Service，而不是 ViewModel

**日期**：2026-08 · **状态**：已采纳

### 上下文
久坐计时必须在用户切走 App、锁屏、甚至 Activity 被系统回收后继续走。
最初实现把「已坐多久」放在 `HomeViewModel` 里用协程自增，切后台一段时间后被系统回收，
回到前台计时归零，用户投诉「计时器自己重置了」。

### 可选方案
1. ViewModel 内计时 + `SavedStateHandle` 持久化
2. `AlarmManager` 定点唤醒
3. 前台 Service 持续心跳 + 全局 `StateFlow` 广播状态

### 决策
选 3。

- `TimerService`（`foregroundServiceType="specialUse"`）每约 15s 检查一次；
- **不在内存里累加时间**，而是把 `sittingStartTime`（时间戳）写进 DataStore，
  任何时刻的「已坐时长」都由 `System.currentTimeMillis() - startTime` 算出；
- `TimerStateHolder` 持有 `StateFlow<TimerState>`，UI 只订阅不写入。

这样即使进程被杀、Service 被重建，时间也不会漂移。

### 代价
- 必须常驻一条前台通知，用户可见；
- Android 14 起需要声明 `FOREGROUND_SERVICE_SPECIAL_USE` 并在应用商店说明用途；
- 15s 心跳意味着提醒最大有 15s 误差 —— 对「久坐提醒」这个场景完全可接受，
  换来的是比 1s 轮询低一个量级的耗电。

---

## ADR-002 引入 Hilt 做依赖注入

**日期**：2026-08 · **状态**：已采纳

### 上下文
重构前所有 ViewModel 都是 `AndroidViewModel(application)`，在 `init` 里手写：

```kotlin
private val repository = CheckInRepository(AppDatabase.getInstance(application).checkInDao())
private val settings = SettingsDataStore(application)
```

带来两个实际问题：
1. **不可测**：想写 ViewModel 单测就必须造一个 `Application`，只能上 Robolectric，慢且脆；
2. **隐式单例**：`SettingsDataStore(context)` 每个 ViewModel 各 new 一个，
   DataStore 本身有进程内单例保护才没出事，属于「靠运气正确」。

### 可选方案
1. 维持手动构造 + 自己写一个 ServiceLocator
2. Koin（运行时解析、上手快）
3. Hilt（编译期生成、Google 官方、和 Jetpack 深度集成）

### 决策
选 3（Hilt 2.50）。

- `@HiltAndroidApp SitBreakApplication`；
- `AppModule` 用 `@Provides @Singleton` 统一提供 `SettingsDataStore` / `CheckInRepository` / `StandingValidator`；
- ViewModel 改成 `@HiltViewModel class XxxViewModel @Inject constructor(...)`，
  Context 通过 `@ApplicationContext` 注入而非继承 `AndroidViewModel`；
- Service 加 `@AndroidEntryPoint`，`NotificationHelper` / `SmartDetector` 由 `object` 改为可注入的类；
- Compose 侧默认参数从 `viewModel()` 换成 `hiltViewModel()`。

### 代价
- 增加 KSP 注解处理，冷构建时间上升（本项目约 +15%）；
- 编译期错误信息较长，需要习惯；
- 对小项目属于「超配」，但本项目的核心目的之一就是让 ViewModel 可测，这笔投入直接兑现。

---

## ADR-003 每日小结用 WorkManager，且**不**接 `@HiltWorker`

**日期**：2026-08 · **状态**：已采纳

### 上下文
希望每天早上给用户一条「昨天站起来了几次」的小结。

### 可选方案
1. `AlarmManager.setExactAndAllowWhileIdle` 定点唤醒
2. 前台服务里判断「是否跨天」
3. `WorkManager` 周期任务

### 决策
选 3。小结是**可延迟**任务，没有精确到分钟的必要：

- WorkManager 会把多个 App 的唤醒合并，遵守 Doze/低电量约束，比精确闹钟省电得多；
- 重启后调度自动恢复，不用自己写 `BOOT_COMPLETED` 处理；
- `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` 保证每次冷启动调用都是幂等的。

**关于 `@HiltWorker` 的额外取舍**：接 `androidx.hilt:hilt-work` 需要
Application 实现 `Configuration.Provider`，并在 Manifest 里移除 WorkManager 默认的
`androidx.startup` 初始化器。这是一处**只会在真机冷启动时暴露**的配置风险，
而本项目的 Worker 依赖（Repository、NotificationHelper）都能从 `applicationContext` 直接组装、
且底层本身就是单例。因此选择在 Worker 内部手动组装，把 Hilt 的边界收在 UI/Service 层。
如果后续 Worker 依赖变复杂（例如需要注入网络层），再引入 `@HiltWorker`。

### 代价
- 触发时间不精确（WorkManager 周期任务最小间隔 15 分钟，实际触发可能晚十几分钟）；
- Worker 内部的依赖组装没有走 DI，测试时无法直接替换 —— 所以把**真正需要测的时间计算逻辑**
  抽到了 `DailySummarySchedule`（纯 Kotlin，不依赖任何 Android 类）。

---

## ADR-004 站立验证用步数传感器阈值，而不是训练一个活动识别模型

**日期**：2026-08 · **状态**：已采纳

### 上下文
「我站起来了」按钮完全靠自觉，统计数据可以随手刷满，可信度为零。
需要一种方式验证用户是否真的起身活动了。

### 可选方案
1. 接 Google Play Services 的 Activity Recognition API（云端模型 + GMS 依赖）
2. 采集加速度计/陀螺仪原始数据，端上跑一个轻量分类模型（TFLite）
3. 用 `TYPE_STEP_COUNTER` 传感器，在一次计时周期内累计步数，超过阈值即判定「已起身」

### 决策
选 3，阈值取 3 步。

判据是**产品定位优先于技术复杂度**：本 App 的核心承诺是「不联网、不上传、无账号」。
方案 1 引入 GMS 依赖并可能触发云端调用，直接违背隐私承诺；
方案 2 需要采集训练数据、评估精度、承担模型体积与推理耗电，而收益仅仅是把
「站起来」和「在椅子上晃动」区分得更细 —— 对一个鼓励型健康工具来说，
过度精确反而会带来「我明明站起来了它不认」的挫败感。

### 降级设计（重点）
- 无 `TYPE_STEP_COUNTER` 传感器 → `supported = false`；
- Android 10+ 未授予 `ACTIVITY_RECOGNITION` 运行时权限 → `supported = false`；
- `registerListener` 抛异常 → `supported = false`；
- **`standingLikely()` 在不支持时恒返回 `false`，绝不返回「已验证」**，
  并且验证失败不会阻止打卡记录写入 —— 验证只是加分项，不是前置条件。

这套降级路径有专门的单测 `StandingValidatorTest` 兜底。

### 代价
- 无法区分「站起来走动」与「坐着抖腿被计成步数」，存在误判上限；
- 阈值 3 步是拍脑袋的经验值，没有数据支撑（后续可做 A/B）。

---

## ADR-005 业务口径计算抽成纯函数对象

**日期**：2026-08 · **状态**：已采纳

### 上下文
「今日完成率」原本内联在 `HomeViewModel.refreshTodayStats()` 里：

```kotlin
val targetStands = (workingHours * 60) / intervalMinutes   // intervalMinutes 为 0 时崩溃
```

这类计算恰恰是最容易写错、也最需要回归保护的部分，却因为耦合在 ViewModel 里而无法被测。

### 决策
抽出 `HomeStatsCalculator`（完成率/活动时长）与 `DailySummarySchedule`（调度时刻），
两者都是无副作用、不依赖任何 Android 类的 `object`，用 JVM 单测把边界锁死：
除零、负值、上限截断、目标不可计算时保持旧值（而不是错误地显示 0%）、延迟不得为负。

### 代价
多了两个类和一层间接调用。相比「线上崩在 `/ 0` 上」，这个成本可以忽略。

---

## ADR-006 提醒渠道按「设置内容」动态生成 ID

**日期**：2026-08（沿用既有实现） · **状态**：已采纳

### 上下文
Android 8.0 起，`NotificationChannel` 的声音、震动等属性**创建后不可修改**。
用户在设置页把提醒声音从 A 换成 B，如果沿用同一个 channel id，改动不会生效。

### 决策
把「是否开声音 / 是否震动 / 铃声索引」编码进 channel id：
`sitting_reminder_1_1_2`。设置变化 → 新 id → 创建新渠道；
同时遍历 `manager.notificationChannels` 删除同前缀的旧渠道，避免系统设置里堆积一长串废弃渠道。

### 代价
用户如果在系统设置里手动调过某个渠道，改动会随渠道重建而丢失。
这是 Android 渠道模型的固有限制，业界普遍做法一致。

---

## ADR-007 Release 签名信息从环境变量读取，未配置时静默跳过

**日期**：2026-08 · **状态**：已采纳

### 上下文
开启 R8 后需要 release 签名。但 keystore 与密码**绝对不能进仓库**，
同时又不能让「没有 keystore 的人 clone 下来直接构建失败」。

### 决策
```kotlin
val keystorePath = System.getenv("SIGNING_KEYSTORE")
if (keystorePath != null) {
    signingConfig = signingConfigs.create("release") { /* 从环境变量读取 */ }
}
```
配置了环境变量就签名，没配置就产出未签名包。CI 里通过 GitHub Secrets 注入。

### 代价
构建结果依赖环境，`assembleRelease` 在不同机器上产物不同（签名/未签名）。
已在 README 的「测试与质量保障」中明确说明。

# 站一站 · 修复前后对比文档

> 本次基于历史审计清单与未完成任务逐项排查，聚焦**动作总线、暂停/周期提醒、通知渠道、提醒取消、天文数字、MVVM 规范**等模块，已完成修复并通过完整测试套件。

---

## 1. 修复总览

| 编号 | 问题 | 修复前 | 修复后 | 关键改动文件 |
|------|------|--------|--------|--------------|
| C7 | 动作总线丢事件 | 通知按钮通过 `BroadcastReceiver` 转发给 `TimerService`，Android 12+ 后台启动前台服务受限，事件易丢失 | 通知按钮直接使用 `PendingIntent.getForegroundService()` 启动 `TimerService`，去除中间广播环节 | `NotificationHelper.kt`, `ReminderActivity.kt` |
| C8 | 暂停死锁与天文数字 | 从 DataStore 读取旧 `sittingStartTime`，跨天/崩溃后恢复时久坐分钟数可能变成数百/数千 | 加载时校验时间戳是否为今天，非当天则重置为 `now`，避免天文数字；暂停逻辑保留补偿 | `TimerService.kt`, `TimeUtils.kt` |
| C9 | 周期提醒 | 久坐提醒只触发一次，用户未响应不再重复提醒 | 首次触发后每 5 分钟重复提醒一次，直到用户处理（站起/推迟/停止） | `TimerService.kt` |
| C10 | 渠道声音与震动 | 渠道创建时固定声音/震动配置，用户后续在 App 内修改设置不生效 | 提醒渠道 ID 编码当前声音/震动/铃声索引；设置变更时自动删除旧渠道并创建新渠道 | `NotificationHelper.kt` |
| C15 | 提醒通知不取消 | 点击「推迟」或「暂停」后，旧的久坐提醒通知仍挂在通知栏 | `handleSnooze()` 与 `handlePause()` 均调用 `cancelReminderNotifications()` | `TimerService.kt` |
| C6/C12/C14/C16/C17 | 残留的 Critical/架构问题 | `BootReceiver` 仍有 `runBlocking` 阻塞主线程；`ActivityDetailScreen` 直接在 UI 层访问 `AppDatabase` | `BootReceiver` 改用 `goAsync() + CoroutineScope`；新增 `ActivityDetailViewModel` 负责写入 | `BootReceiver.kt`, `ActivityDetailScreen.kt`, `ActivityDetailViewModel.kt` |
| 规范 | 重复导入、未用参数 | `SettingsScreen` 重复导入 `LocalContext`；移除 `soundEnabled` 参数后 `TimerService` 保留未用字段 | 删除重复导入；移除 `TimerService.isSoundEnabled` 未用字段 | `SettingsScreen.kt`, `TimerService.kt` |

---

## 2. 详细对比

### 2.1 C7 — 动作总线丢事件

**修复前**（`NotificationHelper.kt`）
```kotlin
val standUpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
    action = ACTION_STAND_UP
}
val standUpPendingIntent = PendingIntent.getBroadcast(...)
```

**修复后**
```kotlin
private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, TimerService::class.java).apply {
        this.action = action
    }
    return PendingIntent.getForegroundService(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

`ReminderActivity` 中的按钮也从 `sendBroadcast()` 改为 `startForegroundService()` 直接调用。

### 2.2 C8 — 暂停死锁与天文数字

**修复前**（`TimerService.kt`）
```kotlin
sittingStartTime = settingsDataStore.sittingStartTime.first()
// 未校验：若值为几天前，(now - sittingStartTime)/60_000 可达数千分钟
```

**修复后**
```kotlin
val now = System.currentTimeMillis()
if (sittingStartTime <= 0L || !TimeUtils.isToday(sittingStartTime)) {
    sittingStartTime = now
    microBreakStartTime = now
    settingsDataStore.setSittingStartTime(sittingStartTime)
    settingsDataStore.setMicroBreakStartTime(microBreakStartTime)
}
```

新增 `TimeUtils.isToday(timestamp)` 工具函数，按设备本地时区判断。

### 2.3 C9 — 周期提醒

**修复前**：`sittingReminderSent` 置 true 后不再提醒。

**修复后**（`TimerService.kt`）
```kotlin
if (sittingReminderSent && now - sittingReminderSentTime >= SITTING_REMINDER_REPEAT_MS) {
    sittingReminderSentTime = now
    NotificationHelper.sendSittingReminder(this, sittingElapsed.toInt(), isVibrationEnabled)
}
```

新增 `SITTING_REMINDER_REPEAT_MS = 5 分钟` 常量。

### 2.4 C10 — 渠道声音与震动

**修复前**：固定渠道 ID `sitting_reminder` / `micro_break`，创建后无法修改声音/震动。

**修复后**：动态渠道 ID，例如 `sitting_reminder_1_1_0`（声音开、震动开、铃声索引 0）。`createChannels()` 在设置变更时清理旧渠道并创建新渠道，确保 Android O+ 上设置实时生效。

### 2.5 C15 — 提醒通知不取消

**修复前**：
```kotlin
private suspend fun handleSnooze() { ... }       // 未取消通知
private suspend fun handlePause() { ... }          // 未取消通知
```

**修复后**：两者均追加 `cancelReminderNotifications()`，用户推迟或暂停后旧的久坐/微休息/喝水/护眼通知立即消失。

### 2.6 架构规范修复

| 文件 | 修复内容 |
|------|----------|
| `BootReceiver.kt` | 移除 `runBlocking`，改用 `goAsync() + CoroutineScope(Dispatchers.IO)` |
| `ActivityDetailViewModel.kt` | 新增 ViewModel，封装运动完成记录的 DB 写入 |
| `ActivityDetailScreen.kt` | 通过 `viewModel()` 获取 `ActivityDetailViewModel`，移除直接 `AppDatabase` 访问 |
| `SettingsScreen.kt` | 删除重复的 `LocalContext`/`stringResource`/`R` 导入 |
| `TimerService.kt` | 移除未用的 `isSoundEnabled` 字段；`onStartCommand` 返回 `START_REDELIVER_INTENT` 提升服务可靠性 |

---

## 3. 验证结果

- **编译**：`compileDebugKotlin` 通过，**代码零 warning / 零 error**（仅 Android SDK 工具链提示，与代码无关）。
- **单元测试**：`testDebugUnitTest` 全部通过（`SettingsDataStoreTest`、`NotificationHelperTest`、`ReminderCopywriterTest`）。
- **打包**：`assembleDebug` 成功，生成 `app-debug.apk`（约 18.2 MB）。

```text
BUILD SUCCESSFUL in 5m 35s
42 actionable tasks: 42 executed
```

---

## 4. 已知保留项

- `NotificationActionReceiver` 仍保留在 `AndroidManifest.xml` 中，作为旧版通知 PendingIntent 的兼容兜底（旧通知若仍指向广播接收器，不会崩溃）。下一版本可安全移除。
- 未引入新的网络权限或第三方 SDK，隐私定位不变。
- 未做 UI 大改，保持现有交互流程。

---

## 5. 后续建议（仍可按需继续）

- 接入 Health Connect / 厂商健康（M9）
- 首启 Onboarding 引导（M4）
- 本地埋点体系（M2）
- Release 签名 + R8 + 应用商店上架（M5）

---

*文档生成时间：2026-08-08*

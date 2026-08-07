# 站一站 —— 久坐健康提醒助手

**站一站** 是一款专为久坐工作者设计的 Android 健康提醒应用，帮助你在专注工作时定时起身活动，养成健康的工作习惯。

---

## 功能特性

### 主页 —— 久坐计时
- 点击「开始计时」启动久坐监测，圆环进度条实时显示已坐时长
- 到达提醒间隔后推送通知，可选择「我站起来了」记录打卡或「推迟 5 分钟」
- 今日记录卡片展示当日站立次数、完成率、活跃时长
- 可展开查看今日全部打卡记录时间线

### 统计 —— 数据看板
- 近 7 天每日站立次数柱状图，对比每日目标
- 周均完成率、总打卡次数、最长连续达标天数
- 年度统计：每月站立次数、年度完成率、最佳月份

### 设置 —— 个性化配置
- **久坐提醒间隔**：10 / 15 / 20 / 25 / 30 / 35 / 40 / 45 / 50 / 55 / 60 / 75 / 90 分钟或自定义（1~180 分钟）
- **微休息间隔**：5 / 10 / 15 / 20 / 25 / 30 分钟或自定义（1~180 分钟），可开关
- **工作时间段**：滑动选择起止时间，非工作时间自动暂停计时
- **提醒日期**：周一至周日多选，支持「工作日」「每天」「清空」快捷选项
- **提醒声音**：多套系统铃声可选，支持试听
- **振动**：开关

### 帮助 —— 使用指南
- APP 使用教程（5 步上手）
- 适用人群介绍
- 久坐危害科普
- 意见反馈（邮件）

---

## 技术架构

```
com.sitbreak.app
├── data/
│   ├── db/                  # Room 数据库：打卡记录持久化
│   │   ├── AppDatabase.kt
│   │   ├── CheckInDao.kt
│   │   └── CheckInRecord.kt
│   └── SettingsDataStore.kt # DataStore Preferences：设置项存储
├── navigation/
│   └── NavGraph.kt          # 导航图 + 底部导航栏
├── notification/
│   ├── NotificationHelper.kt       # 通知构建与发送
│   └── NotificationActionReceiver.kt # 通知栏快捷操作
├── receiver/
│   └── BootReceiver.kt      # 开机自启
├── service/
│   └── TimerService.kt      # 前台服务：后台计时核心
└── ui/
    ├── components/          # 通用 UI 组件
    ├── home/                # 主页
    ├── stats/               # 统计页
    ├── settings/            # 设置页
    ├── help/                # 帮助页
    ├── splash/              # 启动页
    └── theme/               # 主题色彩
```

### 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + StateFlow) |
| 数据库 | Room |
| 键值存储 | DataStore Preferences |
| 导航 | Navigation Compose |
| 后台服务 | Foreground Service |
| 通知 | NotificationCompat (渠道化通知) |

### 计时流程

```
用户点击「开始计时」
    → HomeViewModel.startTimer()
        → DataStore 写入 sittingStartTime
        → 启动 TimerService（前台服务）
    → TimerService 每 15s 轮询一次
        → 检查是否在工作时间 + 启用日期
        → 检查是否达到提醒间隔
        → 达到则推送通知
    → 用户点击「我站起来了」
        → 重置计时 + 写入 Room 数据库
    → 用户上滑关闭 APP
        → onTaskRemoved 清除计时状态
```

---

## 构建与运行

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17（Android Studio 内置 JBR 亦可）
- Android SDK 34
- Gradle 8.x

### 编译安装

```powershell
# 设置 JAVA_HOME（使用 Android Studio 内置 JBR）
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 编译并安装到已连接设备
.\gradlew.bat installDebug

# 使用 adb 启动
adb shell am start -n com.sitbreak.app/.MainActivity
```

或使用项目根目录的 `run.ps1` 一键脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

### 权限说明

| 权限 | 用途 |
|------|------|
| `FOREGROUND_SERVICE` | 后台计时服务 |
| `POST_NOTIFICATIONS` | 推送久坐提醒 |
| `VIBRATE` | 提醒振动 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启动 |

---

## 版本

**v1.0.0** — 首个正式版本

- 最低支持 Android 8.0 (API 26)
- 目标版本 Android 14 (API 34)

---

## 联系

邮箱：2185428966@qq.com
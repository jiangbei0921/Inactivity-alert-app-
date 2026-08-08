# 后续规划（Roadmap）

按「投入 / 收益」排序。已明确**不做**的事项也一并记录，避免反复讨论。

---

## 近期（下一个迭代）

### 1. 仪表化测试与截图测试
现状：只有 JVM 单元测试，Compose UI 没有自动化验证。
计划：接 `androidx.compose.ui:ui-test-junit4` + `HiltAndroidRule`，
覆盖「首启引导三页可翻页并落盘」「无通知权限时横幅可见且可点击」两条关键路径；
CI 用 `reactivecircus/android-emulator-runner` 跑 `connectedDebugAndroidTest`。

阻塞点：模拟器任务在 GitHub Actions 免费额度下单次约 8~12 分钟，
需要先把它拆成独立 workflow 只在 `main` 上跑，避免拖慢每个 PR。

### 2. 多模块化
现状：单 `app` 模块，约 40 个源文件。
计划：拆成 `:core:data`（Room + DataStore + Repository）、`:core:domain`（纯 Kotlin 计算）、
`:feature:home` / `:feature:stats` / `:feature:settings`、`:app`（壳）。

判据：模块化的真正收益是**并行构建**与**依赖边界强制**。
当前规模下增量构建已在 20s 内，收益有限；
等源文件超过 100 个、或团队人数 > 2 时再动，属于「等真痛了再治」。

### 3. Baseline Profile
计划：用 `androidx.benchmark:benchmark-macro-junit4` 生成启动路径的 Baseline Profile，
让 Compose 首帧走 AOT 而非解释执行。同类应用一般能把冷启动 TTID 降 15%~25%。

---

## 中期

### 4. Health Connect 集成
把站立打卡写入 Health Connect（`ExerciseSessionRecord` / `StepsRecord`），
与三星健康、Google Fit 等打通。

**前置约束**：必须保持「默认不写、用户显式开启」，
且要在设置页明确告知「开启后数据会离开本 App」——
这与现有的隐私承诺存在张力，需要在 UI 上讲清楚，而不是偷偷同步。

### 5. Wear OS 伴侣应用
手表端做「震动提醒 + 抬腕确认站立」，比手机通知的触达率高得多。
技术路径：`Wearable Data Layer API` 同步计时状态，手表端用 Compose for Wear OS。

成本较高（新 target + 新 UI + 配对调试），排在 Health Connect 之后。

### 6. 数据导出与备份
导出 CSV / JSON，用 SAF（`ACTION_CREATE_DOCUMENT`）让用户自己选位置。
配合 Room 的 `autoMigrations` 做版本演进。这是「本地优先」应用的必备能力——
用户换手机时不该丢掉一年的记录。

---

## 明确不做

| 事项 | 原因 |
|------|------|
| 云端账号与数据同步 | 与「不联网、不上传、无账号」的核心承诺直接冲突。要做也是端到端加密的可选项，不作为默认 |
| 崩溃上报 / 埋点 SDK | 同上。开源仓库 + Issue 反馈已经能覆盖主要问题定位 |
| 广告与内购 | 会污染提醒场景本身（用户正被打扰时看到广告，体验灾难） |
| 端上跑活动识别模型 | 见 ADR-004。收益是把「站立」和「抖腿」分得更细，代价是模型体积、耗电与误判挫败感，不划算 |
| 精确到秒的提醒 | 需要 `SCHEDULE_EXACT_ALARM`（Android 14 起需用户手动授权），而久坐提醒差 15 秒毫无影响 |

---

## 已知技术债

1. **`TimerService` 单类偏长**，同时承担心跳、免打扰判定、通知发送、Widget 更新。
   下一步应拆出 `ReminderScheduler`（决定「该不该提醒」）与 `TimerService`（只管生命周期）。
2. **`SettingsScreen.kt` 超过 1000 行**，各类 Picker 弹窗都堆在一个文件里，应按弹窗拆分。
3. **无障碍只覆盖了主页关键控件**（计时圆环、权限横幅），
   统计页图表与设置页开关行尚未做语义合并，TalkBack 体验仍然割裂。
4. **英文文案为直译**，未经母语者审校；日期/数字格式也未按 locale 适配。

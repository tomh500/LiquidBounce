# Vape 兼容模块交接记录

本文记录在 `需要合并的/VapeV4.21` 中阅读到的 Vape 4.21 Java 实现，以及移植到 LiquidBounce 时必须保持的行为边界。这里的“Vape 模式”默认指 Vape 逻辑；LiquidBounce 原有模式仍必须保持默认和独立。

## 参考源码

- KillAura（Vape Blatant）：`需要合并的/VapeV4.21/src/main/java/gg/vape/module/blatant/KillAura.java`
- SilentAura：`需要合并的/VapeV4.21/src/main/java/gg/vape/module/combat/SilentAura.java`
- Scaffold：`需要合并的/VapeV4.21/src/main/java/gg/vape/module/blatant/Scaffold.java`
- TellyBridge：`需要合并的/VapeV4.21/src/main/java/gg/vape/module/blatant/scaffold/TellyBridgeScaffoldMode.java`
- Scaffold 点转头：`需要合并的/VapeV4.21/src/main/java/gg/vape/module/blatant/scaffold/ScaffoldPointRotationController.java`
- 移动任务：`需要合并的/VapeV4.21/src/main/java/gg/vape/movement/PlayerMovementTask.java`、`PlayerMovementTaskManager.java`
- Clutch 对应的原始 UI 模块是 `blatant/BlockIn.java`；不要误用 `utility/Clutch.java`，后者是 Vape 的 Block-In UI 模块。

## 总体边界

1. Vape 与 LiquidBounce 是并列实现，不要把 Vape 参数混入 LB 执行树。
2. Vape 模式隐藏并不执行 LB-only 参数；LB 模式隐藏并不执行 Vape-only 参数。
3. 用户要求的兼容参数可以做并集，例如目标选择、AntiBot、Requires、KeepSprint、AllowSprint、ForceSprint，但这些参数必须只在实际适用的模式执行。
4. 模式切换必须清理旧目标、计时器、旋转目标、热键栏占用、移动任务和放置队列。
5. 优先复制 Vape Java 的算法和状态机；只有 Minecraft 新版本 API 不同的地方才通过 LB 适配层转换，不要用 KT 重新发明一套近似逻辑。

## 共享 Vape 旋转

Vape-compatible 模块应通过 `GlobalVapeRotationSettings.rotationTarget(...)` 提交旋转目标，并使用 `vapeCompatible = true` 的目标和 `VapeAdaptiveRotationProcessor`。共享设置包括 `Movement`、`3rdPersonAimView`、`AimIndicator`、`UseReach`、`UseHitboxes`。

不要让 LB 旋转目标污染 Vape 的第三人称模型旋转缓存。Vape 旋转结束、模式切换或模块禁用时必须释放目标并清理积分器/鼠标旋转状态。

## KillAura

- `Vape` 基于 Vape Blatant KillAura；`Silent` 基于 `SilentAura.java`。
- Silent 的核心是 PID/自适应转向、抖动、瞄准点选择、攻击延迟、目标排序和 ray readiness；不能替换成普通 LB KillAura 的逐 tick 近似。
- `Requires`、`KeepSprint`、`Criticals` 等兼容设置可共享，但 LB 的攻击、旋转、raycast、critical、auto-block 参数树仍只属于 LB 模式。
- Vape 的候选目标保留到 `SwingRange`；超过 `AttackRange` 的目标仍可走空挥/动画路径，只有在攻击范围内才真正攻击。
- 当前移植使用 LB 的目标追踪器来保留 AntiBot/目标选择兼容性，因此不是字节级 Vape 源码复制；这是有意的兼容层差异。

## Scaffold / TellyBridge

- Scaffold Vape 子模式只有 `GodBridge` 和 `TellyBridge`。Legit 属于 Eagle/普通 Scaffold，不要塞回 Vape Telly。
- Telly 的核心不是“每 tick 右键 + 跳跃”的简单近似，而是：手动放块激活、确认世界方块、bridge path、落地周期、垂直上升周期、TargetPositionMovementTask、点转头和射线面检查。
- Vape 的手动激活必须观察真实放置请求，并等待对应世界方块出现；失败的右键不能增加 `ActivationBlocks`。
- Telly 进入自动桥接后，移动任务负责前进方向；在到达路径边缘的落地 tick 必须提交跳跃输入。不要依赖同一 tick 尚未更新的 `player.isSprinting` 判断起跳，否则会直接走出平台下坠。
- Vape 的点旋转控制器每次更新都会重新计算指向固定点的旋转，但点 yaw 只有在 `RotationUtil.p(facing, placementPosition)` 条件满足时才更新。新版实现可用等价的“前一 tick 位置已越过放置块”判断。
- Vape 的旋转速度在点控制器初始化/允许更新时取一次；不要每 tick 按当前角度重新计算速度，否则转向曲线与 Vape 不同。
- Java 中 `waitForGroundAfterArrival` 在当前 Telly 路径的到达分支会立即完成，不能仅凭该字段推断任务必须等落地。
- Telly 的放置面：普通路径要求水平侧面；`bridgeLevel != 0 && path.size == 4` 的抬高路径要求 `UP`。
- `AllowSprint=false` 应禁用冲刺，`ForceSprint=true` 应强制冲刺；这两个设置不应破坏 Telly 的跳跃触发。

## Clutch

- Clutch 是自救/落地放块模块，使用当前版本 `FallingPlayer`、预测撞击帧优先的目标选择、`BlockPlacer` 支持路径、可见换槽与换回，以及 Vape 旋转设置。
- Vape 旋转速度根据预测剩余下落 tick 计算；不要改成固定速度的多位置队列，否则转头太晚，容易错过落点。
- 需要同时保留冲击点目标和脚下即时 fallback，避免预测目标暂时为空导致完全不放块。
- 禁用 block limit 时不要隐式限制为一个目标；限制应由配置本身决定。

## 验证与维护

Windows 下 Gradle/Node 必须从 CMD 执行：

```text
cmd /c gradlew compileKotlin compileJava
cmd /c gradlew test --tests net.ccbluex.liquidbounce.config.types.ValueVisibilityTest --no-parallel
cd src-theme && cmd /c npm run check
cmd /c gradlew runClient
```

客户端验证后检查 `git status --short` 和 `git diff --check`。不要为了验证而停止用户正在测试的客户端。日志中应确认客户端完成初始化，并检查是否有编辑模块引起的异常。

## 可信度说明

本文是源码阅读和当前移植过程的交接摘要，不声称所有行为已经在真实服务器上达到 100% 字节级一致。涉及新版 Minecraft API、LB 事件顺序、旋转处理器和目标追踪器的地方，必须以当前代码和实际测试为准；修改前先重新阅读上面的 Vape Java 原文件。

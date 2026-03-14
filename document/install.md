# install.md

## 1. 当前是否有“模拟基站定位”功能

结论：有“部分能力”，但不是完整的基站仿真系统。

- README 里 `mock the cell info` 仍是未完成状态。
- 代码里已经有 Telephony 相关 Hook：
  - `xposed/src/main/java/moe/fuqiuluo/xposed/hooks/telephony/TelephonyHook.kt`
  - 能拦截/改写 `getAllCellInfo`、`getCellLocation`、`getNeighboringCellInfo`、部分网络类型返回值。
- 默认通过“网络降级为 CDMA”路径工作（`needDowngradeToCdma`），属于兼容性方案，不是完整 LTE/NR 参数级模拟。

## 2. 如何添加“完整基站定位模拟”

建议按下面 4 步扩展：

1. 新增配置模型
- 在 `xposed` 侧增加 `FakeCellConfig`（建议字段）：
  - `mcc`、`mnc`
  - 4G: `tac`、`eci`、`pci`、`earfcn`
  - 5G: `nci`、`nrarfcn`、`pci`
  - 信号: `rsrp`、`rsrq`、`sinr`
  - 邻区列表（可选）

2. 扩展远程命令通道
- 在 `RemoteCommandHandler.kt` 增加：
  - `set_cell_config`
  - `get_cell_config`
  - `set_cell_mock_enabled`
- 在 `MockServiceHelper.kt` 增加对应调用。

3. 扩展 Hook 点
- `TelephonyHook.kt` 当前以 CDMA 为主，需补充：
  - `CellInfoLte` / `CellIdentityLte`
  - `CellInfoNr` / `CellIdentityNr`
  - `CellSignalStrengthLte` / `CellSignalStrengthNr`
- 同时覆盖监听回调（`TelephonyRegistry`）返回，保证 App 主动监听和被动查询一致。

4. App 设置页增加 UI
- 参考已有 `cdmaSwitch`，增加“蜂窝模拟”开关和参数输入项。
- 保存到 `Perfs.kt`，并在 `put_config` 时同步到 `xposed` 进程。

## 3. 你需要提供哪些信息

要把“基站定位模拟”做完整、可用，请提供：

1. 目标环境
- Android 版本、ROM（AOSP/MIUI/ColorOS/OneUI）、是否双卡。

2. 目标网络制式
- 只做 4G，还是 4G+5G 都要。

3. 需要模拟的关键参数
- 最少：`mcc/mnc` + 主小区标识（4G: `tac/eci/pci`；5G: `nci/pci`）
- 建议：再给 2~6 个邻区参数。

4. 模拟策略
- 固定不变，还是按轨迹动态切换小区。

5. 验证目标
- 你要通过哪些 App/SDK 验证（例如仅看 `getAllCellInfo`，还是也看监听回调）。

## 4. 这个 App 怎么构建

项目根目录下真正工程在 `Portal/`。

### 4.1 环境要求

- JDK 17
- Android SDK Compile 35
- NDK `26.1.10909125`
- CMake `3.22.1`
- 可联网下载 Gradle 与依赖

说明：本项目 `xposed` 模块包含 native 编译（CMake + NDK），缺少 NDK 会失败。

### 4.2 命令行构建

在仓库根目录执行：

```powershell
cd .\Portal
.\gradlew.bat clean :app:assembleAppDebug
```

可选产物：

```powershell
# arm64
.\gradlew.bat :app:assembleArm64Debug

# x86_64
.\gradlew.bat :app:assembleX64Debug

# release
.\gradlew.bat :app:assembleAppRelease
```

APK 通常在：

- `Portal/app/build/outputs/apk/<flavor>/<buildType>/`

文件名会被重命名为：

- `Portal-v<version>-<abi>.apk`

### 4.3 运行前提（功能生效）

- 设备需支持并启用 LSPosed/Xposed。
- 模块作用域至少包含：
  - `android`
  - `com.android.phone`
  - `com.android.location.fused`（以及机型相关 fused 进程）

否则只安装 APK 不会生效系统级 Hook。

## 5. 本地状态说明

我尝试执行 `gradlew tasks` 时遇到本机 Gradle wrapper 分发包文件锁超时（`~/.gradle/wrapper/.../gradle-8.9-bin.zip`），所以这里给的是基于项目配置的可执行构建步骤。

## 6. 本次已按你的参数落地的默认行为

- 目标环境：
  - Android 13-15 / MIUI / 双卡场景已按系统进程 `android` + `com.android.phone` 的 Hook 路径实现。
- 网络制式：
  - 已支持 4G + 5G（优先 5G，可配置）。
- 基站参数来源：
  - 已接入 OpenCelliD `getInArea` 接口，默认 token 为你提供的
    `pk.43c7b71717439aeed2e00c7ff0a4d27f`。
- 策略：
  - 当前默认固定基站参数。
  - 在“开始模拟”时，会按当前目标点（单点或路线起点）拉取并下发小区参数。
  - 摇杆移动暂未做实时重新拉取（可作为下一步优化，增加节流与缓存）。

关键改动文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/cell/OpenCellIdClient.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/hooks/telephony/TelephonyHook.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/RemoteCommandHandler.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/utils/CellMockConfig.kt`

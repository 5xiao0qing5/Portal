# 基站模拟可行性报告

日期：2026-03-14

## 结论摘要

1. 当前 App 不是“只有 4G”，也不是“完整 5G SA 模拟”。
2. 当前实现是 **LTE 主小区必有 + NR 小区可选叠加** 的混合模式，更接近 **4G Anchor + 5G NR 辅助信息**。
3. 如果只是把现有 5G 模拟做得更像样，可行，复杂度 **中等**。
4. 如果目标是做成更完整、更像系统真实 5G 注册态的模拟，复杂度 **中高**。
5. 如果说的 “Unwired Labs 来源” 指的是 **Unwired Labs LocationAPI (`/v2/process`)**，它**不适合直接替换当前 `经纬度 -> 小区参数` 的链路**，因为它的接口方向是 **`小区/WiFi -> 经纬度`**，不是反向查询。
6. 如果说的 “Unwired Labs 来源” 指的是 **OpenCelliD/Unwired 的数据下载或商业数据授权**，那是可行的，但更适合做成 **后端服务**，不适合直接在 Android 端塞大库做反查。

## 一、当前实现到底是 4G 还是 5G

### 1. 配置层

当前基站配置结构同时包含 LTE 和 NR 字段：

- `lteTac`
- `lteEci`
- `ltePci`
- `lteEarfcn`
- `nrNci`
- `nrPci`
- `nrArfcn`
- `preferNr`

代码位置：

- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/utils/CellMockConfig.kt`
- 关键定义行：`5-16`

### 2. 数据拉取层

当前单点/路线页最终都是走：

- `MockServiceHelper.refreshCellConfigByOpenCellId(...)`
- 再调用 `OpenCellIdClient.queryBestConfig(...)`

代码位置：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt:400`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/cell/OpenCellIdClient.kt:36`

### 3. OpenCellID 选型逻辑

当前逻辑会在候选小区里分别找：

- 最近的 LTE
- 最近的 NR

然后：

- 如果 `preferNr = true`，优先用 NR 作为 anchor
- 但最终配置里仍然同时写入 LTE 字段和 NR 字段

关键代码：

- `bestLte / bestNr`：`OpenCellIdClient.kt:112-138`
- 组装配置：`OpenCellIdClient.kt:140-151`

### 4. 注入层真实行为

Telephony Hook 在构造返回值时：

- 总是先构造一个 `CellInfoLte`
- 如果 `nrNci > 0`，再额外构造一个 `CellInfoNr`

关键代码：

- `buildMockCellInfoList()`：`TelephonyHook.kt:353-377`
- `buildLteCellInfo()`：`TelephonyHook.kt:383-405`
- `buildNrCellInfo()`：`TelephonyHook.kt:408-439`

### 5. 为什么我判断它不是完整 5G SA

因为当前代码里：

- LTE 小区被标记为 `registered = true`
- NR 小区被标记为 `registered = false`
- NR 的 `TAC` 还直接复用了 `lteTac`
- `getDataNetworkType()` 虽然会在 `preferNr && nrNci > 0` 时返回 `20`（NR），但注册态和系统其它 telephony 面仍没有一起补齐

关键代码：

- `getDataNetworkType`：`TelephonyHook.kt:167-180`
- LTE 注册态：`TelephonyHook.kt:402-404`
- NR 注册态：`TelephonyHook.kt:436-438`
- NR 复用 LTE TAC：`TelephonyHook.kt:420-423`

### 6. 当前能力的准确描述

更准确地说，当前实现是：

- **支持 4G 参数模拟**
- **支持 5G NR 参数注入**
- **对外会伪装成 NR 网络类型**（条件满足时）
- 但 **仍偏向 LTE 主注册 + NR 附加信息** 的形态
- **不是完整的 5G SA 全栈模拟**

## 二、如果增强 5G 基站模拟，复杂吗

### 1. 如果只是“把现在的 5G 做得更像”

目标：

- 保留当前 `LTE + NR` 双注入
- 增加更多 5G 相关字段的一致性
- 让更多 App/SDK 更稳定地判断为 NR

复杂度评估：**中等**

原因：

1. 当前数据结构已经有 `nrNci/nrPci/nrArfcn`，基础不差。
2. `CellInfoNr` 已经能构出来，不是从零开始。
3. 主要缺的是 **更多 telephony 面的协同伪装**，而不是单纯再加几个字段。

建议补的点：

1. `ServiceState / NetworkRegistrationInfo` 相关返回值
2. `SignalStrength` / `CellSignalStrengthNr` 与 `CellSignalStrengthLte` 的一致性
3. `TelephonyDisplayInfo` / `override network type`（如果目标 App 会看这些）
4. `PhysicalChannelConfig`（部分 ROM / SDK 会看）
5. 邻区列表（当前基本只有主 LTE + 可选 NR，没有 2~6 个邻区）
6. SA / NSA 行为开关

### 2. 如果目标是“完整 5G SA”

复杂度评估：**中高**

原因：

1. 不只是 `CellInfoNr`。
2. 还要统一多处 framework / registry / callback / cached state。
3. 不同 Android 版本、不同 ROM（你这里是 MIUI 13-15）差异会明显放大维护成本。
4. 一旦做不好，会出现：
   - `getAllCellInfo()` 像 5G
   - 但 `ServiceState` 像 4G
   - 或网络类型像 5G，回调却像 LTE
   - App 很容易看穿。

### 3. 工作量拆分

#### 方案 A：轻量增强 5G

内容：

- 保留现有 `OpenCellID -> CellMockConfig`
- 增补更多 NR 相关 hook
- 加邻区模拟
- 加 SA/NSA 模式开关

评估：

- 可行性：高
- 复杂度：中等
- 风险：中等
- 更适合当前项目

#### 方案 B：完整 5G SA/NSA 框架化模拟

内容：

- 重构 cell state model
- 把 `CellInfo`、`ServiceState`、`NetworkRegistrationInfo`、`SignalStrength`、`DisplayInfo` 等统一从一个状态源生成

评估：

- 可行性：中
- 复杂度：中高
- 风险：高
- 维护成本：高

## 三、Unwired Labs 来源到底复杂不复杂

### 先澄清一个容易混淆的点

你现在已经在用的 **OpenCelliD**，本身就是 **Unwired Labs 维护** 的项目体系之一，而不是完全无关的第三方。

参考：

- https://help.unwiredlabs.com/support/solutions/articles/36000333519-how-is-opencellid-related-to-unwired-
- https://download.unwiredlabs.com/

### 1. 如果你说的是 Unwired Labs `LocationAPI / v2/process`

这个方向 **不适合直接接入成当前“基站来源”**。

原因很直接：

当前 App 需要的是：

- 输入：`lat/lon`
- 输出：`mcc/mnc + LTE/NR 小区参数`

而 Unwired Labs `v2/process` 文档定义的是：

- 输入：`cells[] / wifi[] / mcc / mnc / radio`
- 输出：`lat / lon / accuracy / address`

也就是说它是：

- **小区/WiFi -> 位置**

而不是：

- **位置 -> 小区**

参考：

- https://docs.unwiredlabs.com/

文档里 Geolocation API 的返回结构重点是：

- `lat`
- `lon`
- `accuracy`
- `address`

我没有在官方文档里看到一个适合作为当前链路替代品的官方反查接口，即：

- 输入 `lat/lon`
- 返回附近 serving cell / NR / LTE 参数集合

所以：

- **直接把 Unwired LocationAPI 接到当前 `queryBestConfig(lat, lon)` 位置上，基本不成立。**

### 2. 如果你说的是“用 Unwired / OpenCelliD 的下载数据做自建来源”

这个方向 **可行**，但复杂度比现在高不少。

可行方式有两种：

#### 方案 1：端上离线库

思路：

- 下载国家/区域 CSV
- 导入本地 SQLite / Room / 自建索引
- 在手机端按 `lat/lon` 做空间邻近搜索
- 取最近 LTE / NR 生成 `CellMockConfig`

优点：

- 不依赖实时网络
- 命中速度快
- 可做自己的排序策略

缺点：

- 数据文件大
- 需要做空间索引
- APK/数据包膨胀明显
- 更新机制麻烦
- 对手机端 IO / 存储不友好

结论：

- **能做，但不优雅，不推荐作为首选。**

#### 方案 2：你自己的后端反查服务

思路：

- 后端持有 OpenCelliD/商业数据
- 后端实现 `lat/lon -> best LTE/NR/邻区`
- Android 端只请求你的后端

优点：

- 端上代码最干净
- 可灵活切换数据源
- 后端更适合做空间查询、缓存、去重、配额控制
- 后面想接 OpenCellID、私有库、别的商业库都容易

缺点：

- 需要服务端
- 需要部署和运维
- 需要考虑 token / 版权 / 速率 / 缓存

结论：

- **这是最工程化、最推荐的做法。**

### 3. Unwired Labs 是否支持 5G 字段

官方文档的 Geolocation API 在 `cells` 里已经列出了：

- LTE / 4G
- NB-IoT
- New Radio - NR, 5G (Public BETA)

这说明：

- **它能接受 5G NR 小区标识做定位输入**
- 但这不等于它提供了现成的 **按经纬度反查 5G 小区** 接口

所以从你的项目角度看：

- **它能做“定位引擎”**
- **不天然是“反查基站参数源”**

## 四、可行性评级

### 当前能力评级

- 4G 模拟：高
- 5G NR 基础注入：中高
- 完整 5G SA 一致性：低到中
- 邻区模拟：低

### 新需求评级

#### 需求 1：把当前 5G 做强一点

- 可行性：高
- 复杂度：中等
- 推荐程度：高

#### 需求 2：做完整 5G SA/NSA 统一态

- 可行性：中
- 复杂度：中高
- 推荐程度：中

#### 需求 3：直接接 Unwired Labs `v2/process` 作为当前基站源

- 可行性：低
- 复杂度：高
- 推荐程度：低

#### 需求 4：用 Unwired/OpenCelliD 下载数据或商业数据，自建反查源

- 可行性：高
- 复杂度：中高
- 推荐程度：高

## 五、建议路线

### 最推荐路线

1. 保留现在的 `OpenCellID -> CellMockConfig` 主链路。
2. 把现有 5G 从“能注入”升级到“更像系统真实 5G”。
3. 抽象一个统一的数据源接口，例如：
   - `CellSource.queryBestConfig(lat, lon, preferNr)`
4. 先保留 `OpenCellIdSource`。
5. 后面如果你真要接新来源，不要直连 `Unwired process API`，而是：
   - 接你自己的后端 `BackendCellSource`
6. 邻区支持单独做第二阶段。

### 不推荐路线

1. 直接把 Unwired Labs `v2/process` 当成 `lat/lon -> cell config` 的替代源。
2. 直接在 Android 端塞大体积国家级 CSV 再做重空间查询。

## 六、如果现在就要开工，建议分三期

### 第一期：当前链路增强

- 增加 5G 相关日志
- 增加 SA/NSA 开关
- 增加邻区模型（至少 2~6 个）
- 增加更多 telephony hook 面

### 第二期：数据源抽象

- 抽 `CellSource`
- 当前 OpenCellID 实现迁移进去
- 缓存统一下沉到 source 层

### 第三期：新来源接入

- 走你自己的后端
- 后端接入 OpenCellID 下载数据 / 其它商业数据
- Android 端只接标准化结果

## 七、对你当前项目的最终判断

### 当前状态

- 已有 4G + NR 混合基站模拟能力
- 不是纯 4G
- 也不是完整 5G SA

### 5G 增强

- **能加，不算从零开始**
- 复杂度 **中等**
- 值得做

### Unwired Labs 来源

- **如果指 `v2/process`：不建议直接接，接口方向不匹配**
- **如果指下载数据/商业数据 + 自建反查：可行，但建议走后端**

## 参考资料

### 本地代码

- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/cell/OpenCellIdClient.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/hooks/telephony/TelephonyHook.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/utils/CellMockConfig.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ext/PortalPrefs.kt`

### 官方资料

- Unwired Labs LocationAPI 文档：https://docs.unwiredlabs.com/
- OpenCelliD 与 Unwired 的关系：https://help.unwiredlabs.com/support/solutions/articles/36000333519-how-is-opencellid-related-to-unwired-
- OpenCelliD 下载页：https://download.unwiredlabs.com/

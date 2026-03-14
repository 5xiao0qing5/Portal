# Portal 项目功能流程报告

> 生成时间：2026-03-06  
> 基于当前工作区代码静态梳理，重点关注 `Portal` Android App、`xposed` 模块、配置与运行时交互链路。

## 1. 项目定位

Portal 是一个基于 **LSPosed / Xposed** 的虚拟定位工具，整体由 Android 前台控制 App + Xposed Hook 模块组成。

- App 负责：地图选点、路线绘制、历史点位/路线管理、参数配置、发起模拟指令。
- Xposed 模块负责：Hook 系统定位/传感器/基站/WLAN/GNSS 等链路，在系统或目标进程中注入虚拟定位数据。
- App 与 Xposed 模块之间通过 `LocationManager.sendExtraCommand()` 建立控制通道。

## 2. 仓库结构

根工程位于 `Portal/`，主要模块如下：

- `Portal/app`
  - 前台 Android 应用
  - 百度地图展示、位置搜索、路线编辑、参数配置、发起 mock 指令
- `Portal/xposed`
  - LSPosed/Xposed 模块
  - Hook Android 系统定位服务、GNSS、传感器、基站、WLAN 等
- `Portal/system-api`
  - 一些系统 API 兼容封装
- `Portal/nmea`
  - NMEA 相关解析能力

## 3. 关键入口

### 3.1 App 入口

- `Portal/app/src/main/java/moe/fuqiuluo/portal/Portal.kt`
  - 初始化百度地图 SDK
  - 调用隐私合规接口：`SDKInitializer.setAgreePrivacy()`、`LocationClient.setAgreePrivacy()`
  - 初始化 Bugly
  - 设置地图默认坐标系为 `GCJ02`

- `Portal/app/src/main/java/moe/fuqiuluo/portal/MainActivity.kt`
  - 请求权限
  - 初始化通知
  - 绑定 `NavigationDrawer + Navigation` 路由
  - 初始化全局 GeoCoder / SuggestionSearch 搜索链路
  - 初始化 `MockServiceViewModel` 中的悬浮摇杆协程

### 3.2 Xposed 入口

- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/FakeLocation.kt`
  - Xposed 主入口，实现 `IXposedHookLoadPackage`
  - 针对不同进程安装不同 Hook：
    - `android`
    - `com.android.phone`
    - `com.android.location.fused`
    - `com.xiaomi.location.fused`
    - `com.oplus.location`
    - `com.tencent.mm`

## 4. 页面与导航结构

导航定义在 `Portal/app/src/main/res/navigation/mobile_navigation.xml`。

主要页面：

- `HomeFragment`：地图主页、点选位置、搜索位置、查看详情、添加历史位置
- `MockFragment`：单点模拟、悬浮摇杆控制、历史点位管理
- `GnssMockFragment`：GNSS 模拟开关与卫星状态显示
- `RouteMockFragment`：路线选择、路线模拟、自动行进、路线列表管理
- `RouteEditFragment`：路线编辑、手动加点、自动连线、联网搜索地点、保存路线
- `SettingsFragment`：速度/海拔/精度/上报频率/Hook 行为/基站模拟等配置

## 5. 启动到可用的主流程

### 5.1 App 启动流程

1. `Portal.kt` 初始化百度 SDK、隐私接口、Bugly。
2. `MainActivity.onCreate()` 设置窗口样式与状态栏。
3. 请求运行时权限：定位、网络、Wi‑Fi、电话状态、前台服务等。
4. 若权限通过：将系统 `LocationManager` 注入 `MockServiceViewModel.locationManager`。
5. `locationManager` 赋值时触发 `MockServiceHelper.tryInitService()`。
6. App 向 provider 名称为 `portal` 的系统扩展命令通道发送 `exchange_key`。
7. 若 Xposed 模块已工作，则返回随机 `key`，后续所有命令都用它鉴权。
8. 初始化通知、导航抽屉、GeoCoder、全局搜索。
9. 初始化悬浮摇杆与路线模拟协程，但默认处于暂停态。

### 5.2 模块联通判定

App 侧是否能真正控制系统定位，关键依赖两点：

- Xposed 模块已正确注入目标系统进程
- `MockServiceHelper.tryInitService()` 成功拿到随机密钥

若失败，前台通常会表现为：

- “系统服务注入失败”
- `isServiceInit()` 返回 `false`
- 各类 `sendExtraCommand()` 操作无效

## 6. 地图与搜索功能流程

### 6.1 地图初始化

涉及文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/home/HomeFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/RouteEditFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/viewmodel/BaiduMapViewModel.kt`

流程：

1. Fragment 创建 `MapView` 并取出 `BaiduMap`。
2. 设置缩放、比例尺、指南针、地图类型、定位图层等。
3. 启动 `LocationClient` 持续获取当前位置。
4. 定位回调把当前位置写入 `BaiduMapViewModel.currentLocation`。
5. 地图默认优先自动居中到当前位置；若首次进入时尚未拿到定位，则在首个定位回调后自动居中一次。

### 6.2 地图点选流程

在 `HomeFragment` 中：

1. 单击地图：
   - 取点击点（百度地图返回 GCJ-02）
   - 转成 WGS84 写入 `markedLoc`
   - 异步触发逆地理编码
   - 地图上打点显示
2. 长按地图：
   - 与单击类似，但会切换 `showDetailView = true`
   - 在地图上显示坐标+地址详情窗口

### 6.3 全局地点搜索流程

在 `MainActivity.onCreateOptionsMenu()` 中：

1. 首页工具栏启用 `SearchView`
2. 用户输入关键词
3. 至少 2 个字符后，走 300ms debounce
4. 调用百度 `SuggestionSearch.requestSuggestion()`
5. 请求参数包含：
   - 关键词
   - 当前城市 `mCityString`
   - `citylimit(false)`
   - 当前定位附近的 `location()` 作为提示
6. 搜索结果经 `SuggestionResult.toPoi()` 转成内部 `Poi`
7. 点击搜索结果后：
   - 写入 `markName`
   - 写入 `markedLoc`
   - 地图移动到该点
   - 调用 `markMap()` 打点并显示详情

### 6.4 坐标转换规则

涉及文件：`Portal/app/src/main/java/moe/fuqiuluo/portal/ext/Loc.kt`

- 地图展示/百度接口使用 `GCJ-02`
- App 内部存储与模拟逻辑多以 `WGS84` 处理
- 主要扩展：
  - `LatLng.wgs84`
  - `BDLocation.wgs84`
  - `Pair<Double, Double>.gcj02`

## 7. 历史位置功能流程

### 7.1 数据结构

- `HistoricalLocation`
  - 字段：`name` / `address` / `lat` / `lon`
  - 持久化为 CSV 风格字符串，支持带引号的逗号字段

### 7.2 存储方式

位置与选择状态通过 `SharedPreferences(portal)` 保存，封装在：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ext/Perfs.kt`

主要键：

- `locations`：历史位置集合
- `selectedLocation`：当前选中位置

### 7.3 添加位置流程

在 `HomeFragment`：

1. 用户点地图或长按地图得到 `markedLoc`
2. 点击“添加位置”按钮
3. 弹窗中可编辑名称、地址、经纬度
4. 校验通过后写入 `rawHistoricalLocations`
5. 后续 `MockFragment` 可直接读取并选择该位置进行单点模拟

## 8. 单点模拟功能流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/MockFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/viewmodel/MockServiceViewModel.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`

### 8.1 用户流程

1. 在地图主页选点或从历史位置中选择一个点
2. 进入 `MockFragment`
3. 点击“开始模拟”
4. App 校验：
   - 悬浮窗权限
   - 已选择位置
   - `LocationManager` 已初始化
   - `MockServiceHelper.isServiceInit()` 为真
5. 调用 `MockServiceHelper.tryOpenMock()` 开启系统侧 mock
6. 调用 `MockServiceHelper.setLocation()` 把系统虚拟位置设置到目标点
7. 若启用基站模拟，则通过 `OpenCellIdClient` 拉取附近基站并推送 `set_cell_config`
8. 调用 `broadcastLocation()` 通知监听者刷新定位

### 8.2 停止流程

1. 点击“停止模拟”
2. 调用 `MockServiceHelper.tryCloseMock()`
3. 关闭后同步清理：
   - 路线模拟状态
   - 自动播放状态
   - 悬浮摇杆显示状态
   - 摇杆协程暂停

## 9. 悬浮摇杆移动流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/Rocker.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/android/widget/RockerView.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/viewmodel/MockServiceViewModel.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/android/coro/CoroutineController.kt`

流程：

1. 单点模拟页或路线模拟页打开摇杆
2. `Rocker.show()` 创建/显示悬浮窗
3. 拖动摇杆时回调角度 `onAngle()`
4. App 通过 `MockServiceHelper.setBearing()` 更新系统侧朝向
5. `MockServiceViewModel` 内部常驻协程按 `reportDuration` 周期运行
6. 每一帧按 `speed * delay / 1000 / compensation` 计算步长
7. 调用 `MockServiceHelper.move()` 让虚拟位置朝当前 bearing 前进
8. 松手后若未锁定，协程暂停

说明：

- 当前实现里摇杆与路线自动模拟共享部分状态，需要由 `resetRouteMockState()` 做隔离与收敛。
- 该项目近期已对 `pause()/resume()` 的状态滞留问题做过修复。

## 10. 路线编辑功能流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/RouteEditFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/HistoricalRoute.kt`

### 10.1 进入页面

1. 初始化地图与定位
2. 默认居中到当前定位附近
3. 提供地图类型、我的位置、搜索地点等操作

### 10.2 绘制路线

当前交互已改为“**手动添加点，自动连线**”：

1. 点击开始绘制
2. 地图进入 `isDrawing = true`
3. 每次点击地图：
   - 将点击点转成 WGS84
   - 追加到 `mPoints`
   - 自动重绘折线
4. 每个点会绘制为带序号的 marker：`1 / 2 / 3 ...`
5. 支持撤回最后一个点

### 10.3 联网搜索地点并加点

1. 点击搜索按钮弹出搜索对话框
2. 使用百度 `SuggestionSearch`
3. 至少输入 2 个字符才发请求
4. 结果点击后：
   - 地图移动到结果位置
   - 若当前正在绘制，则自动把该点加入路线
   - 若未绘制，则仅作为标点查看

### 10.4 保存路线

1. 点击完成绘制
2. 校验点数量不少于 2
3. 打开保存弹窗，默认把 `mPoints` 以 JSON 显示在文本框中
4. 用户填写路线名称
5. 校验每个点经纬度合法
6. 将路线保存到 `jsonHistoricalRoutes`

## 11. 路线模拟功能流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/mock/RouteMockFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/viewmodel/MockServiceViewModel.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/android/coro/CoroutineRouteMock.kt`

### 11.1 路线选择与管理

1. `RouteMockFragment` 从 `jsonHistoricalRoutes` 读取历史路线
2. 若为空，自动创建一条默认测试路线
3. 用户点击路线后：
   - 保存为 `selectedRoute`
   - 写入 `selectRoute`
   - 重置路线播放状态
   - 如果模拟服务已启动，则直接把位置切到该路线起点
4. 支持滑动删除路线

### 11.2 路线启动

1. 点击“开始模拟”
2. 校验：
   - 已选路线
   - 路线至少 2 个点
   - 悬浮窗权限
   - 系统服务已注入
3. 调用 `tryOpenMock()` 开启模拟
4. 把当前位置设置为路线起点
5. 拉取并同步该点附近的基站配置

### 11.3 自动行进逻辑

`MockServiceViewModel` 中有单独的路线协程：

1. 协程按 `reportDuration` 周期执行
2. 第 0 阶段时，将当前位置精确设为路线第一个点
3. 用 GeographicLib 计算：
   - 当前点到目标点距离 `s12`
   - 当前朝向 `azi1`
4. 若距离小于 1 米：进入下一阶段
5. 若距离小于本帧步长：直接跳到目标点并进入下一阶段
6. 否则按当前目标点方位角前进一个步长
7. 全部阶段完成后自动 `resetRouteMockState()`

### 11.4 当前实现特点

- 路线模拟与摇杆模拟均可驱动位置变化
- 路线播放结束后不会永久卡死，后续可重新开始
- `resetRouteMockState()` 同时负责重置自动播放按钮状态，降低跨页面残留状态问题

## 12. GNSS 模拟功能流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/ui/gnss/GnssMockFragment.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/hooks/gnss/GnssHook.kt`

流程：

1. 页面请求定位权限
2. 注册 `GnssStatus.Callback` 读取当前系统卫星状态
3. 实时显示：
   - 可见卫星数
   - 参与定位卫星数
   - 平均信号强度
   - GPS / GLONASS / 北斗统计
4. 点击开关时，调用：
   - `startGnssMock()`
   - `stopGnssMock()`
5. 配置项如 `enableAGPS` / `enableNMEA` / `enableRequestGeofence` / `enableGetFromLocation` 等会通过 `putConfig()` 下发给 Xposed 模块

## 13. 设置页流程

核心文件：`Portal/app/src/main/java/moe/fuqiuluo/portal/ui/settings/SettingsFragment.kt`

设置页负责前台参数持久化与远端同步。

主要配置项：

- 海拔 `altitude`
- 速度 `speed`
- 精度 `accuracy`
- 上报间隔 `reportDuration`
- 最小卫星数 `minSatelliteCount`
- Debug 日志
- 禁用 `getCurrentLocation`
- 禁用注册监听
- 禁用 fused provider
- Geofence / GetFromLocation / AGPS / NMEA
- 基站模拟开关
- 传感器 Hook 开关
- WLAN 扫描禁用开关
- 循环广播定位开关

同步流程：

1. 配置写入 `SharedPreferences`
2. 调用 `MockServiceHelper.putConfig()`
3. 通过 `sendExtraCommand(portal, key, bundle)` 下发到 Xposed 侧
4. Xposed 更新 `FakeLoc` 全局运行参数

## 14. 基站模拟流程

核心文件：

- `Portal/app/src/main/java/moe/fuqiuluo/portal/cell/OpenCellIdClient.kt`
- `Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`

流程：

1. 单点模拟或路线模拟启动后，若开启基站模拟：
2. 使用 OpenCellID HTTP 接口按当前位置 BBOX 搜索附近基站
3. 在 LTE / NR 候选中择优生成 `CellMockConfig`
4. App 调用 `set_cell_config`
5. Xposed 侧更新 `FakeLoc.cellConfig`
6. Telephony 相关 Hook 根据配置伪造蜂窝网络信息

依赖：

- 需要可用的 `openCellIdToken`
- 需要联网

## 15. App 与 Xposed 的通信协议

核心文件：

- App：`Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- Xposed：`Portal/xposed/src/main/java/moe/fuqiuluo/xposed/RemoteCommandHandler.kt`

### 15.1 建链

1. App 发送 `exchange_key`
2. Xposed 校验调用方合法性后返回随机 `key`
3. 后续所有命令都以该 `key` 为第二参数

### 15.2 常见命令

- `start` / `stop`
- `is_start`
- `get_location`
- `update_location`
- `move`
- `set_bearing`
- `put_config`
- `start_gnss_mock` / `stop_gnss_mock`
- `start_wifi_mock` / `stop_wifi_mock`
- `set_cell_config`
- `broadcast_location`
- `load_library`

### 15.3 Xposed 侧状态中心

Xposed 模块主要把状态收敛到 `FakeLoc`：

- 当前纬经度
- 高度
- 速度
- 精度
- bearing
- 各类 Hook 开关
- 基站模拟参数
- GNSS/NMEA/AGPS 配置

## 16. Xposed Hook 分层说明

以 `Portal/xposed/src/main/java/moe/fuqiuluo/xposed/FakeLocation.kt` 为总入口，主要 Hook 层包括：

- `LocationServiceHook`
  - 系统定位服务层，拦截/广播定位更新
- `LocationManagerHook`
  - `LocationManager` 层接口 Hook
- `BasicLocationHook`
  - 应用进程内的基础位置对象替换/修正
- `AndroidFusedLocationProviderHook`
  - FusedLocation 相关
- `ThirdPartyLocationHook`
  - 厂商/第三方 fused 兼容
- `TelephonyHook` / `MiuiTelephonyManagerHook`
  - 蜂窝网络信息模拟
- `SystemSensorManagerHook`
  - 传感器数据 Hook
- `WlanHook`
  - WLAN 扫描/相关信息 Hook
- `GnssHook` / `LocationNMEAHook`
  - GNSS 与 NMEA 数据链路 Hook

## 17. 运行时依赖与前提条件

项目要完整可用，通常需要以下条件：

- Root / 可工作的 LSPosed 环境
- Xposed 模块成功启用
- App 拥有定位、网络、Wi‑Fi、电话状态、前台服务等权限
- 已授权悬浮窗权限（摇杆功能）
- 百度地图 AK 可用，且包名 + SHA1 绑定正确
- 若启用基站模拟：OpenCellID Token 可用

## 18. 当前项目的数据持久化

主要通过 `SharedPreferences("portal")` 保存：

- 历史位置
- 历史路线
- 当前选中位置
- 当前选中路线
- 地图类型
- 摇杆位置
- 海拔、速度、精度、上报间隔
- 调试与 Hook 开关
- OpenCellID Token

## 19. 典型使用场景串联

### 场景 A：单点模拟

1. 打开首页地图
2. 自动定位到当前位置附近
3. 点选地图或搜索地点
4. 保存为历史位置或直接进入单点模拟页
5. 点击开始模拟
6. 系统侧虚拟位置切换到该点
7. 可继续通过悬浮摇杆微调移动

### 场景 B：路线模拟

1. 进入路线编辑页
2. 手动点击地图逐点加点，系统自动连线
3. 必要时联网搜索地点并把结果加入路线
4. 保存路线
5. 返回路线模拟页选择路线
6. 开启模拟并点击自动播放
7. 虚拟位置按路线逐段移动直到结束

### 场景 C：GNSS 配置联动

1. 进入设置页调整 NMEA / AGPS / Geofence 等开关
2. 配置通过 `put_config()` 同步到 Xposed
3. 进入 GNSS 页查看卫星状态或开启 GNSS 模拟

## 20. 结论

当前代码已经形成了一个较完整的“**地图选点/路线编辑 -> App 配置下发 -> Xposed 系统 Hook -> 虚拟定位输出**”闭环。

如果按职责拆分，最核心的主链是：

1. `Portal.kt` / `MainActivity`：初始化与入口
2. `HomeFragment` / `RouteEditFragment`：地图交互与数据采集
3. `MockFragment` / `RouteMockFragment` / `GnssMockFragment`：功能执行页
4. `MockServiceViewModel`：运行时状态与协程调度中心
5. `MockServiceHelper`：App -> Xposed 命令桥
6. `RemoteCommandHandler` + `FakeLoc`：Xposed 状态中心
7. 各类 Hook：真正把虚拟定位数据注入系统与目标 App

若后续继续维护，本项目最值得重点关注的区域是：

- App 与 Xposed 的命令协议稳定性
- 路线模拟与摇杆模拟的状态隔离
- 搜索/地图 SDK 的生命周期与异常处理
- 厂商 ROM 兼容性（Miui / Oplus / Fused Provider）
- 基站/GNSS/WLAN 模拟能力与真实系统行为的一致性

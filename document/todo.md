# Portal 项目遗留事项与实现状态

> 生成时间：2026-03-06  
> 说明：基于当前仓库静态代码梳理，重点回答“还留了哪些 TODO”和“禁止扫描 WiFi 列表 / 反定位拉回是否已经完美实现”。

## 1. 总结结论

当前项目主体功能已经能跑通，但**还不能算“完全收尾”**。从代码现状看，遗留项主要分成 3 类：

1. **明确写在代码里的实验性/待完善项**
2. **功能已实现，但兼容性和边界条件不足的项**
3. **项目里存在占位/半成品逻辑的项**

其中你特别问的两项结论先放前面：

- **“禁止扫描 WiFi 列表”**：**不是完美实现**，属于“有基础效果，但兼容性不完整”
- **“反定位拉回”**：**不是完美实现**，属于“简单粗暴但有一定效果的增强手段”

---

## 2. 重点结论：WiFi 与反定位拉回

## 2.1 禁止扫描 WiFi 列表：当前状态

### 已实现的部分

- 设置页可直接开关：`Portal/app/src/main/java/moe/fuqiuluo/portal/ui/settings/SettingsFragment.kt`
- App 侧能发送：
  - `start_wifi_mock`
  - `stop_wifi_mock`
  - `is_wifi_mock_start`
  - 文件：`Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- Xposed 侧有专门的 `WlanHook`：
  - 文件：`Portal/xposed/src/main/java/moe/fuqiuluo/xposed/hooks/wlan/WlanHook.kt`
- 现在主要会处理：
  - `getConnectionInfo`
  - `getScanResults`

### 它现在实际在做什么

当开关打开时：

- 对非系统调用方：
  - `getConnectionInfo` 返回伪造/空洞的 `WifiInfo`
  - `getScanResults` 尽量返回空列表
- 目的就是减少 App 用附近 WiFi 热点重新校准真实位置

### 为什么它还不算“完美实现”

#### 1. 作者自己就在代码里承认了高版本问题

`Portal/xposed/src/main/java/moe/fuqiuluo/xposed/hooks/wlan/WlanHook.kt` 里有明确注释：

- 高版本 Android / APEX 化后，上面的处理**可能无效**
- 应用仍可能通过网络 / AGPS 拉回正常位置
- 当前只是“针对一个普通版本进行一个修复”

这说明它本身就不是一个“所有 ROM / 所有版本都稳”的实现。

#### 2. 只拦了部分 WiFi 入口，不等于拦完整条定位融合链

当前重点处理的是：

- `getConnectionInfo`
- `getScanResults`

但现实里“WiFi 参与定位”不只这两个入口，尤其厂商 ROM、Google 组件、融合定位服务可能还有别的链路。

#### 3. 设置默认值还有明显可疑点

`Portal/app/src/main/java/moe/fuqiuluo/portal/ext/Perfs.kt` 中：

- `disableWifiScan` 的默认值竟然取的是 `FakeLoc.enableNMEA`

这大概率是个历史遗留/拷贝失误，不影响主逻辑必然失效，但说明这块实现还不够干净。

#### 4. 更像“降低拉回概率”，不是“彻底封死 WiFi 纠偏”

设置页文案本身也写得比较保守：

- `一定程度解决位置拉回（测试）`

这和代码现状是匹配的。

### 结论

“禁止扫描 WiFi 列表”现在应理解为：

- **可用的实验性增强项**
- **不是彻底、稳定、全 ROM 通杀的解决方案**

---

## 2.2 反定位拉回：当前状态

### 已实现的部分

- 设置页有单独开关：`Portal/app/src/main/java/moe/fuqiuluo/portal/ui/settings/SettingsFragment.kt`
- 状态持久化在：`Portal/app/src/main/java/moe/fuqiuluo/portal/ext/Perfs.kt`
- 模拟启动时会开启一个循环线程：`Portal/app/src/main/java/moe/fuqiuluo/portal/service/MockServiceHelper.kt`
- 线程按 `reportDuration` 周期不断执行：
  - `broadcastLocation(locationManager)`

### 它现在实际在做什么

本质上就是：

- 模拟开始后
- 如果打开了 `loopBroadcastLocation`
- 就每隔一段时间，主动再广播一次当前位置

对应底层命令：

- `broadcast_location`

Xposed 侧最后调用的是：

- `LocationServiceHook.callOnLocationChanged()`

也就是说，它的核心思路不是“堵死拉回来源”，而是：

- **不停把当前假位置重新推给监听器**

### 为什么它也不算“完美实现”

#### 1. 实现方式非常直接，偏“暴力保活”

它没有做来源判断，也没有做更细的策略控制，基本就是：

- 开线程
- 按固定间隔不停广播

这类方式有时有效，但不够优雅，也更容易带来副作用。

#### 2. 设置页已经明说有副作用

界面文案：

- `解决位置拉回问题 可能导致发热`

这和代码完全一致，因为它真的会持续循环调用。

#### 3. 只能作用于“已注册的定位监听者”

`broadcast_location` 最终是调用 `LocationServiceHook.callOnLocationChanged()`，也就是：

- 对现有监听器主动回调一次 `onLocationChanged`

它并不是万能补丁，不能保证所有定位消费路径都会被覆盖。

#### 4. 开关不是即时全流程生效

设置页代码里提示就是：

- `重启模拟生效`

这也说明它依赖模拟启动流程去拉起循环线程，不是完全热切换。

### 结论

“反定位拉回”现在应理解为：

- **一种简单有效的加固手段**
- **不是彻底修复定位拉回根因的方案**
- **更适合在顽固机型上当补丁用**

---

## 3. 当前仍值得关注的 TODO / 未完成点

下面这些不是空想，而是从代码现状能明确看出来仍有后续空间的内容。

## 3.1 传感器/步频模拟仍是“持续迭代中”

### 证据

- 文案仍写着：`模拟步频（待完善）`
  - 文件：`Portal/app/src/main/res/values/strings.xml`

### 现状

- 现在已经有基础版计步注入和调试页
- 但距离“稳定兼容所有计步类 App”仍有距离

### 后续建议

- 增加更保守/更激进两套模式
- 增加步长、步频、是否屏蔽真实计步事件的 UI 配置
- 继续验证微信、支付宝、MIUI 健康等不同来源的兼容性

---

## 3.2 OpenCellID / 基站模拟链路仍依赖外部数据质量

### 现状

- 当 token 为空会直接失败：
  - `OpenCellID token is empty`
- 拉到的 cell 为空也会失败：
  - `OpenCellID cells is empty`

### 说明

这不是代码错误，而是功能上天然依赖：

- 外部服务可用性
- 当前区域基站数据是否完整
- 网络是否可访问

### 后续建议

- 增加更清晰的 UI 提示
- 增加缓存最近一次成功的基站配置
- 增加“无数据时保留旧基站”的策略

---

## 3.3 悬浮窗自动控制区仍有预留式按钮

### 现状

根据前面梳理，悬浮窗里部分按钮更偏“状态/预留入口”，不是完全成熟的功能模块。

### 后续建议

- 继续裁剪无实际作用的按钮
- 或把它们真正接成：
  - 自动暂停/继续
  - 自动锁定策略
  - 更清晰的状态提示

---

## 3.4 Miui 专项 Hook 仍有留白

### 现状

- `MiuiTelephonyManagerHook.kt` 目前基本还是占位/注释状态

### 说明

这意味着：

- Miui 兼容虽然已有部分基础
- 但还没做到“专门深挖 Miui 全套链路”

### 后续建议

- 如果后续重点支持小米系，值得补专门的 Miui 健康/计步/定位兼容

---

## 3.5 备份规则里有 Android Studio 默认 TODO

### 现状

- `Portal/app/src/main/res/xml/data_extraction_rules.xml` 里还有默认注释 TODO

### 影响

- 对主功能没大影响
- 更偏工程清理项

---

## 4. 从“产品角度”看，当前最值得继续做的事项

如果按优先级排，我会建议后续优先做这几项：

### P1：完善定位拉回对抗能力

- 继续增强 WiFi / Fused / AGPS / Geofence / NetworkLocation 联动
- 把“禁止扫描 WiFi 列表”从实验性提升到更稳版本
- 把“反定位拉回”从纯循环广播升级成更智能的策略

### P1：继续做步频/计步兼容性

- 现在已有基础版
- 后续重点是稳定性、App 兼容性、不同 ROM 兼容性

### P2：优化基站模拟容错

- 拉取失败时保底策略
- 缓存与回退逻辑

### P2：清理历史遗留和默认值问题

- 修复 `disableWifiScan` 默认值引用异常
- 清理占位按钮、注释代码、默认模板 TODO

### P3：补更多调试页

- 当前已有 `Step Debug`
- 后续还可以加：
  - WiFi Mock 调试页
  - Cell Mock 调试页
  - 当前 Hook 生效页

---

## 5. 最终结论

### 你关心的两项是否“完美实现”

- **禁止扫描 WiFi 列表**：**否**
- **反定位拉回**：**否**

### 但它们是否“完全没用”

- **也不是**

更准确的评价是：

- `禁止扫描 WiFi 列表`：**有用，但兼容性有限**
- `反定位拉回`：**有用，但方式粗暴、代价较高**

### 现阶段最合适的理解方式

这两个开关都应该被视为：

- **实战补丁型功能**
- 而不是“已经彻底解决所有拉回问题的完美方案”


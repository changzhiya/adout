# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

项目严格遵循并优先使用superpower的skills

## Project Overview

AdOut - 一款纯本地运行的 Android 广告拦截应用，目标平台为 MagicOS（荣耀手机），兼容其他 Android 设备。

**核心功能**：
1. VPN DNS 拦截 - 过滤依赖系统 DNS 的广告请求
2. 无障碍服务跳过 - 自动跳过开屏广告 Activity

**目标用户**：家人朋友，需要简单易用，尽量少的配置步骤

## Build & Run

```bash
# 编译 Debug APK
cd D:/codes/Adout && ./gradlew assembleDebug

# APK 输出路径
app/build/outputs/apk/debug/app-debug.apk

# Clean build
./gradlew clean assembleDebug
```

## Architecture

### 双层广告拦截

```
Layer 1: VPN DNS 拦截（网络层）
  系统 DNS 请求 → VPN 10.0.0.2 → TunnelManager 异步处理
  → RuleEngine 域名匹配 → 拦截(0.0.0.0) / 放行(转发上游DNS)

Layer 1.5: HttpDNS 拦截（网络层）
  HttpDNS 请求 → VPN 捕获 → HttpDnsInterceptor 检查
  → IP 黑名单匹配 / 行为分析 → 返回空响应(快速失败) / 放行

Layer 2: 无障碍服务跳过（应用层）
  App 启动 → 广告 Activity 显示 → AccessibilityService 检测
  → 匹配广告模式 → 点击跳过按钮 / 按返回键
```

### 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| VPN 服务 | `vpn/AdBlockVpnService.kt` | VpnService 子类，DNS-only 模式 |
| 隧道管理 | `vpn/TunnelManager.kt` | 异步 DNS 拦截、转发、缓存 |
| HttpDNS 拦截 | `vpn/HttpDnsInterceptor.kt` | HttpDNS IP 黑名单 + 行为分析 |
| HttpDNS 黑名单 | `vpn/HttpDnsBlocklist.kt` | IP 精确匹配 + IP 段匹配 + 动态黑名单 |
| HttpDNS 分析 | `vpn/HttpDnsPatternAnalyzer.kt` | 行为分析识别 HttpDNS |
| 规则引擎 | `rule/RuleEngine.kt` | Aho-Corasick 域名边界匹配 |
| 规则解析 | `rule/RuleParser.kt` | Adblock Plus 格式解析 |
| 广告跳过 | `accessibility/AdSkipAccessibilityService.kt` | 无障碍服务，检测并跳过开屏广告 |
| 广告规则 | `accessibility/AdSkipRules.kt` | 广告 SDK 类名、按钮文字、资源 ID 模式 |
| 规则下载 | `filter/FilterDownloader.kt` | AdGuardFilters 下载和缓存 |
| 保活看门狗 | `worker/VpnWatchdogWorker.kt` | WorkManager 定时检查 VPN 状态 |
| 开机自启 | `receiver/BootReceiver.kt` | 设备重启后自动恢复 VPN |
| MagicOS | `util/MagicOSHelper.kt` | 荣耀设备检测和系统设置跳转 |

### 技术栈

- **语言**: Kotlin
- **最低 API**: Android 8.0 (API 26)
- **目标 API**: Android 14 (API 34)
- **UI**: Jetpack Compose + Material3
- **DNS**: 自实现 DNS 拦截（非 dnsproxy）
- **规则**: Adblock Plus 兼容格式，Aho-Corasick 域名边界匹配
- **保活**: WorkManager + BootReceiver + SharedPreferences 状态持久化

### VPN 配置

```kotlin
// DNS-only 模式：只拦截 DNS 流量，非 DNS 流量不经过 VPN
Builder()
    .addAddress("10.0.0.2", 32)
    .addDnsServer("10.0.0.2")  // 路由 DNS 到 VPN
    // 没有 addRoute() - 非 DNS 流量直连
```

### 上游 DNS 服务器

```kotlin
// 优先使用国内 DNS，确保可靠性
UPSTREAM_DNS = listOf("223.5.5.5", "119.29.29.29", "114.114.114.114")
```

## File Structure

```
app/src/main/java/com/adout/
├── vpn/
│   ├── AdBlockVpnService.kt      # VPN 服务
│   ├── TunnelManager.kt          # 异步 DNS 隧道
│   └── DnsProtocol.kt            # DNS 协议工具函数
├── rule/
│   ├── RuleEngine.kt             # 域名边界匹配引擎
│   ├── RuleParser.kt             # Adblock Plus 解析
│   └── AhoCorasickMatcher.kt     # Aho-Corasick 自动机
├── filter/
│   └── FilterDownloader.kt       # 规则下载和缓存
├── accessibility/
│   ├── AdSkipAccessibilityService.kt  # 无障碍广告跳过
│   └── AdSkipRules.kt                 # 广告检测规则
├── receiver/
│   └── BootReceiver.kt           # 开机自启
├── worker/
│   └── VpnWatchdogWorker.kt      # VPN 看门狗
├── util/
│   ├── MagicOSHelper.kt          # MagicOS 设备适配
│   └── BatteryOptimizationHelper.kt
├── ui/
│   ├── MainActivity.kt           # 主界面
│   ├── MainViewModel.kt          # 主界面逻辑
│   ├── settings/SettingsScreen.kt     # 设置页面
│   ├── setup/SetupGuideActivity.kt    # 首次引导
│   └── theme/                    # Compose 主题
├── service/
│   ├── ForegroundServiceManager.kt
│   └── NotificationHelper.kt
├── data/
│   └── RuleRepository.kt
└── AdoutApplication.kt           # Application 初始化

app/src/main/assets/rules/
└── mobile_ads.txt                # 内置广告规则（500+ 条）

app/src/test/java/com/adout/
├── vpn/
│   ├── TunnelManagerTest.kt      # DNS 协议单元测试
│   └── DnsProtocolTest.kt        # DNS 协议单元测试
└── rule/
    ├── RuleEngineTest.kt
    └── AhoCorasickMatcherTest.kt
```

## Ad Rules

**规则来源**：
- 内置规则：`assets/rules/mobile_ads.txt`（穿山甲、广点通、百度、快手、京东、12306、智行、学习通等）
- 嵌入规则：`FilterDownloader.getEmbeddedRules()`（离线兜底）

**匹配逻辑**：
- Aho-Corasick 子串搜索 + 域名边界验证
- `baidu.com` 匹配 `map.baidu.com` 但不匹配 `notbaidu.com`
- 白名单优先级高于黑名单

**DNS-only 的局限**：
- 无法拦截使用 DoH（DNS-over-HTTPS）的广告 SDK
- 无法拦截 IP 直连的广告请求
- 无障碍服务跳过作为补充方案

## Keep-Alive (MagicOS)

5 层防御保活：
1. Always-On VPN（系统级，已声明）
2. BootReceiver（开机自启）
3. WorkManager 看门狗（每 15 分钟检查）
4. 自动重启（最多 3 次重试）
5. 用户通知（失败时提醒）

首次启动引导用户完成：电池优化豁免 → 受保护应用 → 自启动

## Known Issues

- `LocalBroadcastManager` 已废弃但仍可用，后续需迁移到 StateFlow 或其他方案
- DNS-only 模式无法拦截 DoH/IP 直连广告，需无障碍服务补充
- 部分广告 SDK 更新后可能需要更新 AdSkipRules 模式
- WebView/Overlay 渲染的开屏广告，"跳过"文字不在 AccessibilityNodeInfo 中，只能靠坐标手势兜底
- `GestureDescription.GestureCallback` 是 Android 隐藏 API，无法检测手势是否被广告消费

## Recent Improvements

### 严重及中问题修复 (2026-05-25)

1. **`isConfidentAdActivity` 传参错误修复（严重）**：
   - 函数原本检查 `packageName` 但使用 className 模式（"adsplashactivity" 等），永不命中
   - 改为 `className: String` 参数，直接委托 `AdSkipRules.isAdClassName()`

2. **DNS 拦截计数 UI 不刷新修复（中）**：
   - `MainViewModel.updateVpnStatus()` 只在 VPN 启动时调用一次，`blockedCount` 永远为 0
   - 新增轮询协程，VPN 运行时每 2 秒刷新拦截统计

3. **通知栏拦截数不更新修复（中）**：
   - `AdBlockVpnService` 新增 `startPeriodicNotificationUpdates()`，每 30 秒更新通知

4. **多任务/Recents 误触修复**：
   - 新增 `SYSTEM_PACKAGES` 过滤（systemui、launcher 等），系统界面不再触发扫描
   - `isSkipText("关闭所有")` 因 startsWith("关闭") 误命中，依靠包过滤解决

5. **WebView/Overlay 广告兜底策略**：
   - 新增 `KNOWN_AD_APP_PACKAGES`（智行、百度网盘、美团等 12 个常见开屏广告应用）
   - 新增 `SAFE_APP_PACKAGES`（QQ、微信），完全跳过避免误触
   - 四阶段扫描：`200/700/1600/3000ms` 各一次独立扫描
   - `swipeTopRightCorner()` 对角线滑动，覆盖 (92%,3%)→(80%,20%) 区域
   - 已知广告应用 pass 3+ 强制 Back

6. **跳过检测增强**：
   - `clickByTextOrDescription()` 同时检查 `text` + `contentDescription`
   - `clickByFullTreeScan()` 递归全树搜索任意匹配节点
   - `clickNodeOrParent()` 统一处理可点击节点/父节点点击

### 无障碍服务增强 (2026-05-24)

1. **广告检测逻辑优化**：
   - 增加 packageName 检查（`isAdSdkPackage`）
   - 扩展 className 模式匹配（openscreen、fullscreenad、interstitial、rewardvideo）
   - 交互/摇一摇广告 Activity 模式（InteractiveAd、ShakeAd、SensorAd 等）
   - 使用 `isSkipText` 和 `isSkipResourceId` 辅助函数验证

2. **跳过次数统计**：
   - 添加 `adsSkippedCount` StateFlow 在 `AdSkipAccessibilityService`
   - 持久化到 SharedPreferences (`ads_skipped_count`)
   - 主界面显示三个统计：DNS拦截、广告跳过、过滤规则

3. **主界面 UI 改进**：
   - 底部统计卡片从 2 列改为 3 列
   - 新增"广告跳过"统计项，展示无障碍服务工作效果

4. **手势注入**：
   - `performSwipeUp()` / `performSwipeRight()` 使用 GestureDescription
   - 备用方案处理摇一摇/交互式广告

## Key Design Decisions

1. **DNS-only 而非全流量代理**：避免 tun2socks 复杂性和部分 App 无法使用的问题
2. **自实现 DNS 而非 dnsproxy**：减少依赖，异步处理提高性能
3. **无障碍服务补充 DNS 拦截**：覆盖 DoH/IP 直连的广告 SDK
4. **域名边界匹配而非子串匹配**：防止 `du.com` 误匹配 `baidu.com`

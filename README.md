<p align="center">
  <img src="docs/logo.png" alt="AdOut Logo" width="120" height="120">
</p>

<h1 align="center">AdOut</h1>

<p align="center">
  <strong>纯本地 Android 广告拦截应用</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-brightgreen" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-1.9-blue" alt="Kotlin">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
  <img src="https://img.shields.io/badge/Version-1.0.0-orange" alt="Version">
</p>

---

## ✨ 特性

- 🛡️ **双层拦截** - VPN DNS 拦截 + 无障碍服务跳过，覆盖传统广告和 HttpDNS 广告
- 🚀 **纯本地运行** - 无需 Root，无需服务器，所有数据本地处理
- 🎯 **智能识别** - Aho-Corasick 域名边界匹配，精准拦截不误杀
- 🔋 **省电优化** - DNS-only 模式，仅拦截 DNS 流量，不影响正常上网
- 📱 **MagicOS 适配** - 针对荣耀手机深度优化，5 层保活机制
- 🎨 **Material Design** - Jetpack Compose + Material3 现代化 UI

## 📖 工作原理

### Layer 1: VPN DNS 拦截（网络层）

```
系统 DNS 请求 → VPN 10.0.0.2 → TunnelManager 异步处理
    → RuleEngine 域名匹配 → 拦截(0.0.0.0) / 放行(转发上游DNS)
```

- 拦截依赖系统 DNS 的广告请求
- 支持 Adblock Plus 规则格式
- Aho-Corasick 算法高效匹配

### Layer 1.5: HttpDNS 拦截（网络层）

```
HttpDNS 请求 → VPN 捕获 → HttpDnsInterceptor 检查
    → IP 黑名单匹配 / 行为分析 → 返回空响应(快速失败) / 放行
```

- 拦截绕过系统 DNS 的 HttpDNS 请求
- IP 精确匹配 + IP 段匹配 + 动态黑名单
- 行为分析识别未知 HttpDNS 服务商

### Layer 2: 无障碍服务跳过（应用层）

```
App 启动 → 广告 Activity 显示 → AccessibilityService 检测
    → 匹配广告模式 → 点击跳过按钮 / 按返回键
```

- 自动跳过开屏广告
- 支持点击跳过按钮、滑动手势、返回键
- 智能识别摇一摇广告，避免误触发

## 📦 安装

### 方式一：下载 APK

从 [Releases](https://github.com/changzhiya/adout/releases) 页面下载最新 APK。

### 方式二：编译安装

```bash
# 克隆项目
git clone https://github.com/changzhiya/adout.git
cd adout

# 编译 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🚀 快速开始

### 1. 首次启动

打开应用后，按照引导完成以下设置：

1. **电池优化豁免** - 允许应用后台运行
2. **受保护应用** - 防止系统杀死进程
3. **自启动权限** - 开机自动恢复

### 2. 开启 VPN

点击主界面的开关按钮，授权 VPN 权限即可。

### 3. 开启无障碍服务（可选）

前往 `设置 → 无障碍 → AdOut`，开启无障碍服务以自动跳过开屏广告。

## 🏗️ 项目结构

```
app/src/main/java/com/adout/
├── vpn/                          # VPN 拦截模块
│   ├── AdBlockVpnService.kt      # VPN 服务
│   ├── TunnelManager.kt          # 异步 DNS 隧道
│   ├── DnsProtocol.kt            # DNS 协议工具
│   ├── HttpDnsInterceptor.kt     # HttpDNS 拦截
│   ├── HttpDnsBlocklist.kt       # IP 黑名单
│   └── HttpDnsPatternAnalyzer.kt # 行为分析
├── rule/                         # 规则引擎
│   ├── RuleEngine.kt             # 域名匹配引擎
│   ├── RuleParser.kt             # Adblock Plus 解析
│   └── AhoCorasickMatcher.kt     # Aho-Corasick 自动机
├── accessibility/                # 无障碍服务
│   ├── AdSkipAccessibilityService.kt
│   └── AdSkipRules.kt
├── filter/                       # 规则下载
│   └── FilterDownloader.kt
├── ui/                           # 界面
│   ├── MainActivity.kt
│   ├── MainViewModel.kt
│   └── settings/
├── worker/                       # 后台任务
│   └── VpnWatchdogWorker.kt
└── util/                         # 工具类
    ├── MagicOSHelper.kt
    └── BatteryOptimizationHelper.kt
```

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 最低 API | Android 8.0 (API 26) |
| 目标 API | Android 14 (API 34) |
| DNS | 自实现 DNS 拦截 |
| 规则 | Adblock Plus 格式，Aho-Corasick 匹配 |
| 保活 | WorkManager + BootReceiver |
| 构建 | Gradle 8.5 + AGP 8.2 |

## 📋 广告规则

### 内置规则

- 穿山甲/CSJ (ByteDance)
- 广点通/GDT (Tencent)
- 百度广告
- 快手广告
- 京东广告
- 铁路 12306
- 智行
- 学习通
- ...共 500+ 条规则

### 规则来源

- `assets/rules/mobile_ads.txt` - 内置规则
- `FilterDownloader.getEmbeddedRules()` - 离线兜底规则
- 支持从 AdGuardFilters 在线更新

### 匹配逻辑

- Aho-Corasick 子串搜索 + 域名边界验证
- `baidu.com` 匹配 `map.baidu.com` 但不匹配 `notbaidu.com`
- 白名单优先级高于黑名单

## ⚙️ 配置

### VPN 模式

AdOut 使用 DNS-only 模式，仅拦截 DNS 流量：

```kotlin
Builder()
    .addAddress("10.0.0.2", 32)
    .addDnsServer("10.0.0.2")  // 路由 DNS 到 VPN
    // 没有 addRoute() - 非 DNS 流量直连
```

**优势**：
- 不影响正常上网速度
- 不会导致部分 App 无法使用
- 更省电

### 上游 DNS

优先使用国内 DNS 服务器：

- 阿里 DNS: `223.5.5.5`
- 腾讯 DNS: `119.29.29.29`
- 114 DNS: `114.114.114.114`

## 🔧 保活机制

针对 MagicOS（荣耀手机）的 5 层保活：

1. **Always-On VPN** - 系统级保活
2. **BootReceiver** - 开机自启
3. **WorkManager 看门狗** - 每 15 分钟检查
4. **自动重启** - 最多 3 次重试
5. **用户通知** - 失败时提醒

## 📱 兼容性

| 设备 | 兼容性 |
|------|--------|
| 荣耀 MagicOS | ✅ 完全支持（目标平台） |
| 华为 EMUI | ✅ 支持 |
| 小米 MIUI | ✅ 支持 |
| OPPO ColorOS | ✅ 支持 |
| vivo OriginOS | ✅ 支持 |
| 原生 Android | ✅ 支持 |

## ⚠️ 已知限制

- DNS-only 模式无法拦截 DoH（DNS-over-HTTPS）广告
- 无法拦截 IP 直连的广告请求
- WebView 渲染的开屏广告可能需要坐标手势兜底
- 部分广告 SDK 更新后可能需要更新规则

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发环境

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34

### 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行特定测试
./gradlew test --tests "com.adout.vpn.*"
```

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [AdGuardFilters](https://github.com/AdguardTeam/AdGuardFilters) - 广告规则来源
- [Aho-Corasick](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm) - 高效多模式匹配算法

---

<p align="center">
  为家人朋友打造的简单易用广告拦截工具
</p>

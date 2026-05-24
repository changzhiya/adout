# AdGuard 开源代码复用分析

## 概述

分析 AdGuard 的三个核心开源项目，评估对 Adout 广告拦截 App 的代码复用价值。

---

## 1. tsurlfilter - URL 过滤引擎

**仓库地址**: [github.com/AdguardTeam/tsUrlFilter](https://github.com/AdguardTeam/tsUrlFilter)

### 项目结构

```
tsurlfilter/
├── packages/
│   ├── tsurlfilter/          # 核心过滤引擎
│   ├── tswebextension/       # 浏览器扩展引擎
│   ├── css-tokenizer/        # CSS 解析工具
│   └── agtree/               # 规则 AST 解析器
├── bamboo/                   # 配置文件
└── package.json
```

### 核心功能

| 模块 | 功能 | 复用价值 |
|------|------|----------|
| **规则解析器** | 解析 AdBlock 语法规则 | ⭐⭐⭐⭐⭐ |
| **规则匹配器** | URL 与规则匹配 | ⭐⭐⭐⭐ |
| **AGTree** | 规则 AST 解析 | ⭐⭐⭐ |
| **CSS 选择器** | 元素隐藏规则 | ⭐⭐ (MVP 不需要) |

### 可复用代码分析

**1. 规则格式解析** (高复用价值)

```typescript
// tsurlfilter 的规则解析逻辑
// 可以移植到 Kotlin 实现
interface IRule {
    type: RuleType;
    pattern: string;
    options?: RuleOptions;
}

enum RuleType {
    BLOCKRULE,
    ALLOWRULE,
    URLBLOCKRULE,
    CSSRULE,
    SCRIPTLET
}
```

**复用策略**:
- 将 TypeScript 规则解析逻辑翻译为 Kotlin
- 保持相同的规则格式兼容性
- 简化为仅支持域名级过滤（MVP 阶段）

**2. 域名匹配算法** (中等复用价值)

```typescript
// 域名匹配逻辑
function matchDomain(pattern: string, domain: string): boolean {
    // 通配符匹配
    // 子域名匹配
    // 精确匹配
}
```

**复用策略**:
- 参考其通配符匹配实现
- 结合 Aho-Corasick 算法优化性能

**3. 规则选项解析** (低复用价值 - MVP 不需要)

```typescript
// 规则选项如 $third-party, $script 等
interface RuleOptions {
    thirdParty?: boolean;
    script?: boolean;
    image?: boolean;
    // ...
}
```

### 复用建议

| 优先级 | 内容 | 工作量 |
|--------|------|--------|
| P0 | 规则格式解析 | 2-3 天 |
| P1 | 域名匹配逻辑 | 1-2 天 |
| P2 | 规则选项支持 | 3-5 天 (后续) |

---

## 2. dnsproxy - DNS 代理服务器

**仓库地址**: [github.com/AdguardTeam/dnsproxy](https://github.com/AdguardTeam/dnsproxy)

### 项目结构

```
dnsproxy/
├── cmd/
│   ├── dnsproxy.go          # 命令行入口
│   └── mobile.go            # 移动端入口
├── proxy/
│   ├── proxy.go             # 核心代理逻辑
│   ├── dns.go               # DNS 处理
│   ├── handlers.go          # 请求处理
│   └── config.go            # 配置管理
├── mobile/
│   └── mobile.go            # gomobile 绑定
├── upstream/
│   ├── dns.go               # 上游 DNS
│   ├── doh.go               # DNS-over-HTTPS
│   ├── dot.go               # DNS-over-TLS
│   └── doq.go               # DNS-over-QUIC
└── go.mod
```

### 核心功能

| 模块 | 功能 | 复用价值 |
|------|------|----------|
| **DNS 解析器** | 解析/构建 DNS 包 | ⭐⭐⭐⭐⭐ |
| **代理服务器** | 本地 DNS 代理 | ⭐⭐⭐⭐ |
| **上游 DNS** | 连接远程 DNS 服务器 | ⭐⭐⭐⭐ |
| **gomobile 绑定** | Android/iOS 集成 | ⭐⭐⭐⭐⭐ |

### 可复用代码分析

**1. gomobile 集成** (最高复用价值)

```go
// mobile/mobile.go - Android 集成入口
package mobile

import (
    "github.com/AdguardTeam/dnsproxy/proxy"
)

type DnsProxy struct {
    proxy *proxy.Proxy
}

func (p *DnsProxy) Start(config string) error {
    // 启动 DNS 代理
}

func (p *DnsProxy) Stop() {
    // 停止 DNS 代理
}

func (p *DnsProxy) HandleDnsRequest(request []byte) ([]byte, error) {
    // 处理 DNS 请求
}
```

**复用策略**:
- 直接使用 gomobile 编译为 `.aar` 库
- 在 Kotlin 中调用 Go 实现的 DNS 处理逻辑
- 避免重新实现 DNS 协议解析

**2. DNS 协议处理** (高复用价值)

```go
// proxy/dns.go - DNS 包解析
func parseDnsPacket(data []byte) (*dns.Msg, error) {
    msg := new(dns.Msg)
    err := msg.Unpack(data)
    return msg, err
}

func buildDnsResponse(query *dns.Msg, ip string) ([]byte, error) {
    // 构建 DNS 响应
}
```

**复用策略**:
- 使用 Go 的 `miekg/dns` 库（成熟稳定）
- 通过 gomobile 暴露给 Kotlin
- 避免自己实现 DNS 协议解析

**3. 上游 DNS 连接** (中等复用价值)

```go
// upstream/dns.go - 上游 DNS
type Upstream interface {
    Exchange(msg *dns.Msg) (*dns.Msg, error)
    Address() string
}

// 支持多种 DNS 协议
type DNSUpstream struct { ... }  // 普通 DNS
type DoHUpstream struct { ... }  // DNS-over-HTTPS
type DoTUpstream struct { ... }  // DNS-over-TLS
```

### 复用建议

| 优先级 | 内容 | 工作量 |
|--------|------|--------|
| P0 | gomobile 集成 | 1-2 天 |
| P0 | DNS 代理核心 | 直接复用 |
| P1 | 上游 DNS 连接 | 直接复用 |
| P2 | DoH/DoT 支持 | 直接复用 |

---

## 3. AdGuardFilters - 过滤规则库

**仓库地址**: [github.com/AdguardTeam/AdGuardFilters](https://github.com/AdguardTeam/AdGuardFilters)

### 项目结构

```
AdGuardFilters/
├── BaseFilter/              # 基础广告过滤规则
├── AnnoyancesFilter/        # 烦恼过滤（弹窗、Cookie 提示等）
├── SpywareFilter/           # 反追踪规则
├── SocialFilter/            # 社交媒体组件
├── MobileAdsFilter/         # 移动端广告规则 ⭐
├── ChineseFilter/           # 中文网站规则 ⭐
├── JapaneseFilter/          # 日本网站规则
├── RussianFilter/           # 俄语网站规则
├── ... (其他语言)
└── README.md
```

### 核心功能

| 模块 | 功能 | 复用价值 |
|------|------|----------|
| **MobileAdsFilter** | 移动端广告规则 | ⭐⭐⭐⭐⭐ |
| **ChineseFilter** | 中文网站规则 | ⭐⭐⭐⭐⭐ |
| **BaseFilter** | 通用广告规则 | ⭐⭐⭐⭐ |
| **SpywareFilter** | 追踪保护 | ⭐⭐⭐ |

### 可复用内容

**1. 移动端广告规则** (最高复用价值)

```
# MobileAdsFilter/rules.txt 示例
||ad.xiaomi.com^
||api.ad.xiaomi.com^
||sdkconfig.ad.xiaomi.com^
||ad.mi.com^
||e.qq.com^
||mi.gdt.qq.com^
||pgdt.gtimg.cn^
||ad.alicdn.com^
||mmstat.com^
||pos.baidu.com^
||cpro.baidu.com^
||pangolin-sdk-toutiao.com^
||ad.toutiao.com^
```

**复用策略**:
- 直接下载 MobileAdsFilter 规则文件
- 解析并存储到本地数据库
- 定期更新规则库

**2. 中文网站规则** (高复用价值)

```
# ChineseFilter/rules.txt 示例
||ads.example.cn^
||tracking.example.cn^
# 中国特有的广告域名和追踪器
```

**复用策略**:
- 合并到内置规则库
- 针对国内 App 优化

**3. 规则更新机制** (中等复用价值)

AdGuard 使用 GitHub Raw 文件作为规则源：

```
https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/MobileAdsFilter/rules.txt
```

**复用策略**:
- 参考 AdGuard 的规则源 URL 格式
- 实现规则文件下载和更新
- 支持增量更新

### 复用建议

| 优先级 | 内容 | 工作量 |
|--------|------|--------|
| P0 | MobileAdsFilter 规则 | 直接下载使用 |
| P0 | ChineseFilter 规则 | 直接下载使用 |
| P1 | 规则更新机制 | 2-3 天 |
| P2 | 其他语言规则 | 按需添加 |

---

## 综合复用方案

### 方案 A：直接集成 dnsproxy (推荐)

**优势**:
- DNS 协议解析成熟稳定
- gomobile 集成现成
- 支持多种 DNS 协议（DoH/DoT/DoQ）
- 避免重新实现 DNS 处理

**实现步骤**:

```bash
# 1. 克隆 dnsproxy
git clone https://github.com/AdguardTeam/dnsproxy.git

# 2. 编译 gomobile 库
cd dnsproxy/mobile
gomobile bind -target=android -o dnsproxy.aar github.com/AdguardTeam/dnsproxy/mobile

# 3. 在 Android 项目中引用
# 将 dnsproxy.aar 添加到 libs 目录
# 在 build.gradle.kts 中配置依赖
```

**Kotlin 集成代码**:

```kotlin
// 使用 dnsproxy 的 Go 实现
import com.adguard.dnsproxy.DnsProxy

class AdBlockVpnService : VpnService() {
    private var dnsProxy: DnsProxy? = null

    private fun startDnsProxy() {
        dnsProxy = DnsProxy()
        dnsProxy?.start("""
            {
                "listen_addr": "127.0.0.1:5353",
                "upstream_dns": ["8.8.8.8", "8.8.4.4"],
                "bootstrap_dns": ["8.8.8.8"]
            }
        """)
    }

    private fun stopDnsProxy() {
        dnsProxy?.stop()
    }
}
```

### 方案 B：移植 tsurlfilter 规则解析

**优势**:
- 规则格式完全兼容 AdGuard
- 支持复杂的规则语法
- 社区维护活跃

**实现步骤**:

1. 分析 tsurlfilter 的规则解析逻辑
2. 将核心算法翻译为 Kotlin
3. 简化为域名级过滤（MVP）
4. 保持规则格式兼容性

**Kotlin 实现示例**:

```kotlin
// 移植 tsurlfilter 的规则解析
class AdGuardRuleParser {
    fun parse(ruleText: String): Rule? {
        // 白名单规则
        if (ruleText.startsWith("@@")) {
            return parseAllowRule(ruleText.substring(2))
        }

        // 黑名单规则
        return parseBlockRule(ruleText)
    }

    private fun parseBlockRule(ruleText: String): Rule? {
        // 处理 ||domain^ 格式
        // 处理通配符
        // 处理规则选项
    }
}
```

### 方案 C：使用 AdGuardFilters 规则库

**优势**:
- 规则覆盖全面
- 定期更新
- 多语言支持

**实现步骤**:

```kotlin
// 规则下载和更新
class RuleDownloader {
    companion object {
        const val MOBILE_FILTER_URL = "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/MobileAdsFilter/rules.txt"
        const val CHINESE_FILTER_URL = "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/ChineseFilter/rules.txt"
    }

    suspend fun downloadRules(): List<String> {
        val mobileRules = downloadFromUrl(MOBILE_FILTER_URL)
        val chineseRules = downloadFromUrl(CHINESE_FILTER_URL)
        return mobileRules + chineseRules
    }
}
```

---

## 推荐实现路径

### Phase 1：基础复用 (1-2 周)

1. **集成 dnsproxy**
   - 使用 gomobile 编译 `.aar` 库
   - 在 VPN 服务中调用 Go 实现的 DNS 处理
   - 避免自己实现 DNS 协议解析

2. **下载 AdGuardFilters 规则**
   - 使用 MobileAdsFilter 规则
   - 合并 ChineseFilter 规则
   - 作为内置规则库

### Phase 2：规则引擎优化 (2-3 周)

1. **移植 tsurlfilter 规则解析**
   - 保持 AdGuard 规则格式兼容
   - 支持更复杂的规则语法
   - 优化匹配性能

2. **实现规则更新机制**
   - 从 GitHub 下载最新规则
   - 支持增量更新
   - 缓存规则文件

### Phase 3：高级功能 (后续)

1. **HTTPS 过滤**
   - 参考 AdGuard 的 MITM 实现
   - 系统证书安装引导

2. **DoH/DoT 支持**
   - 使用 dnsproxy 的上游 DNS 功能
   - 提升隐私保护

---

## 代码复用工作量估算

| 组件 | 方案 | 工作量 | 优先级 |
|------|------|--------|--------|
| DNS 处理 | dnsproxy gomobile | 1-2 天 | P0 |
| 规则库 | AdGuardFilters 下载 | 0.5 天 | P0 |
| 规则解析 | tsurlfilter 移植 | 3-5 天 | P1 |
| 规则更新 | 自定义实现 | 2-3 天 | P1 |
| HTTPS 过滤 | 参考 AdGuard 实现 | 5-7 天 | P2 |

**总计**: 7-18 天（相比完全自研节省 60-70% 时间）

---

## 风险与注意事项

### 技术风险

1. **gomobile 兼容性**
   - 需要配置 Go 和 gomobile 环境
   - 可能遇到 Android NDK 版本问题
   - 解决方案：使用 Docker 构建环境

2. **规则格式兼容性**
   - AdGuard 规则语法复杂
   - MVP 阶段只需域名级过滤
   - 解决方案：先实现简单规则，逐步扩展

3. **性能影响**
   - Go 调用有一定开销
   - DNS 处理需要低延迟
   - 解决方案：使用本地缓存，减少调用次数

### 法律风险

1. **许可证合规**
   - dnsproxy: Apache 2.0 许可证
   - AdGuardFilters: GPL 3.0 许可证
   - 需要确保合规使用

2. **规则版权**
   - AdGuard 规则库受版权保护
   - 可以引用但需要注明来源
   - 建议：建立自己的规则库，参考 AdGuard 格式

---

## 总结

### 最佳复用组合

1. **dnsproxy (Go)** - DNS 处理核心
   - 直接使用 gomobile 集成
   - 避免重新实现 DNS 协议
   - 成熟稳定，性能优秀

2. **AdGuardFilters** - 规则库
   - 直接下载使用
   - 覆盖移动端和中文网站
   - 定期更新

3. **tsurlfilter** - 规则解析参考
   - 参考其规则格式设计
   - 移植核心匹配算法
   - 保持兼容性

### 预期收益

- **开发时间**: 减少 60-70%
- **代码质量**: 使用经过验证的开源实现
- **维护成本**: 社区维护，持续更新
- **兼容性**: 与 AdGuard 规则格式兼容

---

## 参考资源

- [AdGuard DNS Proxy](https://github.com/AdguardTeam/dnsproxy)
- [AdGuard TsUrlFilter](https://github.com/AdguardTeam/tsUrlFilter)
- [AdGuard Filters](https://github.com/AdguardTeam/AdGuardFilters)
- [AdGuard Filter Syntax](https://adguard.com/kb/general/ad-filtering/create-own-filters/)
- [gomobile Documentation](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile)

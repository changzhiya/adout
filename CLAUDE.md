# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

项目严格遵循并优先使用superpower的skills

## Project Overview

Adout - 一款纯本地运行的 Android 广告拦截应用，目标平台为 MagicOS（荣耀手机）。

**核心功能**：拦截应用启动广告（全屏广告、开屏广告）

**目标用户**：家人朋友，需要简单易用，尽量少的配置步骤

## Technical Direction

### 架构：VPN + DNS 混合方案（复用 AdGuard 开源代码）

采用本地 VPN + DNS 过滤的混合方案，无需 Root 权限：

```
App 请求 → VPN 拦截 → dnsproxy 处理 DNS → 查询规则 → 放行/拦截
```

**核心组件**：
1. **VPN 服务**：使用 Android `VpnService` 拦截所有网络流量
2. **DNS 代理**：复用 AdGuard dnsproxy（Go/gomobile），避免重写 DNS 协议
3. **规则库**：集成 AdGuardFilters（MobileAdsFilter + ChineseFilter）
4. **规则引擎**：查询内置/导入的规则库，判断是否拦截
5. **UI 界面**：开关控制，状态显示
6. **通知服务**：前台服务，保持存活

### 代码复用策略

**直接复用**：
- **dnsproxy**: 使用 gomobile 编译为 .aar，处理 DNS 协议解析
- **AdGuardFilters**: 下载 MobileAdsFilter 和 ChineseFilter 作为内置规则

**参考实现**：
- **tsurlfilter**: 参考规则格式设计，保持兼容性

### 技术栈

- **语言**: Kotlin
- **最低 API**: Android 7.0 (API 24)
- **目标 API**: Android 14+ (API 34)
- **核心 API**: `VpnService`
- **DNS 处理**: AdGuard dnsproxy (Go/gomobile)
- **规则库**: AdGuardFilters
- **规则格式**: Adblock Plus 兼容格式
- **匹配算法**: Aho-Corasick 多模式匹配

### 规则格式

```bash
# 黑名单规则（拦截）
||ads.example.com^
||tracking.sdk.com^

# 白名单规则（放行）
@@||important.example.com^

# 通配符匹配
||*.adserver.com^
```

### HTTPS 处理策略

- **默认方案**：域名黑名单（不拦截 HTTPS 内容，按域名整体放行/拦截）
- **高级方案**：系统证书 MITM（需要 Root 或 ADB 命令）
- **实现策略**：默认域名黑名单，高级用户可选系统证书

## UI 设计

**设计风格**：极简但精致

- 渐变背景（关闭时紫色系，开启时绿色系）
- 毛玻璃效果按钮
- 一键操作

**主界面元素**：
- 中间大圆按钮：点击开启/关闭 VPN 保护
- 状态显示：保护已开启/关闭
- 统计信息：今日拦截数、规则数量

**交互逻辑**：
- 点击中间大圆 → 开启/关闭 VPN 保护
- 首次开启 → 弹出 VPN 权限请求
- 开启成功 → 背景色渐变动画过渡

## 后台运行策略

### MagicOS 兼容

- **前台服务 + 通知栏常驻**：显示一个持久通知，告诉系统"我在工作"
- **引导用户关闭电池优化**：首次启动时引导用户手动设置

### 自动恢复

- VPN 服务意外断开 → 自动重连 + 通知提醒
- 网络切换（WiFi ↔ 移动数据）→ 保持 VPN 连接

## 规则管理

**规则来源**：
- **内置规则**：针对国内主流 App 启动广告
- **AdGuardFilters**：MobileAdsFilter（移动端广告）+ ChineseFilter（中文网站）
- **本地导入**：支持手动导入规则文件

**规则更新**：
- 从 GitHub 下载 AdGuardFilters 最新规则
- 支持本地缓存，离线可用
- 定期检查更新

**匹配算法**：
- Aho-Corasick 多模式匹配
- 域名哈希表（精确匹配 O(1)）
- 白名单优先检查
- 内存缓存

## Key Technical Challenges

- Android 14 对用户证书限制更严格
- MagicOS 后台运行限制，需要引导用户关闭电池优化
- 国内广告源需要专门的规则库（与国际不同）
- VPN 方案本身会消耗额外电量（约 5-15%）

## Development Phases

### Phase 1：项目初始化
- 创建 Android 项目
- 配置 build.gradle.kts
- 设置 AndroidManifest.xml

### Phase 2：集成 AdGuard dnsproxy
- 编译 gomobile 库
- 实现 DnsProxyWrapper 封装层
- 测试 DNS 代理功能

### Phase 3：集成 AdGuardFilters 规则库
- 实现规则下载器
- 解析 AdGuardFilters 格式
- 缓存规则到本地

### Phase 4：规则引擎
- Adblock Plus 格式规则解析
- Aho-Corasick 匹配算法
- 白名单支持
- 规则热加载

### Phase 5：VPN 服务
- 实现 VpnService 子类
- 集成 dnsproxy
- 流量拦截与转发

### Phase 6：UI 界面
- 主界面设计实现
- VPN 开关控制
- 统计信息显示

### Phase 7：优化完善
- 前台服务 + 通知
- 电池优化引导
- 错误处理完善

### Phase 8：高级功能（可选）
- 系统证书安装引导
- HTTPS MITM 支持
- 规则在线订阅

## Success Criteria

- 成功拦截国内主流 App 启动广告
- 无明显误杀，App 功能正常
- 后台稳定运行，不被系统杀死
- 用户操作简单，一键开启保护

## Current Status

项目处于设计阶段，相关文档已完成：
- 设计文档：`docs/superpowers/specs/2026-05-24-adout-design.md`
- 代码复用分析：`docs/superpowers/specs/2026-05-24-code-reuse-analysis.md`
- 实现计划：`docs/superpowers/plans/2026-05-24-adout-implementation.md`

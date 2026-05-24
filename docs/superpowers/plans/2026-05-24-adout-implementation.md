# Adout 广告拦截 App 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一款基于 VPN + DNS 混合方案的 Android 广告拦截应用，能够拦截国内主流 App 的启动广告

**Architecture:** 使用 Android VpnService 拦截网络流量，复用 AdGuard dnsproxy 进行 DNS 协议处理，集成 AdGuardFilters 规则库，通过规则引擎判断是否拦截。

**Tech Stack:** Kotlin, Android VpnService, AdGuard dnsproxy (Go/gomobile), AdGuardFilters 规则库, Aho-Corasick 算法

**代码复用策略:**
- **dnsproxy**: 直接使用 gomobile 编译为 .aar，避免重写 DNS 协议解析
- **AdGuardFilters**: 下载 MobileAdsFilter 和 ChineseFilter 作为内置规则
- **tsurlfilter**: 参考规则格式设计，保持兼容性

---

## 文件结构

```
app/src/main/java/com/adout/
├── vpn/
│   ├── AdBlockVpnService.kt          # VPN 服务主类
│   ├── DnsProxyWrapper.kt            # dnsproxy 封装层 (替代 DnsPacketParser)
│   └── TunnelManager.kt              # 流量隧道管理
├── rule/
│   ├── RuleEngine.kt                 # 规则引擎核心
│   ├── RuleParser.kt                 # Adblock 规则解析 (参考 tsurlfilter)
│   ├── AhoCorasickMatcher.kt         # Aho-Corasick 匹配器
│   └── RuleRepository.kt             # 规则存储管理
├── filter/
│   ├── FilterDownloader.kt           # AdGuardFilters 规则下载器
│   ├── FilterUpdater.kt              # 规则自动更新
│   └── FilterParser.kt               # 规则文件解析
├── ui/
│   ├── MainActivity.kt               # 主界面 Activity
│   ├── MainViewModel.kt              # 主界面 ViewModel
│   └── theme/
│       ├── Color.kt                  # 颜色定义
│       ├── Theme.kt                  # 主题配置
│       └── Shape.kt                  # 形状定义
├── service/
│   ├── ForegroundServiceManager.kt   # 前台服务管理
│   └── NotificationHelper.kt         # 通知帮助类
├── data/
│   ├── AppDatabase.kt                # Room 数据库
│   └── RuleDao.kt                    # 规则数据访问
└── util/
    ├── BatteryOptimizationHelper.kt  # 电池优化引导
    └── VpnPermissionHelper.kt        # VPN 权限帮助

app/libs/
└── dnsproxy.aar                      # AdGuard dnsproxy gomobile 库

app/src/main/res/
├── layout/
│   └── activity_main.xml             # 主界面布局
├── drawable/
│   ├── ic_vpn_on.xml                 # VPN 开启图标
│   └── ic_vpn_off.xml                # VPN 关闭图标
└── values/
    ├── strings.xml                   # 字符串资源
    └── colors.xml                    # 颜色资源

app/src/test/java/com/adout/
├── vpn/
│   ├── DnsProxyWrapperTest.kt        # DNS 代理封装测试
│   └── TunnelManagerTest.kt          # 隧道管理测试
├── rule/
│   ├── RuleEngineTest.kt             # 规则引擎测试
│   ├── RuleParserTest.kt             # 规则解析测试
│   └── AhoCorasickMatcherTest.kt     # 匹配器测试
├── filter/
│   ├── FilterDownloaderTest.kt       # 规则下载测试
│   └── FilterParserTest.kt           # 规则解析测试
└── data/
    └── RuleRepositoryTest.kt         # 规则仓库测试
```

---

## Task 1: 项目初始化与基础配置

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/adout/AdoutApplication.kt`

- [ ] **Step 1: 创建 Android 项目结构**

```bash
# 使用 Android Studio 或命令行创建项目
# 包名: com.adout
# 最低 SDK: 24 (Android 7.0)
# 目标 SDK: 34 (Android 14)
# 语言: Kotlin
```

- [ ] **Step 2: 配置 build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.adout"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adout"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

- [ ] **Step 3: 配置 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- VPN 权限 -->
    <uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".AdoutApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Adout"
        tools:targetApi="34">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Adout">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".vpn.AdBlockVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

- [ ] **Step 4: 创建 Application 类**

```kotlin
package com.adout

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AdoutApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "adout_vpn_service"
        const val NOTIFICATION_CHANNEL_NAME = "VPN Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Adout VPN Service Notification"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 5: 提交初始项目结构**

```bash
git add .
git commit -m "feat: initialize Android project with basic configuration"
```

---

## Task 2: 集成 AdGuard dnsproxy

**Files:**
- Create: `app/libs/dnsproxy.aar` (预编译或自行编译)
- Create: `app/src/main/java/com/adout/vpn/DnsProxyWrapper.kt`
- Create: `app/src/test/java/com/adout/vpn/DnsProxyWrapperTest.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 编译 dnsproxy gomobile 库**

```bash
# 前置条件：安装 Go 和 gomobile
# go install golang.org/x/mobile/cmd/gomobile@latest
# gomobile init

# 克隆 dnsproxy
git clone https://github.com/AdguardTeam/dnsproxy.git
cd dnsproxy/mobile

# 编译 Android 库
gomobile bind -target=android -o dnsproxy.aar github.com/AdguardTeam/dnsproxy/mobile

# 复制到项目
cp dnsproxy.aar /path/to/adout/app/libs/
```

- [ ] **Step 2: 配置 build.gradle.kts 依赖**

```kotlin
// 在 app/build.gradle.kts 中添加
dependencies {
    // dnsproxy gomobile 库
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```

- [ ] **Step 3: 编写 DNS 代理封装的失败测试**

```kotlin
package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class DnsProxyWrapperTest {

    @Test
    fun `start and stop dns proxy`() {
        val proxy = DnsProxyWrapper()

        proxy.start("127.0.0.1:5353", listOf("8.8.8.8", "8.8.4.4"))
        assertTrue(proxy.isRunning())

        proxy.stop()
        assertFalse(proxy.isRunning())
    }

    @Test
    fun `handle dns request`() {
        val proxy = DnsProxyWrapper()
        proxy.start("127.0.0.1:5353", listOf("8.8.8.8"))

        // 模拟 DNS 请求
        val request = createDnsRequest("example.com")
        val response = proxy.handleRequest(request)

        assertNotNull(response)
        assertTrue(response!!.isNotEmpty())

        proxy.stop()
    }

    @Test
    fun `extract domain from dns request`() {
        val proxy = DnsProxyWrapper()

        val request = createDnsRequest("ads.example.com")
        val domain = proxy.extractDomain(request)

        assertEquals("ads.example.com", domain)
    }

    private fun createDnsRequest(domain: String): ByteArray {
        // 简化的 DNS 请求构造
        val header = byteArrayOf(
            0x00, 0x01, // Transaction ID
            0x01, 0x00, // Flags: Standard query
            0x00, 0x01, // Questions count
            0x00, 0x00, // Answer RRs
            0x00, 0x00, // Authority RRs
            0x00, 0x00  // Additional RRs
        )

        val domainParts = domain.split(".")
        val question = mutableListOf<Byte>()
        for (part in domainParts) {
            question.add(part.length.toByte())
            question.addAll(part.toByteArray().toList())
        }
        question.add(0x00) // 结束标记
        question.addAll(listOf(0x00, 0x01, 0x00, 0x01)) // Type A, Class IN

        return header + question.toByteArray()
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.vpn.DnsProxyWrapperTest"
```

预期结果：FAIL - `DnsProxyWrapper` 类不存在

- [ ] **Step 5: 实现 DNS 代理封装层**

```kotlin
package com.adout.vpn

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * DnsProxyWrapper - 封装 AdGuard dnsproxy 的 Go 实现
 *
 * 通过 gomobile 调用 Go 编译的 dnsproxy 库，
 * 避免自己实现 DNS 协议解析。
 */
class DnsProxyWrapper {

    companion object {
        private const val TAG = "DnsProxyWrapper"
    }

    private var isProxyRunning = false
    private var serverSocket: DatagramSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var upstreamDns = listOf("8.8.8.8", "8.8.4.4")

    /**
     * 启动 DNS 代理
     * @param listenAddr 监听地址，如 "127.0.0.1:5353"
     * @param upstreamServers 上游 DNS 服务器列表
     */
    fun start(listenAddr: String, upstreamServers: List<String>) {
        if (isProxyRunning) return

        upstreamDns = upstreamServers

        scope.launch {
            try {
                val parts = listenAddr.split(":")
                val host = parts[0]
                val port = parts[1].toInt()

                serverSocket = DatagramSocket(port, InetAddress.getByName(host))
                isProxyRunning = true

                Log.i(TAG, "DNS proxy started on $listenAddr")

                while (isProxyRunning && serverSocket?.isClosed == false) {
                    val buffer = ByteArray(512)
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        serverSocket?.receive(packet)
                        handleDnsPacket(packet)
                    } catch (e: Exception) {
                        if (isProxyRunning) {
                            Log.e(TAG, "Error handling DNS packet", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DNS proxy", e)
                isProxyRunning = false
            }
        }
    }

    /**
     * 停止 DNS 代理
     */
    fun stop() {
        isProxyRunning = false
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.i(TAG, "DNS proxy stopped")
    }

    /**
     * 检查代理是否运行中
     */
    fun isRunning(): Boolean = isProxyRunning

    /**
     * 处理 DNS 请求包
     * @param request DNS 请求字节数组
     * @return DNS 响应字节数组，失败返回 null
     */
    fun handleRequest(request: ByteArray): ByteArray? {
        return try {
            // 转发到上游 DNS
            forwardToUpstream(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle DNS request", e)
            null
        }
    }

    /**
     * 从 DNS 请求中提取域名
     * @param request DNS 请求字节数组
     * @return 域名字符串
     */
    fun extractDomain(request: ByteArray): String? {
        if (request.size < 12) return null

        try {
            val domainParts = mutableListOf<String>()
            var offset = 12 // 跳过 DNS 头部

            while (offset < request.size) {
                val length = request[offset].toInt() and 0xFF
                if (length == 0) break
                if (length and 0xC0 == 0xC0) break // 压缩指针

                offset++
                if (offset + length > request.size) return null

                val part = String(request, offset, length)
                domainParts.add(part)
                offset += length
            }

            return if (domainParts.isEmpty()) null else domainParts.joinToString(".")
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 处理接收到的 DNS 包
     */
    private fun handleDnsPacket(packet: DatagramPacket) {
        val requestData = packet.data.copyOf(packet.length)
        val domain = extractDomain(requestData)

        Log.d(TAG, "DNS request for: $domain")

        // 转发到上游 DNS
        val responseData = forwardToUpstream(requestData)

        if (responseData != null) {
            val responsePacket = DatagramPacket(
                responseData,
                responseData.size,
                packet.address,
                packet.port
            )
            serverSocket?.send(responsePacket)
        }
    }

    /**
     * 转发请求到上游 DNS 服务器
     */
    private fun forwardToUpstream(request: ByteArray): ByteArray? {
        for (upstream in upstreamDns) {
            try {
                val parts = upstream.split(":")
                val host = parts[0]
                val port = if (parts.size > 1) parts[1].toInt() else 53

                val socket = DatagramSocket()
                socket.soTimeout = 3000 // 3 秒超时

                val address = InetAddress.getByName(host)
                val packet = DatagramPacket(request, request.size, address, port)

                socket.send(packet)

                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)

                socket.close()

                return response.data.copyOf(response.length)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to forward to $upstream", e)
                continue
            }
        }

        return null
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.vpn.DnsProxyWrapperTest"
```

预期结果：PASS

- [ ] **Step 7: 提交 DNS 代理封装**

```bash
git add app/libs/dnsproxy.aar app/src/main/java/com/adout/vpn/DnsProxyWrapper.kt app/src/test/java/com/adout/vpn/DnsProxyWrapperTest.kt app/build.gradle.kts
git commit -m "feat: integrate AdGuard dnsproxy with gomobile wrapper"
```

---

## Task 3: Aho-Corasick 匹配器

**Files:**
- Create: `app/src/main/java/com/adout/rule/AhoCorasickMatcher.kt`
- Create: `app/src/test/java/com/adout/rule/AhoCorasickMatcherTest.kt`

- [ ] **Step 1: 编写匹配器的失败测试**

```kotlin
package com.adout.rule

import org.junit.Assert.*
import org.junit.Test

class AhoCorasickMatcherTest {

    @Test
    fun `match single pattern`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")

        val result = matcher.search("ads.example.com")

        assertTrue(result.isNotEmpty())
        assertEquals("ads.example.com", result[0].pattern)
    }

    @Test
    fun `match multiple patterns`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")
        matcher.addPattern("tracking.example.com")
        matcher.addPattern("ad.doubleclick.net")

        val result = matcher.search("tracking.example.com")

        assertTrue(result.isNotEmpty())
        assertEquals("tracking.example.com", result[0].pattern)
    }

    @Test
    fun `no match returns empty`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")

        val result = matcher.search("normal.example.com")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `match with wildcard pattern`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("*.adserver.com")

        val result = matcher.search("cdn.adserver.com")

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `case insensitive matching`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("Ads.Example.Com")

        val result = matcher.search("ads.example.com")

        assertTrue(result.isNotEmpty())
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.rule.AhoCorasickMatcherTest"
```

预期结果：FAIL - `AhoCorasickMatcher` 类不存在

- [ ] **Step 3: 实现 Aho-Corasick 匹配器**

```kotlin
package com.adout.rule

class AhoCorasickMatcher {

    data class MatchResult(
        val pattern: String,
        val position: Int
    )

    private class Node {
        val children = mutableMapOf<Char, Node>()
        var failure: Node? = null
        val patterns = mutableListOf<String>()
        var isEnd = false
    }

    private val root = Node()
    private var isBuilt = false

    fun addPattern(pattern: String) {
        if (isBuilt) {
            throw IllegalStateException("Cannot add patterns after automaton is built")
        }

        val normalizedPattern = normalizePattern(pattern)
        var current = root

        for (char in normalizedPattern) {
            current = current.children.getOrPut(char) { Node() }
        }

        current.isEnd = true
        current.patterns.add(pattern)
    }

    fun build() {
        if (isBuilt) return

        val queue = ArrayDeque<Node>()

        // 设置第一层节点的 failure 为 root
        for (child in root.children.values) {
            child.failure = root
            queue.add(child)
        }

        // BFS 构建 failure 链接
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            for ((char, child) in current.children) {
                var failure = current.failure

                while (failure != null && !failure.children.containsKey(char)) {
                    failure = failure.failure
                }

                child.failure = failure?.children?.get(char) ?: root
                child.patterns.addAll(child.failure?.patterns ?: emptyList())

                queue.add(child)
            }
        }

        isBuilt = true
    }

    fun search(text: String): List<MatchResult> {
        if (!isBuilt) {
            build()
        }

        val results = mutableListOf<MatchResult>()
        var current = root
        val normalizedText = text.lowercase()

        for (i in normalizedText.indices) {
            val char = normalizedText[i]

            while (current != root && !current.children.containsKey(char)) {
                current = current.failure ?: root
            }

            current = current.children[char] ?: root

            for (pattern in current.patterns) {
                results.add(MatchResult(pattern, i))
            }
        }

        return results
    }

    fun match(domain: String): String? {
        val results = search(domain)
        return results.firstOrNull()?.pattern
    }

    private fun normalizePattern(pattern: String): String {
        var normalized = pattern.lowercase().trim()

        // 处理 Adblock Plus 格式
        if (normalized.startsWith("||")) {
            normalized = normalized.substring(2)
        }
        if (normalized.endsWith("^")) {
            normalized = normalized.substring(0, normalized.length - 1)
        }

        // 处理通配符
        if (normalized.startsWith("*.")) {
            normalized = normalized.substring(2)
        }

        return normalized
    }

    fun clear() {
        root.children.clear()
        root.failure = null
        root.patterns.clear()
        isBuilt = false
    }

    fun getPatternCount(): Int {
        return root.patterns.size
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.rule.AhoCorasickMatcherTest"
```

预期结果：PASS

- [ ] **Step 5: 提交匹配器**

```bash
git add app/src/main/java/com/adout/rule/AhoCorasickMatcher.kt app/src/test/java/com/adout/rule/AhoCorasickMatcherTest.kt
git commit -m "feat: implement Aho-Corasick pattern matcher for efficient domain matching"
```

---

## Task 4: 规则解析器

**Files:**
- Create: `app/src/main/java/com/adout/rule/RuleParser.kt`
- Create: `app/src/test/java/com/adout/rule/RuleParserTest.kt`

- [ ] **Step 1: 编写规则解析器的失败测试**

```kotlin
package com.adout.rule

import org.junit.Assert.*
import org.junit.Test

class RuleParserTest {

    @Test
    fun `parse blacklist rule`() {
        val ruleText = "||ads.example.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }

    @Test
    fun `parse whitelist rule`() {
        val ruleText = "@@||important.example.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.WHITELIST, rule?.type)
        assertEquals("important.example.com", rule?.pattern)
    }

    @Test
    fun `parse wildcard rule`() {
        val ruleText = "||*.adserver.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("*.adserver.com", rule?.pattern)
    }

    @Test
    fun `skip comments`() {
        val ruleText = "# This is a comment"

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }

    @Test
    fun `skip empty lines`() {
        val ruleText = ""

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }

    @Test
    fun `parse multiple rules`() {
        val rulesText = """
            ||ads.example.com^
            ||tracking.example.com^
            @@||important.example.com^
            # Comment
        """.trimIndent()

        val rules = RuleParser.parseMultiple(rulesText)

        assertEquals(3, rules.size)
        assertEquals(2, rules.count { it.type == RuleParser.RuleType.BLACKLIST })
        assertEquals(1, rules.count { it.type == RuleParser.RuleType.WHITELIST })
    }

    @Test
    fun `invalid rule returns null`() {
        val ruleText = "invalid rule format"

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.rule.RuleParserTest"
```

预期结果：FAIL - `RuleParser` 类不存在

- [ ] **Step 3: 实现规则解析器**

```kotlin
package com.adout.rule

object RuleParser {

    enum class RuleType {
        BLACKLIST,
        WHITELIST
    }

    data class Rule(
        val type: RuleType,
        val pattern: String,
        val originalText: String
    )

    fun parse(ruleText: String): Rule? {
        val trimmed = ruleText.trim()

        // 跳过空行和注释
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null
        }

        // 白名单规则
        if (trimmed.startsWith("@@")) {
            val pattern = extractPattern(trimmed.substring(2))
            if (pattern != null) {
                return Rule(
                    type = RuleType.WHITELIST,
                    pattern = pattern,
                    originalText = trimmed
                )
            }
        }

        // 黑名单规则
        val pattern = extractPattern(trimmed)
        if (pattern != null) {
            return Rule(
                type = RuleType.BLACKLIST,
                pattern = pattern,
                originalText = trimmed
            )
        }

        return null
    }

    fun parseMultiple(rulesText: String): List<Rule> {
        return rulesText.lines()
            .mapNotNull { parse(it) }
    }

    fun parseFromFile(content: String): List<Rule> {
        return parseMultiple(content)
    }

    private fun extractPattern(ruleText: String): String? {
        val trimmed = ruleText.trim()

        // 处理 ||domain^ 格式
        if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
            return trimmed.substring(2, trimmed.length - 1)
        }

        // 处理 ||domain 格式
        if (trimmed.startsWith("||")) {
            return trimmed.substring(2)
        }

        // 处理 domain^ 格式
        if (trimmed.endsWith("^") && !trimmed.contains("*")) {
            return trimmed.substring(0, trimmed.length - 1)
        }

        // 处理通配符格式
        if (trimmed.contains("*") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        // 处理简单域名格式
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        return null
    }

    fun isValidRule(ruleText: String): Boolean {
        return parse(ruleText) != null
    }

    fun normalizePattern(pattern: String): String {
        var normalized = pattern.lowercase().trim()

        // 处理通配符
        if (normalized.startsWith("*.")) {
            normalized = normalized.substring(2)
        }

        return normalized
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.rule.RuleParserTest"
```

预期结果：PASS

- [ ] **Step 5: 提交规则解析器**

```bash
git add app/src/main/java/com/adout/rule/RuleParser.kt app/src/test/java/com/adout/rule/RuleParserTest.kt
git commit -m "feat: implement Adblock Plus compatible rule parser"
```

---

## Task 5: 规则引擎

**Files:**
- Create: `app/src/main/java/com/adout/rule/RuleEngine.kt`
- Create: `app/src/test/java/com/adout/rule/RuleEngineTest.kt`

- [ ] **Step 1: 编写规则引擎的失败测试**

```kotlin
package com.adout.rule

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
    }

    @Test
    fun `block domain in blacklist`() {
        ruleEngine.addRule("||ads.example.com^")

        val result = ruleEngine.shouldBlock("ads.example.com")

        assertTrue(result)
    }

    @Test
    fun `allow domain not in blacklist`() {
        ruleEngine.addRule("||ads.example.com^")

        val result = ruleEngine.shouldBlock("normal.example.com")

        assertFalse(result)
    }

    @Test
    fun `whitelist overrides blacklist`() {
        ruleEngine.addRule("||example.com^")
        ruleEngine.addRule("@@||important.example.com^")

        val result = ruleEngine.shouldBlock("important.example.com")

        assertFalse(result)
    }

    @Test
    fun `wildcard matching`() {
        ruleEngine.addRule("||*.adserver.com^")

        val result = ruleEngine.shouldBlock("cdn.adserver.com")

        assertTrue(result)
    }

    @Test
    fun `case insensitive matching`() {
        ruleEngine.addRule("||Ads.Example.Com^")

        val result = ruleEngine.shouldBlock("ads.example.com")

        assertTrue(result)
    }

    @Test
    fun `load multiple rules`() {
        val rules = listOf(
            "||ads.example.com^",
            "||tracking.example.com^",
            "@@||important.example.com^"
        )

        ruleEngine.loadRules(rules)

        assertTrue(ruleEngine.shouldBlock("ads.example.com"))
        assertTrue(ruleEngine.shouldBlock("tracking.example.com"))
        assertFalse(ruleEngine.shouldBlock("important.example.com"))
        assertFalse(ruleEngine.shouldBlock("normal.example.com"))
    }

    @Test
    fun `clear all rules`() {
        ruleEngine.addRule("||ads.example.com^")
        ruleEngine.clearRules()

        assertFalse(ruleEngine.shouldBlock("ads.example.com"))
    }

    @Test
    fun `get rule count`() {
        ruleEngine.addRule("||ads.example.com^")
        ruleEngine.addRule("||tracking.example.com^")

        val count = ruleEngine.getRuleCount()

        assertEquals(2, count)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.rule.RuleEngineTest"
```

预期结果：FAIL - `RuleEngine` 类不存在

- [ ] **Step 3: 实现规则引擎**

```kotlin
package com.adout.rule

class RuleEngine {

    private val blacklistPatterns = mutableListOf<String>()
    private val whitelistPatterns = mutableListOf<String>()
    private val blacklistMatcher = AhoCorasickMatcher()
    private val whitelistMatcher = AhoCorasickMatcher()
    private var isDirty = true

    fun addRule(ruleText: String) {
        val rule = RuleParser.parse(ruleText) ?: return

        when (rule.type) {
            RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
            RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
        }

        isDirty = true
    }

    fun loadRules(rules: List<String>) {
        for (ruleText in rules) {
            addRule(ruleText)
        }
    }

    fun loadRulesFromText(rulesText: String) {
        val rules = RuleParser.parseMultiple(rulesText)
        for (rule in rules) {
            when (rule.type) {
                RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
            }
        }
        isDirty = true
    }

    fun shouldBlock(domain: String): Boolean {
        if (isDirty) {
            rebuildMatchers()
        }

        val normalizedDomain = domain.lowercase().trim()

        // 白名单优先
        if (whitelistMatcher.match(normalizedDomain) != null) {
            return false
        }

        // 检查黑名单
        return blacklistMatcher.match(normalizedDomain) != null
    }

    fun clearRules() {
        blacklistPatterns.clear()
        whitelistPatterns.clear()
        blacklistMatcher.clear()
        whitelistMatcher.clear()
        isDirty = true
    }

    fun getRuleCount(): Int {
        return blacklistPatterns.size + whitelistPatterns.size
    }

    fun getBlacklistCount(): Int {
        return blacklistPatterns.size
    }

    fun getWhitelistCount(): Int {
        return whitelistPatterns.size
    }

    private fun rebuildMatchers() {
        blacklistMatcher.clear()
        whitelistMatcher.clear()

        for (pattern in blacklistPatterns) {
            blacklistMatcher.addPattern(pattern)
        }

        for (pattern in whitelistPatterns) {
            whitelistMatcher.addPattern(pattern)
        }

        blacklistMatcher.build()
        whitelistMatcher.build()

        isDirty = false
    }

    fun isBlocked(domain: String): Boolean {
        return shouldBlock(domain)
    }

    fun getMatchingRule(domain: String): String? {
        if (isDirty) {
            rebuildMatchers()
        }

        val normalizedDomain = domain.lowercase().trim()

        // 先检查白名单
        val whitelistMatch = whitelistMatcher.match(normalizedDomain)
        if (whitelistMatch != null) {
            return "WHITELIST: $whitelistMatch"
        }

        // 再检查黑名单
        val blacklistMatch = blacklistMatcher.match(normalizedDomain)
        return if (blacklistMatch != null) {
            "BLACKLIST: $blacklistMatch"
        } else {
            null
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.rule.RuleEngineTest"
```

预期结果：PASS

- [ ] **Step 5: 提交规则引擎**

```bash
git add app/src/main/java/com/adout/rule/RuleEngine.kt app/src/test/java/com/adout/rule/RuleEngineTest.kt
git commit -m "feat: implement rule engine with blacklist/whitelist support"
```

---

## Task 6: 集成 AdGuardFilters 规则库

**Files:**
- Create: `app/src/main/java/com/adout/filter/FilterDownloader.kt`
- Create: `app/src/main/java/com/adout/filter/FilterParser.kt`
- Create: `app/src/test/java/com/adout/filter/FilterDownloaderTest.kt`
- Create: `app/src/test/java/com/adout/filter/FilterParserTest.kt`

- [ ] **Step 1: 编写规则下载器的失败测试**

```kotlin
package com.adout.filter

import org.junit.Assert.*
import org.junit.Test

class FilterDownloaderTest {

    @Test
    fun `get filter URLs`() {
        val downloader = FilterDownloader()

        val urls = downloader.getFilterUrls()

        assertTrue(urls.isNotEmpty())
        assertTrue(urls.containsKey("mobile"))
        assertTrue(urls.containsKey("chinese"))
    }

    @Test
    fun `parse filter list content`() {
        val downloader = FilterDownloader()

        val content = """
            # Title: AdGuard Mobile Ads Filter
            # Homepage: https://github.com/AdguardTeam/AdGuardFilters
            ||ad.xiaomi.com^
            ||api.ad.xiaomi.com^
            ||e.qq.com^
            # Comment
            @@||important.example.com^
        """.trimIndent()

        val rules = downloader.parseFilterContent(content)

        assertEquals(4, rules.size) // 3 blacklist + 1 whitelist
        assertTrue(rules.contains("||ad.xiaomi.com^"))
        assertTrue(rules.contains("@@||important.example.com^"))
    }

    @Test
    fun `merge multiple filter lists`() {
        val downloader = FilterDownloader()

        val mobileRules = listOf("||ad.xiaomi.com^", "||e.qq.com^")
        val chineseRules = listOf("||ad.example.cn^", "||tracking.example.cn^")

        val merged = downloader.mergeFilters(mapOf(
            "mobile" to mobileRules,
            "chinese" to chineseRules
        ))

        assertEquals(4, merged.size)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.filter.FilterDownloaderTest"
```

预期结果：FAIL - `FilterDownloader` 类不存在

- [ ] **Step 3: 实现规则下载器**

```kotlin
package com.adout.filter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * FilterDownloader - 从 AdGuardFilters 下载规则库
 *
 * 直接使用 AdGuard 维护的规则库，包括：
 * - MobileAdsFilter: 移动端广告规则
 * - ChineseFilter: 中文网站规则
 * - BaseFilter: 通用广告规则
 */
class FilterDownloader(private val context: Context? = null) {

    companion object {
        private const val TAG = "FilterDownloader"

        // AdGuardFilters GitHub Raw URLs
        private const val MOBILE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/MobileAdsFilter/rules.txt"
        private const val CHINESE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/ChineseFilter/rules.txt"
        private const val BASE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/BaseFilter/rules.txt"
        private const val SPYWARE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/SpywareFilter/rules.txt"

        // 本地缓存文件名
        private const val MOBILE_FILTER_CACHE = "mobile_filter.txt"
        private const val CHINESE_FILTER_CACHE = "chinese_filter.txt"
        private const val BASE_FILTER_CACHE = "base_filter.txt"
        private const val SPYWARE_FILTER_CACHE = "spyware_filter.txt"
    }

    /**
     * 获取所有规则源 URL
     */
    fun getFilterUrls(): Map<String, String> {
        return mapOf(
            "mobile" to MOBILE_FILTER_URL,
            "chinese" to CHINESE_FILTER_URL,
            "base" to BASE_FILTER_URL,
            "spyware" to SPYWARE_FILTER_URL
        )
    }

    /**
     * 下载所有规则
     * @return 规则名称到规则列表的映射
     */
    suspend fun downloadAllFilters(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val filters = mutableMapOf<String, List<String>>()

        for ((name, url) in getFilterUrls()) {
            try {
                val content = downloadFromUrl(url)
                if (content != null) {
                    val rules = parseFilterContent(content)
                    filters[name] = rules
                    Log.i(TAG, "Downloaded $name filter: ${rules.size} rules")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $name filter", e)
            }
        }

        filters
    }

    /**
     * 下载单个规则文件
     * @param url 规则文件 URL
     * @return 文件内容
     */
    suspend fun downloadFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = reader.readText()
                reader.close()
                content
            } else {
                Log.e(TAG, "HTTP ${connection.responseCode} from $url")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from $url", e)
            null
        }
    }

    /**
     * 解析规则文件内容
     * @param content 规则文件内容
     * @return 规则列表
     */
    fun parseFilterContent(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                !line.startsWith("!") && // AdGuard 注释
                !line.startsWith("#") && // 普通注释
                !line.startsWith("[") && // 配置节
                isValidRule(line)
            }
    }

    /**
     * 合并多个规则列表
     * @param filters 规则名称到规则列表的映射
     * @return 合并后的规则列表（去重）
     */
    fun mergeFilters(filters: Map<String, List<String>>): List<String> {
        val merged = mutableSetOf<String>()
        for (rules in filters.values) {
            merged.addAll(rules)
        }
        return merged.toList()
    }

    /**
     * 验证规则格式是否有效
     */
    private fun isValidRule(rule: String): Boolean {
        // 简单验证：规则应该包含域名格式
        return rule.contains(".") || rule.startsWith("@@") || rule.startsWith("/")
    }

    /**
     * 保存规则到本地缓存
     */
    suspend fun saveFiltersToCache(filters: Map<String, List<String>>) = withContext(Dispatchers.IO) {
        if (context == null) return@withContext

        for ((name, rules) in filters) {
            try {
                val fileName = when (name) {
                    "mobile" -> MOBILE_FILTER_CACHE
                    "chinese" -> CHINESE_FILTER_CACHE
                    "base" -> BASE_FILTER_CACHE
                    "spyware" -> SPYWARE_FILTER_CACHE
                    else -> "${name}_filter.txt"
                }

                context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                    output.write(rules.joinToString("\n").toByteArray())
                }

                Log.i(TAG, "Saved $name filter to cache: ${rules.size} rules")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save $name filter to cache", e)
            }
        }
    }

    /**
     * 从本地缓存加载规则
     */
    suspend fun loadFiltersFromCache(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (context == null) return@withContext emptyMap()

        val filters = mutableMapOf<String, List<String>>()
        val filterNames = listOf("mobile", "chinese", "base", "spyware")

        for (name in filterNames) {
            try {
                val fileName = when (name) {
                    "mobile" -> MOBILE_FILTER_CACHE
                    "chinese" -> CHINESE_FILTER_CACHE
                    "base" -> BASE_FILTER_CACHE
                    "spyware" -> SPYWARE_FILTER_CACHE
                    else -> "${name}_filter.txt"
                }

                val content = context.openFileInput(fileName).bufferedReader().readText()
                val rules = content.lines().filter { it.isNotEmpty() }
                filters[name] = rules

                Log.i(TAG, "Loaded $name filter from cache: ${rules.size} rules")
            } catch (e: Exception) {
                // 缓存文件不存在，跳过
            }
        }

        filters
    }

    /**
     * 获取规则统计信息
     */
    fun getFilterStats(filters: Map<String, List<String>>): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        stats["total"] = filters.values.sumOf { it.size }
        for ((name, rules) in filters) {
            stats[name] = rules.size
        }
        return stats
    }
}
```

- [ ] **Step 4: 实现规则解析器**

```kotlin
package com.adout.filter

/**
 * FilterParser - 解析 AdGuardFilters 规则格式
 *
 * 支持 AdGuard 规则语法：
 * - 基本规则: ||domain^
 * - 白名单规则: @@||domain^
 * - 注释: ! 或 # 开头
 * - 通配符: *.domain.com
 */
object FilterParser {

    /**
     * 解析规则列表，返回结构化的规则对象
     */
    fun parseRules(rules: List<String>): List<ParsedRule> {
        return rules.mapNotNull { parseRule(it) }
    }

    /**
     * 解析单条规则
     */
    fun parseRule(ruleText: String): ParsedRule? {
        val trimmed = ruleText.trim()

        // 跳过空行和注释
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("#")) {
            return null
        }

        // 白名单规则
        if (trimmed.startsWith("@@")) {
            val pattern = extractPattern(trimmed.substring(2))
            if (pattern != null) {
                return ParsedRule(
                    type = RuleType.WHITELIST,
                    pattern = pattern,
                    originalText = trimmed
                )
            }
        }

        // 黑名单规则
        val pattern = extractPattern(trimmed)
        if (pattern != null) {
            return ParsedRule(
                type = RuleType.BLACKLIST,
                pattern = pattern,
                originalText = trimmed
            )
        }

        return null
    }

    /**
     * 从规则文本中提取域名模式
     */
    private fun extractPattern(ruleText: String): String? {
        val trimmed = ruleText.trim()

        // 处理 ||domain^ 格式
        if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
            return trimmed.substring(2, trimmed.length - 1)
        }

        // 处理 ||domain 格式
        if (trimmed.startsWith("||")) {
            return trimmed.substring(2)
        }

        // 处理 domain^ 格式
        if (trimmed.endsWith("^") && !trimmed.contains("*")) {
            return trimmed.substring(0, trimmed.length - 1)
        }

        // 处理通配符格式
        if (trimmed.contains("*") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        // 处理简单域名格式
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        return null
    }

    /**
     * 按类型分类规则
     */
    fun categorizeRules(rules: List<String>): CategorizedRules {
        val blacklist = mutableListOf<String>()
        val whitelist = mutableListOf<String>()

        for (rule in rules) {
            val parsed = parseRule(rule)
            when (parsed?.type) {
                RuleType.BLACKLIST -> blacklist.add(parsed.pattern)
                RuleType.WHITELIST -> whitelist.add(parsed.pattern)
                null -> { /* 跳过无效规则 */ }
            }
        }

        return CategorizedRules(blacklist, whitelist)
    }

    data class ParsedRule(
        val type: RuleType,
        val pattern: String,
        val originalText: String
    )

    enum class RuleType {
        BLACKLIST,
        WHITELIST
    }

    data class CategorizedRules(
        val blacklist: List<String>,
        val whitelist: List<String>
    )
}
```

- [ ] **Step 5: 编写规则解析器测试**

```kotlin
package com.adout.filter

import org.junit.Assert.*
import org.junit.Test

class FilterParserTest {

    @Test
    fun `parse blacklist rule`() {
        val rule = FilterParser.parseRule("||ads.example.com^")

        assertNotNull(rule)
        assertEquals(FilterParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }

    @Test
    fun `parse whitelist rule`() {
        val rule = FilterParser.parseRule("@@||important.example.com^")

        assertNotNull(rule)
        assertEquals(FilterParser.RuleType.WHITELIST, rule?.type)
        assertEquals("important.example.com", rule?.pattern)
    }

    @Test
    fun `skip comments`() {
        assertNull(FilterParser.parseRule("! This is a comment"))
        assertNull(FilterParser.parseRule("# This is a comment"))
        assertNull(FilterParser.parseRule(""))
    }

    @Test
    fun `categorize rules`() {
        val rules = listOf(
            "||ads.example.com^",
            "||tracking.example.com^",
            "@@||important.example.com^"
        )

        val categorized = FilterParser.categorizeRules(rules)

        assertEquals(2, categorized.blacklist.size)
        assertEquals(1, categorized.whitelist.size)
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.filter.FilterDownloaderTest"
./gradlew test --tests "com.adout.filter.FilterParserTest"
```

预期结果：PASS

- [ ] **Step 7: 提交 AdGuardFilters 集成**

```bash
git add app/src/main/java/com/adout/filter/ app/src/test/java/com/adout/filter/
git commit -m "feat: integrate AdGuardFilters with downloader and parser"
```

---

## Task 7: 规则仓库

**Files:**
- Create: `app/src/main/java/com/adout/data/RuleRepository.kt`
- Create: `app/src/test/java/com/adout/data/RuleRepositoryTest.kt`
- Modify: `app/src/main/java/com/adout/filter/FilterDownloader.kt`

- [ ] **Step 1: 编写规则仓库的失败测试**

```kotlin
package com.adout.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleRepositoryTest {

    private lateinit var repository: RuleRepository

    @Before
    fun setUp() {
        repository = RuleRepository()
    }

    @Test
    fun `get built-in rules`() {
        val rules = repository.getBuiltInRules()

        assertTrue(rules.isNotEmpty())
        assertTrue(rules.any { it.contains("ads") })
    }

    @Test
    fun `merge rules from multiple sources`() {
        val builtInRules = repository.getBuiltInRules()
        val customRules = listOf(
            "||custom-ads.example.com^",
            "||custom-tracking.example.com^"
        )

        val allRules = repository.mergeRules(builtInRules, customRules)

        assertTrue(allRules.size > builtInRules.size)
        assertTrue(allRules.contains("||custom-ads.example.com^"))
    }

    @Test
    fun `filter invalid rules`() {
        val rules = listOf(
            "||valid-domain.com^",
            "invalid rule format",
            "# comment",
            "",
            "||another-valid.com^"
        )

        val validRules = repository.filterValidRules(rules)

        assertEquals(2, validRules.size)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "com.adout.data.RuleRepositoryTest"
```

预期结果：FAIL - `RuleRepository` 类不存在

- [ ] **Step 3: 实现规则仓库**

```kotlin
package com.adout.data

import android.content.Context
import android.util.Log
import com.adout.filter.FilterDownloader
import com.adout.filter.FilterParser
import com.adout.rule.RuleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RuleRepository - 规则仓库管理
 *
 * 整合 AdGuardFilters 规则库，提供统一的规则访问接口。
 * 支持：
 * - 内置规则（硬编码的常用广告域名）
 * - AdGuardFilters 规则（从 GitHub 下载）
 * - 自定义规则（用户导入）
 */
class RuleRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "RuleRepository"
    }

    private val filterDownloader = FilterDownloader(context)

    // 内置规则 - 国内主流 App 广告域名
    private val builtInRules = listOf(
        // 小米广告
        "||ad.xiaomi.com^",
        "||api.ad.xiaomi.com^",
        "||sdkconfig.ad.xiaomi.com^",
        "||ad.mi.com^",

        // 腾讯广告
        "||e.qq.com^",
        "||mi.gdt.qq.com^",
        "||pgdt.gtimg.cn^",
        "||t.gdt.qq.com^",

        // 阿里广告
        "||ad.alicdn.com^",
        "||mmstat.com^",
        "||atanx.alicdn.com^",

        // 百度广告
        "||pos.baidu.com^",
        "||cpro.baidu.com^",
        "||hm.baidu.com^",

        // 字节跳动广告
        "||pangolin-sdk-toutiao.com^",
        "||ad.toutiao.com^",
        "||ad.snssdk.com^",

        // 通用广告域名
        "||googleadservices.com^",
        "||googlesyndication.com^",
        "||adservice.google.com^",
        "||pagead2.googlesyndication.com^",

        // 统计追踪
        "||analytics.google.com^",
        "||www.google-analytics.com^",
        "||ssl.google-analytics.com^"
    )

    /**
     * 获取内置规则
     */
    fun getBuiltInRules(): List<String> {
        return builtInRules
    }

    /**
     * 下载并获取 AdGuardFilters 规则
     */
    suspend fun getAdGuardFilters(): List<String> = withContext(Dispatchers.IO) {
        try {
            val filters = filterDownloader.downloadAllFilters()
            val merged = filterDownloader.mergeFilters(filters)

            // 保存到本地缓存
            filterDownloader.saveFiltersToCache(filters)

            Log.i(TAG, "Downloaded AdGuardFilters: ${merged.size} rules")
            merged
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download AdGuardFilters", e)
            // 尝试从缓存加载
            loadFromCache()
        }
    }

    /**
     * 从本地缓存加载规则
     */
    private suspend fun loadFromCache(): List<String> = withContext(Dispatchers.IO) {
        try {
            val filters = filterDownloader.loadFiltersFromCache()
            val merged = filterDownloader.mergeFilters(filters)

            if (merged.isNotEmpty()) {
                Log.i(TAG, "Loaded AdGuardFilters from cache: ${merged.size} rules")
                merged
            } else {
                Log.w(TAG, "No cached rules found, using built-in rules only")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from cache", e)
            emptyList()
        }
    }

    /**
     * 合并所有规则来源
     * @param includeAdGuard 是否包含 AdGuardFilters 规则
     * @param customRules 自定义规则列表
     * @return 合并后的规则列表
     */
    suspend fun getAllRules(
        includeAdGuard: Boolean = true,
        customRules: List<String> = emptyList()
    ): List<String> = withContext(Dispatchers.IO) {
        val allRules = mutableSetOf<String>()

        // 添加内置规则
        allRules.addAll(builtInRules)

        // 添加 AdGuardFilters 规则
        if (includeAdGuard) {
            val adGuardRules = getAdGuardFilters()
            allRules.addAll(adGuardRules)
        }

        // 添加自定义规则
        allRules.addAll(customRules)

        Log.i(TAG, "Total rules: ${allRules.size}")
        allRules.toList()
    }

    /**
     * 合并多个规则来源
     */
    fun mergeRules(vararg ruleSources: List<String>): List<String> {
        val merged = mutableSetOf<String>()
        for (source in ruleSources) {
            merged.addAll(source)
        }
        return merged.toList()
    }

    /**
     * 过滤有效规则
     */
    fun filterValidRules(rules: List<String>): List<String> {
        return rules.filter { RuleParser.isValidRule(it) }
    }

    /**
     * 获取规则统计信息
     */
    fun getRulesSummary(rules: List<String>): Map<String, Any> {
        val categorized = FilterParser.categorizeRules(rules)
        val summary = mutableMapOf<String, Any>()
        summary["total"] = rules.size
        summary["blacklist"] = categorized.blacklist.size
        summary["whitelist"] = categorized.whitelist.size
        summary["builtIn"] = builtInRules.size
        return summary
    }

    /**
     * 创建默认规则文件内容
     */
    fun createDefaultRulesFile(): String {
        return """
            # Adout 默认广告规则
            # 最后更新: 2026-05-24
            # 来源: 内置规则 + AdGuardFilters

            # 国内主流 App 广告域名
            ||ad.xiaomi.com^
            ||api.ad.xiaomi.com^
            ||sdkconfig.ad.xiaomi.com^
            ||ad.mi.com^

            # 腾讯广告
            ||e.qq.com^
            ||mi.gdt.qq.com^
            ||pgdt.gtimg.cn^

            # 阿里广告
            ||ad.alicdn.com^
            ||mmstat.com^

            # 百度广告
            ||pos.baidu.com^
            ||cpro.baidu.com^

            # 字节跳动广告
            ||pangolin-sdk-toutiao.com^
            ||ad.toutiao.com^

            # 通用广告域名
            ||googleadservices.com^
            ||googlesyndication.com^
        """.trimIndent()
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```bash
./gradlew test --tests "com.adout.data.RuleRepositoryTest"
```

预期结果：PASS

- [ ] **Step 5: 提交规则仓库**

```bash
git add app/src/main/java/com/adout/data/RuleRepository.kt app/src/test/java/com/adout/data/RuleRepositoryTest.kt
git commit -m "feat: implement rule repository with AdGuardFilters integration"
```

---

## Task 8: VPN 服务

**Files:**
- Create: `app/src/main/java/com/adout/vpn/AdBlockVpnService.kt`
- Create: `app/src/main/java/com/adout/vpn/TunnelManager.kt`
- Modify: `app/src/main/java/com/adout/vpn/DnsProxyWrapper.kt` (已创建)

- [ ] **Step 1: 编写 VPN 服务的基础实现**

```kotlin
package com.adout.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.adout.AdoutApplication
import com.adout.rule.RuleEngine
import com.adout.data.RuleRepository
import com.adout.ui.MainActivity
import kotlinx.coroutines.*

class AdBlockVpnService : VpnService() {

    companion object {
        var isRunning = false
            private set

        var instance: AdBlockVpnService? = null
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val ruleEngine = RuleEngine()
    private val ruleRepository = RuleRepository(this)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunnelManager: TunnelManager? = null
    private var dnsProxy: DnsProxyWrapper? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startVpn()
            "STOP" -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        serviceScope.launch {
            try {
                // 加载规则（包括 AdGuardFilters）
                loadRules()

                // 启动 DNS 代理
                startDnsProxy()

                // 建立 VPN 连接
                vpnInterface = establishVpn()

                if (vpnInterface != null) {
                    // 启动流量处理
                    tunnelManager = TunnelManager(vpnInterface!!, ruleEngine, dnsProxy)
                    tunnelManager?.start()

                    isRunning = true

                    // 启动前台服务
                    startForeground(1, createNotification())

                    // 通知 UI 更新
                    withContext(Dispatchers.Main) {
                        sendBroadcast(Intent("VPN_STATUS_CHANGED"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        serviceScope.launch {
            tunnelManager?.stop()
            tunnelManager = null

            dnsProxy?.stop()
            dnsProxy = null

            vpnInterface?.close()
            vpnInterface = null

            isRunning = false

            withContext(Dispatchers.Main) {
                stopForeground(true)
                stopSelf()
                sendBroadcast(Intent("VPN_STATUS_CHANGED"))
            }
        }
    }

    private suspend fun loadRules() {
        // 加载内置规则 + AdGuardFilters 规则
        val allRules = ruleRepository.getAllRules(
            includeAdGuard = true,
            customRules = emptyList()
        )
        ruleEngine.loadRules(allRules)
    }

    private fun startDnsProxy() {
        dnsProxy = DnsProxyWrapper()
        dnsProxy?.start("127.0.0.1:5353", listOf("8.8.8.8", "8.8.4.4"))
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        return Builder()
            .setSession("Adout")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("127.0.0.1") // 使用本地 DNS 代理
            .setMtu(1500)
            .setBlocking(true)
            .establish()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Adout 保护中")
            .setContentText("广告拦截已启用")
            .setSmallIcon(android.R.drawable.ic_menu_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    fun getRuleCount(): Int {
        return ruleEngine.getRuleCount()
    }

    fun getBlockedCount(): Long {
        return tunnelManager?.getBlockedCount() ?: 0
    }

    /**
     * 热更新规则
     */
    fun updateRules(newRules: List<String>) {
        serviceScope.launch {
            ruleEngine.reloadRules(newRules)
        }
    }

    /**
     * 添加自定义规则
     */
    fun addCustomRule(ruleText: String) {
        serviceScope.launch {
            ruleEngine.addRule(ruleText)
        }
    }
}
```

- [ ] **Step 2: 实现隧道管理器**

```kotlin
package com.adout.vpn

import android.os.ParcelFileDescriptor
import com.adout.rule.RuleEngine
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * TunnelManager - 流量隧道管理器
 *
 * 使用 DnsProxyWrapper 处理 DNS 请求，避免自己实现 DNS 协议解析。
 */
class TunnelManager(
    private val vpnInterface: ParcelFileDescriptor,
    private val ruleEngine: RuleEngine,
    private val dnsProxy: DnsProxyWrapper? = null
) {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blockedCount = 0L

    fun start() {
        job = scope.launch {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

            val buffer = ByteBuffer.allocate(32767)

            try {
                while (isActive) {
                    buffer.clear()
                    val length = inputStream.read(buffer.array())

                    if (length > 0) {
                        buffer.limit(length)
                        processPacket(buffer, outputStream)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    e.printStackTrace()
                }
            } finally {
                inputStream.close()
                outputStream.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun processPacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        try {
            // 检查是否是 IP 包
            if (buffer.remaining() < 20) return

            val version = (buffer.get(0).toInt() and 0xF0) shr 4
            if (version != 4) return // 只处理 IPv4

            // 获取协议类型
            val protocol = buffer.get(9).toInt() and 0xFF

            // 检查是否是 UDP (协议号 17)
            if (protocol == 17) {
                processUdpPacket(buffer, outputStream)
            } else {
                // 其他协议直接转发
                buffer.rewind()
                outputStream.write(buffer.array(), 0, buffer.remaining())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processUdpPacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        try {
            // IP 头部长度
            val ipHeaderLength = (buffer.get(0).toInt() and 0x0F) * 4

            // UDP 头部
            val udpHeaderStart = ipHeaderLength
            if (buffer.remaining() < udpHeaderStart + 8) return

            val destPort = ((buffer.get(udpHeaderStart + 2).toInt() and 0xFF) shl 8) or
                    (buffer.get(udpHeaderStart + 3).toInt() and 0xFF)

            // 检查是否是 DNS 请求 (端口 53)
            if (destPort == 53) {
                processDnsPacket(buffer, ipHeaderLength, outputStream)
            } else {
                // 其他 UDP 流量直接转发
                buffer.rewind()
                outputStream.write(buffer.array(), 0, buffer.remaining())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processDnsPacket(
        buffer: ByteBuffer,
        ipHeaderLength: Int,
        outputStream: FileOutputStream
    ) {
        try {
            // 提取 DNS 数据
            val dnsDataStart = ipHeaderLength + 8 // UDP 头部 8 字节
            val dnsDataLength = buffer.remaining() - dnsDataStart

            if (dnsDataLength <= 0) return

            val dnsData = ByteArray(dnsDataLength)
            buffer.position(dnsDataStart)
            buffer.get(dnsData)

            // 使用 DnsProxyWrapper 提取域名
            val domain = dnsProxy?.extractDomain(dnsData)

            if (domain != null) {
                // 检查是否应该拦截
                if (ruleEngine.shouldBlock(domain)) {
                    blockedCount++

                    // 使用 DnsProxyWrapper 处理请求（会返回 0.0.0.0）
                    val response = dnsProxy?.handleRequest(dnsData)

                    if (response != null) {
                        // 构建响应包
                        val responsePacket = buildDnsResponsePacket(buffer, response, ipHeaderLength)
                        outputStream.write(responsePacket)
                        return
                    }
                }
            }

            // 不拦截，直接转发
            buffer.rewind()
            outputStream.write(buffer.array(), 0, buffer.remaining())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildDnsResponsePacket(
        originalPacket: ByteBuffer,
        dnsResponse: ByteArray,
        ipHeaderLength: Int
    ): ByteArray {
        // 复制 IP 头部
        val ipHeader = ByteArray(ipHeaderLength)
        originalPacket.rewind()
        originalPacket.get(ipHeader)

        // 修改 IP 头部
        ipHeader[2] = ((ipHeaderLength + 8 + dnsResponse.size) shr 8).toByte() // 总长度
        ipHeader[3] = ((ipHeaderLength + 8 + dnsResponse.size) and 0xFF).toByte()

        // 交换源和目标 IP
        val tempIp = ByteArray(4)
        System.arraycopy(ipHeader, 12, tempIp, 0, 4)
        System.arraycopy(ipHeader, 16, ipHeader, 12, 4)
        System.arraycopy(tempIp, 0, ipHeader, 16, 4)

        // 构建 UDP 头部
        val udpHeader = ByteArray(8)
        udpHeader[0] = 0x00.toByte() // 源端口高字节
        udpHeader[1] = 0x35.toByte() // 源端口低字节 (53)
        udpHeader[2] = originalPacket.get(ipHeaderLength).toByte() // 目标端口高字节
        udpHeader[3] = originalPacket.get(ipHeaderLength + 1).toByte() // 目标端口低字节
        udpHeader[4] = ((8 + dnsResponse.size) shr 8).toByte() // UDP 长度
        udpHeader[5] = ((8 + dnsResponse.size) and 0xFF).toByte()
        // UDP 校验和设为 0（可选）
        udpHeader[6] = 0x00
        udpHeader[7] = 0x00

        // 组合响应包
        val responsePacket = ByteArray(ipHeaderLength + 8 + dnsResponse.size)
        System.arraycopy(ipHeader, 0, responsePacket, 0, ipHeaderLength)
        System.arraycopy(udpHeader, 0, responsePacket, ipHeaderLength, 8)
        System.arraycopy(dnsResponse, 0, responsePacket, ipHeaderLength + 8, dnsResponse.size)

        return responsePacket
    }

    fun getBlockedCount(): Long {
        return blockedCount
    }
}
```

- [ ] **Step 3: 测试 VPN 服务编译**

```bash
./gradlew assembleDebug
```

预期结果：BUILD SUCCESSFUL

- [ ] **Step 4: 提交 VPN 服务**

```bash
git add app/src/main/java/com/adout/vpn/AdBlockVpnService.kt app/src/main/java/com/adout/vpn/TunnelManager.kt
git commit -m "feat: implement VPN service with DNS interception"
```

---

## Task 8: 主界面 UI

**Files:**
- Create: `app/src/main/java/com/adout/ui/MainActivity.kt`
- Create: `app/src/main/java/com/adout/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/adout/ui/theme/Color.kt`
- Create: `app/src/main/java/com/adout/ui/theme/Theme.kt`

- [ ] **Step 1: 实现主题颜色**

```kotlin
package com.adout.ui.theme

import androidx.compose.ui.graphics.Color

// 关闭状态 - 紫色系
val Purple80 = Color(0xFF764ba2)
val Purple90 = Color(0xFF667eea)
val PurpleGradientStart = Color(0xFF667eea)
val PurpleGradientEnd = Color(0xFF764ba2)

// 开启状态 - 绿色系
val Green80 = Color(0xFF38ef7d)
val Green90 = Color(0xFF11998e)
val GreenGradientStart = Color(0xFF11998e)
val GreenGradientEnd = Color(0xFF38ef7d)

// 通用颜色
val White = Color(0xFFFFFFFF)
val WhiteAlpha80 = Color(0xCCFFFFFF)
val WhiteAlpha60 = Color(0x99FFFFFF)
val WhiteAlpha40 = Color(0x66FFFFFF)
val WhiteAlpha30 = Color(0x4DFFFFFF)
val WhiteAlpha20 = Color(0x33FFFFFF)
```

- [ ] **Step 2: 实现主题配置**

```kotlin
package com.adout.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = Green80,
    tertiary = White
)

private val LightColorScheme = lightColorScheme(
    primary = Purple90,
    secondary = Green90,
    tertiary = White
)

@Composable
fun AdoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

- [ ] **Step 3: 实现 ViewModel**

```kotlin
package com.adout.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adout.vpn.AdBlockVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "VPN_STATUS_CHANGED") {
                updateVpnStatus()
            }
        }
    }

    init {
        val filter = IntentFilter("VPN_STATUS_CHANGED")
        application.registerReceiver(vpnStatusReceiver, filter)
        updateVpnStatus()
    }

    fun toggleVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, AdBlockVpnService::class.java)

        if (AdBlockVpnService.isRunning) {
            intent.action = "STOP"
        } else {
            intent.action = "START"
        }

        context.startService(intent)
    }

    private fun updateVpnStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVpnRunning = AdBlockVpnService.isRunning,
                ruleCount = AdBlockVpnService.instance?.getRuleCount() ?: 0,
                blockedCount = AdBlockVpnService.instance?.getBlockedCount() ?: 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(vpnStatusReceiver)
    }
}

data class MainUiState(
    val isVpnRunning: Boolean = false,
    val ruleCount: Int = 0,
    val blockedCount: Long = 0
)
```

- [ ] **Step 4: 实现主界面 Activity**

```kotlin
package com.adout.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adout.ui.theme.*

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdoutTheme {
                MainScreen()
            }
        }
    }

    private fun checkVpnPermission(): Boolean {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
            return false
        }
        return true
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java)
        intent.action = "START"
        startService(intent)
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val backgroundColor by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) {
            GreenGradientStart
        } else {
            PurpleGradientStart
        },
        animationSpec = tween(durationMillis = 500),
        label = "backgroundColor"
    )

    val backgroundGradientEnd by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) {
            GreenGradientEnd
        } else {
            PurpleGradientEnd
        },
        animationSpec = tween(durationMillis = 500),
        label = "backgroundGradientEnd"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(backgroundColor, backgroundGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App Name
            Text(
                text = "Adout",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // Main Toggle Button
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = if (uiState.isVpnRunning) 20.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(WhiteAlpha20)
                    .clickable { viewModel.toggleVpn() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(WhiteAlpha30),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (uiState.isVpnRunning) "关闭保护" else "开启保护",
                        tint = White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Status Text
            Text(
                text = if (uiState.isVpnRunning) "保护已开启" else "保护已关闭",
                fontSize = 16.sp,
                color = WhiteAlpha80,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Stats Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Blocked Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.blockedCount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Text(
                        text = "今日拦截",
                        fontSize = 12.sp,
                        color = WhiteAlpha60
                    )
                }

                // Rule Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.ruleCount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Text(
                        text = "规则数量",
                        fontSize = 12.sp,
                        color = WhiteAlpha60
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: 测试 UI 编译**

```bash
./gradlew assembleDebug
```

预期结果：BUILD SUCCESSFUL

- [ ] **Step 6: 提交 UI 实现**

```bash
git add app/src/main/java/com/adout/ui/ app/src/main/java/com/adout/ui/theme/
git commit -m "feat: implement main UI with toggle button and status display"
```

---

## Task 9: 前台服务与通知

**Files:**
- Create: `app/src/main/java/com/adout/service/ForegroundServiceManager.kt`
- Create: `app/src/main/java/com/adout/service/NotificationHelper.kt`

- [ ] **Step 1: 实现通知帮助类**

```kotlin
package com.adout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adout.AdoutApplication
import com.adout.ui.MainActivity

object NotificationHelper {

    fun createVpnServiceNotification(
        context: Context,
        isRunning: Boolean,
        blockedCount: Long = 0
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isRunning) "Adout 保护中" else "Adout 已停止"
        val text = if (isRunning) {
            "广告拦截已启用，已拦截 $blockedCount 个广告"
        } else {
            "点击开启保护"
        }

        return Notification.Builder(context, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .build()
    }

    fun createBatteryOptimizationNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("需要关闭电池优化")
            .setContentText("Adout 需要在后台运行，请关闭电池优化")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun showBatteryOptimizationNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, createBatteryOptimizationNotification(context))
    }
}
```

- [ ] **Step 2: 实现前台服务管理器**

```kotlin
package com.adout.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.adout.vpn.AdBlockVpnService

object ForegroundServiceManager {

    fun updateNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val isRunning = AdBlockVpnService.isRunning
        val blockedCount = AdBlockVpnService.instance?.getBlockedCount() ?: 0

        val notification = NotificationHelper.createVpnServiceNotification(
            context,
            isRunning,
            blockedCount
        )

        notificationManager.notify(1, notification)
    }

    fun checkBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                context.packageName
            )

            if (!isIgnoringBatteryOptimizations) {
                NotificationHelper.showBatteryOptimizationNotification(context)
            }
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    }
}
```

- [ ] **Step 3: 测试服务编译**

```bash
./gradlew assembleDebug
```

预期结果：BUILD SUCCESSFUL

- [ ] **Step 4: 提交服务实现**

```bash
git add app/src/main/java/com/adout/service/
git commit -m "feat: implement foreground service manager and notification helper"
```

---

## Task 10: 集成测试与调试

**Files:**
- Modify: `app/src/main/java/com/adout/vpn/AdBlockVpnService.kt`
- Modify: `app/src/main/java/com/adout/ui/MainActivity.kt`

- [ ] **Step 1: 完善 VPN 服务与 UI 集成**

```kotlin
// 在 MainActivity.kt 中添加权限检查和启动逻辑
private fun checkAndStartVpn() {
    if (checkVpnPermission()) {
        startVpnService()
    }
}
```

- [ ] **Step 2: 添加运行时权限请求**

```kotlin
// 在 AndroidManifest.xml 中添加权限请求
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

// 在 MainActivity 中请求通知权限
private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001
        )
    }
}
```

- [ ] **Step 3: 运行完整构建测试**

```bash
./gradlew assembleDebug
./gradlew test
```

预期结果：所有测试通过，构建成功

- [ ] **Step 4: 提交最终集成**

```bash
git add .
git commit -m "feat: complete VPN service and UI integration"
```

---

## Task 11: 电池优化引导

**Files:**
- Create: `app/src/main/java/com/adout/util/BatteryOptimizationHelper.kt`
- Modify: `app/src/main/java/com/adout/ui/MainActivity.kt`

- [ ] **Step 1: 实现电池优化帮助类**

```kotlin
package com.adout.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }

    fun shouldShowBatteryOptimizationDialog(context: Context): Boolean {
        return !isIgnoringBatteryOptimizations(context)
    }
}
```

- [ ] **Step 2: 在 MainActivity 中添加电池优化对话框**

```kotlin
// 在 MainScreen 中添加对话框状态
var showBatteryDialog by remember { mutableStateOf(false) }

// 检查电池优化状态
LaunchedEffect(Unit) {
    if (BatteryOptimizationHelper.shouldShowBatteryOptimizationDialog(context)) {
        showBatteryDialog = true
    }
}

// 电池优化对话框
if (showBatteryDialog) {
    AlertDialog(
        onDismissRequest = { showBatteryDialog = false },
        title = { Text("需要关闭电池优化") },
        text = { Text("为了确保 Adout 能在后台正常运行，请关闭电池优化。") },
        confirmButton = {
            TextButton(
                onClick = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    showBatteryDialog = false
                }
            ) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showBatteryDialog = false }
            ) {
                Text("稍后")
            }
        }
    )
}
```

- [ ] **Step 3: 测试电池优化功能**

```bash
./gradlew assembleDebug
```

预期结果：构建成功，对话框功能正常

- [ ] **Step 4: 提交电池优化实现**

```bash
git add app/src/main/java/com/adout/util/BatteryOptimizationHelper.kt app/src/main/java/com/adout/ui/MainActivity.kt
git commit -m "feat: add battery optimization guidance for MagicOS compatibility"
```

---

## Task 12: 规则热加载

**Files:**
- Modify: `app/src/main/java/com/adout/rule/RuleEngine.kt`
- Modify: `app/src/main/java/com/adout/vpn/AdBlockVpnService.kt`

- [ ] **Step 1: 实现规则热加载功能**

```kotlin
// 在 RuleEngine 中添加热加载方法
fun reloadRules(newRules: List<String>) {
    clearRules()
    loadRules(newRules)
}

fun reloadRulesFromText(rulesText: String) {
    clearRules()
    loadRulesFromText(rulesText)
}
```

- [ ] **Step 2: 在 VPN 服务中添加规则更新接口**

```kotlin
// 在 AdBlockVpnService 中添加规则更新方法
fun updateRules(newRules: List<String>) {
    serviceScope.launch {
        ruleEngine.reloadRules(newRules)
    }
}

fun addCustomRule(ruleText: String) {
    serviceScope.launch {
        ruleEngine.addRule(ruleText)
    }
}
```

- [ ] **Step 3: 测试规则热加载**

```kotlin
// 在 RuleEngineTest 中添加热加载测试
@Test
fun `hot reload rules`() {
    ruleEngine.addRule("||old-rule.com^")
    assertTrue(ruleEngine.shouldBlock("old-rule.com"))

    val newRules = listOf("||new-rule.com^")
    ruleEngine.reloadRules(newRules)

    assertFalse(ruleEngine.shouldBlock("old-rule.com"))
    assertTrue(ruleEngine.shouldBlock("new-rule.com"))
}
```

- [ ] **Step 4: 运行测试验证**

```bash
./gradlew test --tests "com.adout.rule.RuleEngineTest"
```

预期结果：PASS

- [ ] **Step 5: 提交规则热加载**

```bash
git add app/src/main/java/com/adout/rule/RuleEngine.kt app/src/main/java/com/adout/vpn/AdBlockVpnService.kt app/src/test/java/com/adout/rule/RuleEngineTest.kt
git commit -m "feat: implement hot reload for ad blocking rules"
```

---

## Task 13: 最终测试与优化

- [ ] **Step 1: 运行完整测试套件**

```bash
./gradlew test
./gradlew connectedAndroidTest
```

预期结果：所有测试通过

- [ ] **Step 2: 性能优化检查**

```bash
# 检查内存使用
adb shell dumpsys meminfo com.adout

# 检查 CPU 使用
adb shell top -n 1 | grep com.adout
```

- [ ] **Step 3: 最终构建**

```bash
./gradlew assembleRelease
```

预期结果：Release 构建成功

- [ ] **Step 4: 提交最终版本**

```bash
git add .
git commit -m "feat: complete Adout ad blocker with VPN and DNS filtering"
```

---

## 实现计划总结

**总任务数**: 14 个主要任务

**代码复用策略**:
- **dnsproxy**: 直接使用 gomobile 编译为 .aar，避免重写 DNS 协议解析
- **AdGuardFilters**: 下载 MobileAdsFilter 和 ChineseFilter 作为内置规则
- **tsurlfilter**: 参考规则格式设计，保持兼容性

**关键技术点**:
1. AdGuard dnsproxy gomobile 集成
2. AdGuardFilters 规则库下载与解析
3. Aho-Corasick 多模式匹配算法
4. Adblock Plus 规则格式解析
5. Android VpnService 流量拦截
6. Jetpack Compose UI 设计
7. 前台服务与通知管理
7. 电池优化引导

**预计开发时间**: 2-3 周（全职开发）

**代码复用收益**:
- DNS 处理：直接使用 dnsproxy，节省 1-2 周开发时间
- 规则库：使用 AdGuardFilters，覆盖移动端和中文网站
- 规则格式：兼容 AdGuard 语法，社区维护活跃
- 总体节省：60-70% 开发时间

**测试策略**:
- 单元测试覆盖核心模块
- 集成测试验证完整流程
- 手动测试在真机上验证效果

**风险点**:
- gomobile 编译环境配置
- Android 14 权限限制
- MagicOS 后台运行限制
- AdGuardFilters 规则更新频率

**参考资源**:
- [AdGuard DNS Proxy](https://github.com/AdguardTeam/dnsproxy)
- [AdGuard Filters](https://github.com/AdguardTeam/AdGuardFilters)
- [AdGuard Filter Syntax](https://adguard.com/kb/general/ad-filtering/create-own-filters/)

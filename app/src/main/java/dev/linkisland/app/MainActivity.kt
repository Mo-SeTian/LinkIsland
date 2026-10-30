package dev.linkisland.app

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf("home")
    private var mode by mutableIntStateOf(0)
    private var running by mutableStateOf(false)
    private var connecting by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var capture by mutableStateOf("")
    private var rules by mutableStateOf(LinkRules.DEFAULTS)
    private var island by mutableStateOf(true)
    private var device by mutableStateOf("正在查询…")
    private var shizukuPrompt by mutableStateOf(false)
    private var permissions by mutableStateOf(emptyList<PermissionItem>())
    private var browsers by mutableStateOf(emptyList<BrowserAccess.Browser>())
    private var browserChoice by mutableStateOf("")
    private var browserLabel by mutableStateOf("跟随系统默认")
    private var hideRecent by mutableStateOf(false)
    private var autoRestart by mutableStateOf(true)
    private var privilegedOpen by mutableStateOf(false)
    private var checkingRoot = false
    private val preferences by lazy { Prefs.get(this) }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (result == PackageManager.PERMISSION_GRANTED && requestCode == 22) startMonitor()
        else if (result != PackageManager.PERMISSION_GRANTED) toast("Shizuku 未授权")
        refreshPermissions()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestStart() else toast("需要通知权限才能显示跳转提示")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.ensureDefaultRules(this)
        screen = savedInstanceState?.getString("screen") ?: "home"
        refresh()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        LinkNotifications.channels(this)
        setContent {
            LinkIslandUi(
                screen = screen, mode = mode, running = running, connecting = connecting,
                status = status, capture = capture, rulesJson = rules, island = island,
                browsers = browsers, browserChoice = browserChoice, browserLabel = browserLabel,
                hideRecent = hideRecent, autoRestart = autoRestart, privilegedOpen = privilegedOpen,
                onHideRecent = { value -> preferences.edit().putBoolean("hide_recent", value).apply(); applyTaskVisibility() },
                onAutoRestart = { value ->
                    preferences.edit().putBoolean("auto_restart", value).apply()
                    if (MonitorService.running) startService(Intent(this, MonitorService::class.java).setAction("OPTIONS"))
                },
                onPrivilegedOpen = { value -> preferences.edit().putBoolean("privileged_open", value).apply() },
                onBrowser = { choice -> preferences.edit().putString("browser", choice).apply(); refreshBrowsers() },
                device = device, shizukuPrompt = shizukuPrompt, permissions = permissions,
                onPermission = ::managePermission, onRefreshPermissions = ::refreshPermissions,
                onNavigate = ::navigate,
                onMode = { selected ->
                    if (running) toast("切换授权方式前，请先关闭监听")
                    else preferences.edit().putInt("mode", selected).apply()
                },
                onToggle = {
                    if (running) {
                        MonitorService.stopListening(this)
                        Prefs.status(this, "监听已停止")
                    } else requestStart()
                },
                onTest = ::testLink,
                onIsland = { preferences.edit().putBoolean("island", it).apply() },
                onProbe = {
                    if (running) startService(Intent(this, MonitorService::class.java).setAction("CHECK"))
                    else toast("请先开启监听")
                },
                onNotificationSettings = {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                },
                onRefresh = ::queryDevice,
                onSaveRule = ::saveRule,
                onDeleteRule = ::deleteRule,
                installedApps = ::installedApps,
                onShizukuDismiss = { shizukuPrompt = false },
                onOpenShizuku = {
                    shizukuPrompt = false
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        ?.let(::startActivity) ?: toast("未安装 Shizuku")
                }
            )
        }
        applyTaskVisibility()
        if (screen == "settings") queryDevice()
        if (screen == "permissions") refreshPermissions()
        if (screen == "settings" || screen == "browsers") refreshBrowsers()
    }

    private fun applyTaskVisibility() {
        getSystemService(android.app.ActivityManager::class.java).appTasks.forEach { task ->
            if (task.taskInfo.baseActivity?.className == MainActivity::class.java.name)
                task.setExcludeFromRecents(preferences.getBoolean("hide_recent", false))
        }
    }

    private fun refresh() {
        hideRecent = preferences.getBoolean("hide_recent", false)
        autoRestart = preferences.getBoolean("auto_restart", true)
        privilegedOpen = preferences.getBoolean("privileged_open", false)
        mode = preferences.getInt("mode", 0)
        running = MonitorService.running
        status = preferences.getString("status", "") ?: ""
        capture = if (running && MonitorService.diagnostics.isNotEmpty()) MonitorService.diagnostics
            else preferences.getString("clipboard_diagnostics", "") ?: ""
        connecting = running && (capture == "等待权限服务连接…" || status.contains("正在连接权限服务"))
        rules = Prefs.rules(this)
        island = preferences.getBoolean("island", true)
    }
    private fun navigate(destination: String) {
        screen = destination
        if (destination == "settings") queryDevice()
        if (destination == "permissions") refreshPermissions()
        if (destination == "settings" || destination == "browsers") refreshBrowsers()
    }
    private fun refreshBrowsers() {
        browsers = BrowserAccess.installed(this)
        browserChoice = BrowserAccess.selection(this)
        browserLabel = when (browserChoice) {
            BrowserAccess.ASK -> "每次询问"
            "" -> "跟随系统默认"
            else -> browsers.firstOrNull { it.packageName == browserChoice }?.label ?: "所选浏览器不可用，跟随系统默认"
        }
    }
    private fun refreshPermissions() {
        permissions = PermissionAccess.read(this, mode, checkingRoot)
    }
    private fun managePermission(key: String) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        when (key) {
            "notifications" -> openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName), details)
            "prompt" -> openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, LinkNotifications.PROMPTS), details)
            "battery" -> {
                val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) openSettings(list, details)
                else openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")), list, details)
            }
            "autostart" -> openSettings(Intent().setComponent(ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")), details)
            "background" -> if (PermissionAccess.isXiaomi()) {
                openSettings(Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", packageName).putExtra("package_label", applicationInfo.loadLabel(packageManager).toString()), details)
            } else openSettings(details)
            "bridge" -> if (mode == 0) {
                if (!Shizuku.pingBinder()) shizukuPrompt = true
                else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(23)
                else packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { openSettings(it) }
                    ?: toast("未安装 Shizuku")
            } else if (!checkingRoot) {
                checkingRoot = true
                refreshPermissions()
                Shell.getShell { _ -> runOnUiThread {
                    checkingRoot = false
                    if (!isDestroyed) refreshPermissions()
                } }
            }
        }
    }
    private fun openSettings(vararg intents: Intent) {
        for ((index, intent) in intents.withIndex()) {
            try {
                startActivity(intent)
                if (index > 0) toast("专用设置页不可用，已打开系统设置，请手动检查")
                return
            } catch (_: ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
        toast("无法打开设置，请在系统应用信息中检查权限")
    }
    private fun requestStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (mode == 0) {
            if (!Shizuku.pingBinder()) { shizukuPrompt = true; return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(22)
                return
            }
        }
        startMonitor()
    }
    private fun startMonitor() {
        preferences.edit().putBoolean("monitor_wanted", true).putLong("monitor_started_at", System.currentTimeMillis()).commit()
        startForegroundService(Intent(this, MonitorService::class.java).putExtra("mode", if (mode == 0) "shizuku" else "root"))
    }
    private fun testLink(text: String): String? {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            return "允许通知后，请再次发送测试提示"
        }
        val url = LinkRules.extract(text) ?: return "请输入有效的 HTTP / HTTPS 链接"
        return try {
            LinkRules.resolve(url, LinkRules.parse(rules))
            LinkNotifications.show(this, url)
            toast("已发送，请查看超级岛或通知栏")
            null
        } catch (_: Exception) { "规则无法处理这个链接，请检查改写模板" }
    }
    private fun saveRule(index: Int, rule: JSONObject): String? {
        if (rule.optString("pattern").isBlank()) return "匹配内容不能为空"
        return try {
            val all = JSONArray(rules)
            if (index < 0) all.put(rule) else all.put(index, rule)
            LinkRules.parse(all.toString())
            preferences.edit().putString("rules", all.toString()).apply()
            navigate("rules")
            null
        } catch (_: Exception) { "请检查匹配内容、正则语法和目标 App 包名" }
    }
    private fun deleteRule(index: Int) {
        val all = JSONArray(rules)
        all.remove(index)
        preferences.edit().putString("rules", all.toString()).apply()
        navigate("rules")
    }
    private fun installedApps(): List<Pair<String, String>> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .sortedBy { it.first }

    private fun queryDevice() {
        device = "正在查询…"
        Thread({
            val protocol = Settings.System.getInt(contentResolver, "notification_focus_protocol", 0)
            val focus = try {
                val extras = Bundle().apply { putString("package", packageName) }
                val response = contentResolver.call(Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null, extras)
                if (response == null || !response.containsKey("canShowFocus")) "未知（系统未返回）"
                else if (response.getBoolean("canShowFocus")) "允许" else "未允许"
            } catch (e: Exception) { "无法查询 · ${ClipboardBridge.causeName(e)}" }
            val shizuku = try {
                if (Shizuku.pingBinder()) "在线 · UID ${Shizuku.getUid()}" else "未连接"
            } catch (_: RuntimeException) { "不可用" }
            val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            val text = "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}" +
                "\n系统构建：${Build.DISPLAY}\n通知权限：${if (enabled) "允许" else "关闭"}" +
                "\n焦点通知协议：$protocol\n焦点通知权限：$focus\nShizuku：$shizuku"
            runOnUiThread { if (!isDestroyed) device = text }
        }, "device-diagnostics").start()
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    override fun onResume() {
        MonitorService.observeDiagnostics { refresh() }
        super.onResume(); refresh(); applyTaskVisibility()
        if (!MonitorService.running && MonitorPolicy.shouldResume(preferences.getBoolean("monitor_wanted", false), autoRestart)) {
            try { startForegroundService(Intent(this, MonitorService::class.java).setAction("RESUME")) }
            catch (_: RuntimeException) { Prefs.status(this, "自动恢复启动失败 · 请点击开启监听重试") }
        }
        if (screen == "settings") queryDevice()
        if (screen == "permissions") refreshPermissions()
        if (screen == "settings" || screen == "browsers") refreshBrowsers()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("screen", screen); super.onSaveInstanceState(outState) }
    override fun onPause() {
        MonitorService.observeDiagnostics(null)
        super.onPause()
    }
    override fun onDestroy() {
        if (isFinishing && !MonitorService.running) PromptSafety.cancel(this)
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}

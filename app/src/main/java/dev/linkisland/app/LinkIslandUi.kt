package dev.linkisland.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val Accent = Color(0xFF287EFF)
private val PageBackground = Color(0xFFF5F6F8)
internal data class RulePreview(val rule: String, val app: String, val packageName: String,
    val original: String, val target: String, val capability: String)

@Composable
internal fun LinkIslandUi(
    screen: String, mode: Int, running: Boolean, connecting: Boolean,
    status: String, capture: String, rulesJson: String, island: Boolean,
    hideRecent: Boolean, autoRestart: Boolean, privilegedOpen: Boolean,
    promptSeconds: Int, onPromptSeconds: (Int) -> Unit, jumpLog: String, onClearLog: () -> Unit,
    onHideRecent: (Boolean) -> Unit, onAutoRestart: (Boolean) -> Unit, onPrivilegedOpen: (Boolean) -> Unit,
    browsers: List<BrowserAccess.Browser>, browserChoice: String, browserLabel: String, onBrowser: (String) -> Unit,
    device: String, shizukuPrompt: Boolean, permissions: List<PermissionItem>,
    onPermission: (String) -> Unit, onRefreshPermissions: () -> Unit,
    onNavigate: (String) -> Unit, onMode: (Int) -> Unit, onToggle: () -> Unit,
    onTest: (String) -> String?, onPreview: (String) -> RulePreview,
    backupBusy: Boolean, backupMessage: String, pendingBackup: JSONObject?,
    onExport: () -> Unit, onImport: () -> Unit, onApplyBackup: (Boolean) -> String?, onCancelBackup: () -> Unit,
    onIsland: (Boolean) -> Unit,
    onProbe: () -> Unit, onNotificationSettings: () -> Unit, onRefresh: () -> Unit,
    onSaveRule: (Int, JSONObject) -> String?, onDeleteRule: (Int) -> Unit,
    installedApps: () -> List<Pair<String, String>>,
    onShizukuDismiss: () -> Unit, onOpenShizuku: () -> Unit
) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Accent,
        background = Color(0xFF101012), surface = Color(0xFF101012), surfaceContainer = Color(0xFF1D1D20))
    else lightColorScheme(primary = Accent, surface = PageBackground, background = PageBackground, surfaceContainer = Color.White)
    MiuixTheme(colors = colors) {
        BackHandler(screen != "home") {
            onNavigate(if (screen.startsWith("edit:")) "rules" else if (screen in listOf("permissions", "browsers", "diagnostics", "backup")) "settings" else "home")
        }
        Scaffold(containerColor = colors.background) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
                Crossfade(targetState = screen, label = "page") { page ->
                    when {
                        page == "home" -> HomePage(running, connecting, status, rulesJson, onNavigate, onToggle)
                        page == "rules" -> RulesPage(rulesJson, onNavigate)
                        page.startsWith("edit:") -> {
                            val index = page.substringAfter(":").toInt()
                            val initial = JSONArray(rulesJson).optJSONObject(index) ?: JSONObject()
                            RuleEditor(index, initial, { onNavigate("rules") }, onSaveRule, onDeleteRule, installedApps)
                        }
                        page == "test" -> TestPage(island, promptSeconds, onIsland, onTest, onPreview, { onNavigate("home") })
                        page == "browsers" -> BrowserPage(browsers, browserChoice, onBrowser, { onNavigate("settings") })
                        page == "permissions" -> PermissionsPage(permissions, onPermission, onRefreshPermissions, { onNavigate("settings") })
                        page == "diagnostics" -> DiagnosticsPage(running, status, capture, device, onProbe, onRefresh, { onNavigate("settings") })
                        page == "logs" -> LogsPage(jumpLog, onClearLog, { onNavigate("home") })
                        page == "backup" -> BackupPage(rulesJson, backupBusy, backupMessage, pendingBackup, onExport, onImport, onApplyBackup, onCancelBackup, { onNavigate("settings") })
                        else -> SettingsPage(hideRecent, autoRestart, privilegedOpen, onHideRecent, onAutoRestart, onPrivilegedOpen, browserLabel, { onNavigate("browsers") }, mode, onMode, running, island, onIsland, promptSeconds, onPromptSeconds,
                            onNotificationSettings, { onNavigate("diagnostics") }, { onNavigate("permissions") }, { onNavigate("backup") }, { onNavigate("home") })
                    }
                }
                SuperDialog(show = shizukuPrompt, title = "请先启动 Shizuku",
                    summary = "通过无线调试或 Root 启动 Shizuku 服务，再返回链接岛开启监听。",
                    onDismissRequest = onShizukuDismiss) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton("稍后", onShizukuDismiss, Modifier.weight(1f))
                        TextButton("打开 Shizuku", onOpenShizuku, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(running: Boolean, connecting: Boolean, status: String, rules: String,
    navigate: (String) -> Unit, onToggle: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 740.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(if (compact) 20.dp else 42.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("链接岛", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { navigate("settings") }, modifier = Modifier.size(44.dp)) {
                        Icon(MiuixIcons.Settings, "设置", Modifier.size(24.dp))
                    }
                }
                Text("复制后，让链接找到对应应用", fontSize = 14.sp, color = muted(), modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(if (compact) 32.dp else 60.dp))
                val action = if (running) "关闭监听" else "开启监听"
                val diameter = if (compact) 112.dp else 128.dp
                Box(Modifier.fillMaxWidth().height(diameter), contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(diameter + 16.dp).blur(28.dp, BlurredEdgeTreatment.Unbounded)
                        .background(Accent.copy(alpha = .2f), CircleShape))
                    Button(
                        onClick = onToggle, enabled = true,
                        modifier = Modifier.size(diameter)
                            .shadow(24.dp, CircleShape, ambientColor = Accent.copy(alpha = .25f), spotColor = Accent.copy(alpha = .25f))
                            .clip(CircleShape)
                            .semantics {
                                contentDescription = action
                                stateDescription = if (connecting) "正在连接" else if (running) "已开启" else "未开启"
                            },
                        cornerRadius = diameter / 2,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(color = Accent, disabledColor = Accent,
                            contentColor = Color.White, disabledContentColor = Color.White)
                    ) {
                        if (connecting) CircularProgressIndicator(size = 40.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = Color.White, backgroundColor = Color.White.copy(alpha = .2f)))
                        else Icon(painterResource(R.drawable.ic_power), null, Modifier.size(52.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (connecting) "正在连接权限服务" else if (running) "监听已开启" else "监听未开启",
                    Modifier.fillMaxWidth(), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                val lastOperationFailed = listOf("失败", "超时", "未授权", "不可用").any { status.contains(it) }
                Text(if (connecting) "连接中，可点击上方按钮停止" else if (running) "复制链接后，将提示是否打开"
                    else if (lastOperationFailed) "上次操作未成功，可在设置中查看诊断" else "点击上方按钮开启",
                    Modifier.fillMaxWidth().padding(top = 8.dp), color = muted(), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                Card(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    SuperArrow(title = "匹配规则", summary = "按关键词或域名匹配",
                        endActions = { Text("${JSONArray(rules).length()} 条", color = muted(), fontSize = 14.sp) },
                        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp), onClick = { navigate("rules") })
                    RowDivider()
                    SuperArrow(title = "链接测试", summary = "检查提示与跳转",
                        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp), onClick = { navigate("test") })
                    RowDivider()
                    SuperArrow(title = "跳转日志", summary = "查看识别与跳转记录",
                        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp), onClick = { navigate("logs") })
                }
                Spacer(Modifier.height(24.dp))
            }
            Text("本地匹配，不记录剪贴板历史", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp),
                fontSize = 12.sp, color = muted(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RulesPage(json: String, navigate: (String) -> Unit) {
    val rules = remember(json) { JSONArray(json).let { array -> List(array.length()) { array.getJSONObject(it) } } }
    DetailPage("匹配规则", { navigate("home") }, action = {
        IconButton(onClick = { navigate("edit:-1") }, modifier = Modifier.size(48.dp)) {
            Icon(MiuixIcons.Add, "添加规则", tint = Accent)
        }
    }) {
        Note("按列表顺序匹配，第一条命中生效。\n未命中的网页链接使用设置中选择的浏览器。")
        Spacer(Modifier.height(18.dp))
        if (rules.isEmpty()) {
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                Text("还没有匹配规则", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Note("添加一条规则，指定链接应交给哪个 App。")
            }
        } else Card(Modifier.fillMaxWidth()) {
            rules.forEachIndexed { index, rule ->
                val context = LocalContext.current
                val packageName = rule.optString("package")
                val appIcon = remember(packageName) { TargetAppIcon.load(context, packageName).asImageBitmap() }
                SuperArrow(title = rule.optString("name").ifBlank { "自定义规则" },
                    startAction = { Image(appIcon, null, Modifier.padding(end = 12.dp).size(36.dp)) },
                    summary = rule.optString("description").ifBlank { typeLabel(rule.optString("type")) + " · " + rule.optString("pattern") },
                    endActions = { Text(if (rule.optBoolean("enabled", true)) "已启用" else "停用",
                        color = if (rule.optBoolean("enabled", true)) Accent else muted(), fontSize = 12.sp) },
                    onClick = { navigate("edit:$index") })
                if (index < rules.lastIndex) RowDivider()
            }
        }
        Spacer(Modifier.height(20.dp))
        TextButton("添加规则", { navigate("edit:-1") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable
private fun RuleEditor(index: Int, initial: JSONObject, back: () -> Unit,
    save: (Int, JSONObject) -> String?, delete: (Int) -> Unit, installedApps: () -> List<Pair<String, String>>) {
    var name by rememberSaveable { mutableStateOf(initial.optString("name")) }
    val types = listOf("domain", "contains", "regex")
    var type by rememberSaveable { mutableIntStateOf(types.indexOf(initial.optString("type", "contains")).coerceAtLeast(0)) }
    var pattern by rememberSaveable { mutableStateOf(initial.optString("pattern")) }
    var pkg by rememberSaveable { mutableStateOf(initial.optString("package")) }
    var replacement by rememberSaveable { mutableStateOf(initial.optString("replacement")) }
    var enabled by rememberSaveable { mutableStateOf(initial.optBoolean("enabled", true)) }
    var error by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }
    var appSearch by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    DetailPage(if (index < 0) "添加规则" else "编辑规则", back, action = {
        TextButton("保存", {
            val rule = JSONObject().put("name", name.trim().ifBlank { "自定义规则" })
                .put("type", types[type]).put("pattern", pattern.trim()).put("package", pkg.trim())
                .put("replacement", replacement.trim()).put("enabled", enabled)
            error = save(index, rule) ?: ""
        }, colors = ButtonDefaults.textButtonColorsPrimary())
    }) {
        Field("规则名称", name, { name = it })
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperDropdown(items = listOf("域名完全匹配", "包含关键词", "正则表达式"),
                selectedIndex = type, title = "匹配方式", onSelectedIndexChange = { type = it })
        }
        Spacer(Modifier.height(16.dp))
        Field(if (type == 0) "域名，例如 github.com" else if (type == 1) "要匹配的关键词" else "正则表达式",
            pattern, { pattern = it })
        Spacer(Modifier.height(24.dp))
        Caption("跳转目标")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperArrow(title = "选择目标 App", summary = if (pkg.isBlank()) "使用设置中选择的浏览器" else pkg, onClick = { appSearch = ""; picker = true })
        }
        Spacer(Modifier.height(12.dp))
        Field("App 包名，留空使用浏览器", pkg, { pkg = it })
        Spacer(Modifier.height(16.dp))
        Field("改写后的链接（可选）", replacement, { replacement = it }, singleLine = false)
        Note("留空保留原链接。域名与关键词模式可使用 {url}；正则模式可使用 $1、$2 捕获组。")
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperSwitch(checked = enabled, onCheckedChange = { enabled = it }, title = "启用规则")
        }
        if (error.isNotEmpty()) Text(error, Modifier.padding(top = 16.dp), color = MiuixTheme.colorScheme.error, fontSize = 14.sp)
        if (index >= 0) {
            Spacer(Modifier.height(24.dp))
            TextButton("删除规则", { confirmDelete = true }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error))
        }
    }
    SuperDialog(show = picker, title = "选择目标 App", onDismissRequest = { picker = false }) {
        val apps = remember(picker) { if (picker) installedApps() else emptyList() }
        Field("搜索应用名称或包名", appSearch, { appSearch = it })
        Spacer(Modifier.height(12.dp))
        val filtered = apps.filter { it.first.contains(appSearch.trim(), ignoreCase = true) || it.second.contains(appSearch.trim(), ignoreCase = true) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            item {
                SuperArrow(title = "网页浏览器", summary = "使用设置中选择的浏览器", onClick = { pkg = ""; picker = false })
            }
            if (filtered.isEmpty()) item { Text("没有匹配的应用", Modifier.padding(20.dp), color = muted()) }
            items(filtered, key = { it.second }) { (label, packageName) ->
                SuperArrow(title = label, summary = packageName, onClick = { pkg = packageName; picker = false })
            }
        }
        TextButton("取消", { picker = false }, Modifier.fillMaxWidth())
    }
    SuperDialog(show = confirmDelete, title = "删除这条规则？", summary = "删除后，该链接将继续匹配其他规则或使用所选浏览器。",
        onDismissRequest = { confirmDelete = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { confirmDelete = false }, Modifier.weight(1f))
            TextButton("删除", { confirmDelete = false; delete(index) }, Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error))
        }
    }
}

@Composable
private fun TestPage(island: Boolean, seconds: Int, onIsland: (Boolean) -> Unit, onTest: (String) -> String?, onPreview: (String) -> RulePreview, back: () -> Unit) {
    var link by rememberSaveable { mutableStateOf("https://github.com/LinkSheet/LinkSheet") }
    var error by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<RulePreview?>(null) }
    DetailPage("链接测试", back) {
        Text("先检查一次跳转", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Note("输入链接，测试提示和目标 App。\n无需开启后台监听。")
        Spacer(Modifier.height(24.dp))
        Field("输入或粘贴网页链接", link, { link = it; error = ""; preview = null }, singleLine = false, url = true)
        Spacer(Modifier.height(12.dp))
        TextButton("预览命中结果", {
            error = ""
            preview = try { onPreview(link) } catch (e: IllegalArgumentException) { error = e.message ?: "无法预览此链接"; null }
        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
        Note("预览只在本机计算，不发送通知、不跳转、不联网。")
        preview?.let { result ->
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("命中结果", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(result.rule, fontSize = 14.sp)
                        Text("目标：${result.app}" + if (result.packageName.isEmpty()) "" else "\n${result.packageName}", fontSize = 14.sp)
                        Text(result.capability, color = Accent, fontSize = 13.sp)
                        Caption("识别到的链接")
                        Text(result.original, fontSize = 13.sp, lineHeight = 19.sp)
                        Caption("改写后的目标")
                        Text(result.target, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperSwitch(checked = island, onCheckedChange = onIsland, title = "小米超级岛",
                summary = "请求原生超级岛展示（实验）")
        }
        if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), color = MiuixTheme.colorScheme.error, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        TextButton("发送测试提示", { error = onTest(link) ?: "" }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary())
        Note("提示 ${seconds} 秒后消失，可在设置中调整。若系统未允许上岛，可能显示为普通通知。")
    }
}

@Composable
private fun BrowserPage(browsers: List<BrowserAccess.Browser>, choice: String, select: (String) -> Unit, back: () -> Unit) {
    val available = browsers.any { it.packageName == choice }
    DetailPage("浏览器选择", back) {
        Caption("打开网页的方式")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperArrow(title = "跟随系统默认", summary = "使用手机设置的默认浏览器",
                endActions = { if (choice.isEmpty() || (choice != BrowserAccess.ASK && !available)) Text("已选择", color = Accent, fontSize = 12.sp) },
                onClick = { select("") })
            RowDivider()
            SuperArrow(title = "每次询问", summary = "打开网页时选择浏览器",
                endActions = { if (choice == BrowserAccess.ASK) Text("已选择", color = Accent, fontSize = 12.sp) },
                onClick = { select(BrowserAccess.ASK) })
        }
        Spacer(Modifier.height(24.dp))
        Caption("已安装的浏览器")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            if (browsers.isEmpty()) Text("没有检测到可用浏览器", Modifier.padding(20.dp), color = muted())
            browsers.forEachIndexed { index, browser ->
                val context = LocalContext.current
                val icon = remember(browser.packageName) { TargetAppIcon.load(context, browser.packageName).asImageBitmap() }
                SuperArrow(title = browser.label, summary = browser.packageName,
                    startAction = { Image(icon, null, Modifier.padding(end = 12.dp).size(36.dp)) },
                    endActions = { if (choice == browser.packageName) Text("已选择", color = Accent, fontSize = 12.sp) },
                    onClick = { select(browser.packageName) })
                if (index < browsers.lastIndex) RowDivider()
            }
        }
        Note("此选择只影响链接岛打开网页。规则指定的目标 App 优先；未匹配规则或改用浏览器时使用此设置。")
        if (choice.isNotEmpty() && choice != BrowserAccess.ASK && !available)
            Note("之前选择的浏览器已不可用，暂时跟随系统默认。")
    }
}

@Composable
private fun SettingsPage(hideRecent: Boolean, autoRestart: Boolean, privilegedOpen: Boolean,
    onHideRecent: (Boolean) -> Unit, onAutoRestart: (Boolean) -> Unit, onPrivilegedOpen: (Boolean) -> Unit,
    browserLabel: String, chooseBrowser: () -> Unit, mode: Int, onMode: (Int) -> Unit, running: Boolean,
    island: Boolean, onIsland: (Boolean) -> Unit, seconds: Int, onSeconds: (Int) -> Unit,
    notificationSettings: () -> Unit, diagnostics: () -> Unit, managePermissions: () -> Unit, backup: () -> Unit, back: () -> Unit) {
    var about by remember { mutableStateOf(false) }
    DetailPage("设置", back) {
        Caption("后台监听")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperDropdown(items = listOf("Shizuku", "Root"), selectedIndex = mode, title = "授权方式",
                summary = if (running) "请先关闭监听，再切换授权方式" else "用于读取复制的链接",
                enabled = !running, onSelectedIndexChange = onMode)
            RowDivider()
            SuperSwitch(checked = hideRecent, onCheckedChange = onHideRecent, title = "隐藏最近任务",
                summary = "隐藏任务卡片，监听仍可运行；从桌面图标返回")
            RowDivider()
            SuperSwitch(checked = autoRestart, onCheckedChange = onAutoRestart, title = "监听自动恢复",
                summary = "重新打开时恢复已开启的监听，断线或心跳超时后重连；手动停止后不恢复")
            RowDivider()
            SuperSwitch(checked = privilegedOpen, onCheckedChange = onPrivilegedOpen, title = "使用授权服务跳转",
                summary = "点击打开时通过 Root / Shizuku 跳转，尝试减少系统二次确认；需开启监听")
        }
        Spacer(Modifier.height(24.dp))
        Caption("链接与提示")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperArrow(title = "网页浏览器", summary = browserLabel, onClick = chooseBrowser)
            RowDivider()
            SuperSwitch(checked = island, onCheckedChange = onIsland, title = "小米超级岛", summary = "请求原生超级岛展示（实验）")
            RowDivider()
            SuperDropdown(items = PromptDuration.OPTIONS.map { "$it 秒" },
                selectedIndex = PromptDuration.OPTIONS.indexOf(seconds).coerceAtLeast(0), title = "提示停留时间",
                summary = "链接提示自动消失的间隔，下次发送起生效",
                onSelectedIndexChange = { onSeconds(PromptDuration.OPTIONS[it]) })
            RowDivider()
            SuperArrow(title = "系统通知设置", summary = "管理跳转提示和悬浮通知", onClick = notificationSettings)
        }
        Spacer(Modifier.height(24.dp))
        Caption("管理")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            SuperArrow(title = "运行诊断", summary = "查看监听状态、识别计数与设备信息", onClick = diagnostics)
            RowDivider()
            SuperArrow(title = "授权管理", summary = "查看通知、电池优化与自启动等权限", onClick = managePermissions)
            RowDivider()
            SuperArrow(title = "备份与恢复", summary = "导出配置，预览后合并或覆盖导入", onClick = backup)
            RowDivider()
            SuperArrow(title = "关于链接岛", summary = BuildConfig.VERSION_NAME, onClick = { about = true })
        }
    }
    SuperDialog(show = about, title = "链接岛", summary = BuildConfig.VERSION_NAME, onDismissRequest = { about = false }) {
        Text("链接在本机识别与匹配。日志只保留域名和处理结果，不保存完整链接或剪贴板正文。\n\n界面使用 miuix；权限桥接使用 Shizuku API 和 libsu。\n\n开源许可随 APK 和源码提供。", fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        TextButton("关闭", { about = false }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable
private fun DiagnosticsPage(running: Boolean, status: String, capture: String, device: String,
    probe: () -> Unit, refresh: () -> Unit, back: () -> Unit) {
    DetailPage("运行诊断", back) {
        Caption("运行诊断")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (running) "监听服务运行中" else "监听已停止", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(capture.ifBlank { "尚未连接监听服务" }, color = muted(), fontSize = 13.sp, lineHeight = 21.sp)
                    Text(status.ifBlank { "尚无运行记录" }, color = muted(), fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton("检查当前剪贴板", probe, Modifier.fillMaxWidth(), enabled = running)
        Note("每 2 秒检查一次以补偿漏事件；空内容、敏感内容和非网页文本分别显示。")
        Spacer(Modifier.height(24.dp))
        Caption("设备与权限")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            SelectionContainer { Text(device, fontSize = 13.sp, color = muted(), lineHeight = 22.sp) }
        }
        Spacer(Modifier.height(12.dp))
        TextButton("刷新诊断", refresh, Modifier.fillMaxWidth())
        Note("诊断不记录剪贴板内容或完整链接。")
    }
}

@Composable
private fun BackupPage(current: String, busy: Boolean, message: String, pending: JSONObject?, export: () -> Unit,
    chooseImport: () -> Unit, apply: (Boolean) -> String?, cancel: () -> Unit, back: () -> Unit) {
    var merge by remember(pending) { mutableStateOf(true) }
    var expanded by remember(pending) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember(pending) { mutableStateOf("") }
    DetailPage("备份与恢复", back) {
        Note("备份包含规则和普通设置，不包含系统授权、运行状态、剪贴板内容或跳转日志。通过系统文件选择器保存和读取 JSON 文件。")
        Note("文件为明文 JSON，包含自定义规则中的匹配内容和链接模板，请保存到合适的位置。")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("导出配置", export, Modifier.weight(1f), enabled = !busy)
            TextButton("选择备份文件", chooseImport, Modifier.weight(1f), enabled = !busy, colors = ButtonDefaults.textButtonColorsPrimary())
        }
        if (busy) Note("正在处理文件…")
        if (message.isNotEmpty()) Note(message)
        pending?.let { document ->
            val incoming = document.getJSONArray("rules")
            val settings = document.getJSONObject("settings")
            var planError = ""
            val planned = try { JSONArray(ConfigBackup.importRules(current, incoming, merge)).length() }
                catch (e: Exception) { planError = if (e is IllegalArgumentException) e.message ?: "无法合并" else "当前规则异常，无法合并"; -1 }
            Spacer(Modifier.height(22.dp))
            Card(Modifier.fillMaxWidth()) {
                SuperDropdown(items = listOf("合并导入", "覆盖导入"), selectedIndex = if (merge) 0 else 1,
                    title = "导入方式", onSelectedIndexChange = { merge = it == 0; error = "" })
            }
            val localCount = JSONArray(current).length()
            Note("本机 ${localCount} 条，备份 ${incoming.length()} 条。" + if (planned >= 0) "导入后 ${planned} 条。" else "")
            Note(if (merge) "同匹配方式和匹配内容的规则保留本机版本，新规则追加到末尾；备份中的普通设置会应用。"
                else "用备份替换当前规则与普通设置；备份未包含的普通设置恢复默认值。")
            if (merge && planned >= 0) Note("新增 ${planned - localCount} 条，跳过重复 ${incoming.length() - (planned - localCount)} 条。")
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                SuperArrow(title = "预览备份规则", summary = "${incoming.length()} 条 · 点击${if (expanded) "收起" else "展开"}", onClick = { expanded = !expanded })
                if (expanded) Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (index in 0 until incoming.length()) {
                        val rule = incoming.getJSONObject(index)
                        Text("${index + 1}. ${rule.optString("name", "自定义规则")} · ${if (rule.optBoolean("enabled", true)) "已启用" else "停用"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${typeLabel(rule.optString("type", "contains"))}：${rule.optString("pattern")}\n目标：${rule.optString("package").ifEmpty { "浏览器" }}" +
                            if (rule.optString("replacement").isNotEmpty()) "\n改写：${rule.optString("replacement")}" else "", fontSize = 12.sp, color = muted(), lineHeight = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Caption("将应用的普通设置 · ${settings.length()} 项")
            Note(settings.keys().asSequence().map { key -> backupSetting(key, settings.get(key)) }.joinToString("\n"))
            Note("导入后会停止当前监听，请检查授权后重新开启。导入不会恢复系统权限。")
            if (planError.isNotEmpty() || error.isNotEmpty()) Text(planError.ifEmpty { error }, Modifier.padding(top = 12.dp), color = MiuixTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            TextButton("确认导入配置", { confirm = true }, Modifier.fillMaxWidth(), enabled = !busy && planned >= 0,
                colors = ButtonDefaults.textButtonColorsPrimary())
            TextButton("取消本次导入", cancel, Modifier.fillMaxWidth())
        }
    }
    SuperDialog(show = confirm, title = if (merge) "合并导入配置？" else "覆盖当前配置？",
        summary = "导入前可返回预览。确认后会停止监听并应用所选配置。", onDismissRequest = { confirm = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("返回预览", { confirm = false }, Modifier.weight(1f))
            TextButton("导入", { error = apply(merge) ?: ""; confirm = false }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

private fun backupSetting(key: String, value: Any): String {
    val label = when (key) {
        "mode" -> "授权方式"; "browser" -> "网页浏览器"; "prompt_seconds" -> "提示停留时间"
        "island" -> "小米超级岛"; "hide_recent" -> "隐藏最近任务"; "auto_restart" -> "监听自动恢复"
        else -> "使用授权服务跳转"
    }
    val text = when {
        value is Boolean -> if (value) "开启" else "关闭"
        key == "mode" -> if (value == 1) "Root" else "Shizuku"
        key == "prompt_seconds" -> "$value 秒"
        value == "" -> "跟随系统默认"
        value == "ask" -> "每次询问"
        else -> value.toString()
    }
    return "$label：$text"
}

@Composable
private fun LogsPage(json: String, clear: () -> Unit, back: () -> Unit) {
    var failuresOnly by rememberSaveable { mutableStateOf(false) }
    val all = remember(json) { JSONArray(json).let { array -> (array.length() - 1 downTo 0).map { array.getJSONObject(it) } } }
    val entries = remember(all, failuresOnly) { if (failuresOnly) all.filter { it.optString("status") == "failed" } else all }
    val format = remember { java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.SIMPLIFIED_CHINESE) }
    val time = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.SIMPLIFIED_CHINESE) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.Back, "返回", Modifier.size(22.dp)) }
            Text("跳转日志", Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            TextButton("清空", { confirm = true }, enabled = all.isNotEmpty())
        }
        Text("最近 100 次，仅存域名与处理结果；点击记录查看完整过程。", Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = muted(), fontSize = 12.sp, lineHeight = 18.sp)
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (failuresOnly) "失败 ${entries.size} 次" else "共 ${all.size} 次", Modifier.weight(1f), color = muted(), fontSize = 12.sp)
            TextButton(if (failuresOnly) "显示全部" else "仅看失败", { failuresOnly = !failuresOnly })
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (failuresOnly) "暂无失败记录" else "暂无记录，复制链接或发送测试提示后可在此查看", Modifier.padding(28.dp), color = muted(), textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.getString("id") }) { entry ->
                var expanded by remember(entry.getString("id")) { mutableStateOf(false) }
                val events = entry.getJSONArray("events")
                val state = entry.optString("status")
                val label = when (state) {
                    "failed" -> "失败"; "requested" -> "已请求打开"; "ignored" -> "已忽略"; "expired" -> "已到期"
                    "replaced" -> "已被替换"; "skipped" -> "已跳过"; "legacy" -> "历史记录"; "cancelled" -> "已撤销"
                    "opening" -> "跳转中"; else -> "待处理"
                }
                Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }, cornerRadius = 12.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = if (state == "failed") MiuixTheme.colorScheme.error else Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(format.format(java.util.Date(entry.optLong("time"))), color = muted(), fontSize = 11.sp)
                    }
                    Text(entry.optString("detail"), Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 2.dp)) {
                        Text(entry.optString("host"), Modifier.weight(1f), color = muted(), fontSize = 12.sp)
                        Text("${events.length()} 步 · ${if (expanded) "收起" else "展开"}", color = muted(), fontSize = 11.sp)
                    }
                    if (expanded) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (index in 0 until events.length()) {
                            val event = events.getJSONObject(index)
                            Text("${time.format(java.util.Date(event.optLong("time")))} · ${event.optString("stage")}\n${event.optString("detail")}", fontSize = 12.sp, lineHeight = 18.sp, color = muted())
                        }
                    }
                }
            }
        }
    }
    SuperDialog(show = confirm, title = "清空跳转日志？", summary = "清空后无法恢复。", onDismissRequest = { confirm = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { confirm = false }, Modifier.weight(1f))
            TextButton("清空", { clear(); confirm = false }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

@Composable
private fun PermissionsPage(permissions: List<PermissionItem>, manage: (String) -> Unit, refresh: () -> Unit, back: () -> Unit) {
    DetailPage("授权管理", back, action = { TextButton("刷新", refresh) }) {
        Note("点按项目进入授权或系统设置。返回本页后自动更新可查询的状态。")
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            permissions.forEachIndexed { index, item ->
                SuperArrow(title = item.title, summary = item.summary,
                    endActions = { Text(item.state, color = when (item.allowed) {
                        true -> Accent; false -> MiuixTheme.colorScheme.error; null -> muted()
                    }, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 80.dp, max = 96.dp)) }, onClick = { manage(item.key) })
                if (index < permissions.lastIndex) RowDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
        Note("“需手动确认”表示系统未提供可读取的结果，不代表已经授权。允许自启动不会自动开启监听。")
    }
}

@Composable
private fun DetailPage(title: String, back: () -> Unit, action: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, modifier = Modifier.size(48.dp)) {
                Icon(MiuixIcons.Back, "返回", Modifier.size(22.dp))
            }
            Text(title, Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            action()
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 28.dp), content = content)
    }
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit, singleLine: Boolean = true, url: Boolean = false) {
    TextField(value = value, onValueChange = change, label = label, useLabelAsPlaceholder = true,
        modifier = Modifier.fillMaxWidth(), singleLine = singleLine, maxLines = if (singleLine) 1 else 4,
        textStyle = MiuixTheme.textStyles.main.copy(fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(keyboardType = if (url) KeyboardType.Uri else KeyboardType.Text))
}
@Composable private fun muted(): Color = MiuixTheme.colorScheme.onSurfaceVariantSummary
@Composable private fun Caption(text: String) { Text(text, fontSize = 13.sp, color = muted()) }
@Composable private fun Note(text: String) { Text(text, Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 21.sp, color = muted()) }
@Composable private fun RowDivider() { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(.5.dp).background(muted().copy(alpha = .13f))) }
private fun typeLabel(type: String) = when (type) { "domain" -> "域名"; "regex" -> "正则"; else -> "关键词" }

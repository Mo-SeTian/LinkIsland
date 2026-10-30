package dev.linkisland.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

internal data class PermissionItem(
    val key: String, val title: String, val summary: String,
    val state: String, val allowed: Boolean?
)

internal object PermissionAccess {
    fun isXiaomi() = Build.MANUFACTURER.lowercase() in listOf("xiaomi", "redmi", "poco")

    fun read(context: Context, mode: Int, checkingRoot: Boolean): List<PermissionItem> {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val appNotifications = notifications.areNotificationsEnabled()
        val channel = notifications.getNotificationChannel(LinkNotifications.PROMPTS)
        val promptEnabled = appNotifications && channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        val ignoringBattery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        val restricted = context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        val autoStart = autoStartAllowed(context)
        val backgroundAllowed = if (restricted) false else if (isXiaomi()) null else true
        return listOf(
            PermissionItem("notifications", "通知权限", "用于显示链接提示和监听状态通知",
                if (appNotifications) "已允许" else "未允许", appNotifications),
            PermissionItem("prompt", "链接提示通知", "需开启此通知通道；悬浮展示请在系统中设置",
                if (promptEnabled) "已开启" else "未开启", promptEnabled),
            PermissionItem("battery", "取消电池优化", "减少系统省电机制对后台监听的影响",
                if (ignoringBattery) "已取消" else "未取消", ignoringBattery),
            PermissionItem("autostart", "自启动", "允许后台自启动；无法读取时请在系统中确认",
                when (autoStart) { true -> "已允许"; false -> "未允许"; null -> "需手动确认" }, autoStart),
            PermissionItem("background", "后台运行", if (isXiaomi()) "请在小米省电策略中选择“无限制”" else "检查系统是否限制本应用后台运行",
                when (backgroundAllowed) { true -> "未被限制"; false -> "受限"; null -> "需手动确认" }, backgroundAllowed),
            bridgePermission(mode, checkingRoot)
        )
    }

    private fun autoStartAllowed(context: Context): Boolean? {
        if (!isXiaomi()) return null
        return try {
            val type = Class.forName("android.miui.AppOpsUtils")
            val result = type.getDeclaredMethod("getApplicationAutoStart", Context::class.java, String::class.java)
                .invoke(null, context, context.packageName)
            when (result) { 0 -> true; 1 -> false; else -> null }
        } catch (_: Exception) { null }
    }

    private fun bridgePermission(mode: Int, checkingRoot: Boolean): PermissionItem {
        if (mode == 1) {
            val allowed = if (checkingRoot) null else Shell.isAppGrantedRoot()
            return PermissionItem("bridge", "Root 授权", "显示当前会话结果；点按检查或申请 Root 授权",
                if (checkingRoot) "检查中…" else when (allowed) { true -> "已授权"; false -> "未授权或不可用"; null -> "未检查" }, allowed)
        }
        val online = Shizuku.pingBinder()
        val allowed = try { online && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            catch (_: RuntimeException) { false }
        return PermissionItem("bridge", "Shizuku 授权", if (online) "当前授权方式；点按申请或前往 Shizuku 管理" else "请先启动 Shizuku，再返回申请授权",
            if (!online) "服务未启动" else if (allowed) "已授权" else "未授权", allowed)
    }
}

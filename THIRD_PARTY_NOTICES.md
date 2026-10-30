# 第三方依赖与参考

- [miuix 0.8.8 / miuix-icons 0.8.8](https://github.com/compose-miuix-ui/miuix)：Apache-2.0，compose-miuix-ui contributors。界面、主题、偏好行、弹窗、按钮、分段选择器和图标。采用兼容本项目 SDK 36 的固定版本。
- [AndroidX Activity Compose 1.12.4 / Jetpack Compose](https://developer.android.com/jetpack/compose)：Apache-2.0，The Android Open Source Project。提供 Android 原生 Compose 界面和返回事件宿主。
- [Lucide Power 图标](https://github.com/lucide-icons/lucide/blob/main/icons/power.svg)：从官方 SVG 转为 Android VectorDrawable，许可全文见 app/src/main/assets/licenses/Lucide.txt。

- [Shizuku API 13.1.5](https://github.com/RikkaApps/Shizuku-API)：MIT，Copyright (c) 2021 RikkaW。包含 api / provider 及其传递依赖。许可全文打包于 app/src/main/assets/licenses/Shizuku-MIT.txt。
- [libsu 6.0.0](https://github.com/topjohnwu/libsu)：Apache-2.0，Copyright John “topjohnwu” Wu。使用 service / core。许可全文打包于 app/src/main/assets/licenses/Apache-2.0.txt。
- [AndroidX Annotation](https://developer.android.com/jetpack/androidx/releases/annotation)：Apache-2.0，The Android Open Source Project。
- Kotlin 标准库（依赖传递引入）：Apache-2.0，JetBrains。相关许可遵循依赖包内声明。
- JUnit 4.13.2（仅测试）：EPL-1.0；org.json 20240303（仅测试）：Public Domain。两者不打包到 APK。

- [Simple Icons](https://github.com/simple-icons/simple-icons/tree/90646e4bf7f3b89df6c37e65baf3e7bc759a517c)：9 个品牌备用图标，CC0-1.0。基于原始 SVG 路径规范化为 Android VectorDrawable，许可全文见 app/src/main/assets/licenses/SimpleIcons-CC0.txt。品牌标志用于识别跳转目标，不表示品牌为链接岛背书。

实现参考：

- [PackageManager.MATCH_ALL](https://developer.android.com/reference/android/content/pm/PackageManager#MATCH_ALL)：浏览器枚举绕过默认查询过滤，并结合通用网页 Intent 与过滤器元数据筛选浏览器。
- 新增默认应用映射参考应用发布页与官方入口：[知乎](https://play.google.com/store/apps/details?id=com.zhihu.android)、[小红书](https://play.google.com/store/apps/details?id=com.xingin.xhs)、[微博](https://play.google.com/store/apps/details?id=com.sina.weibo)、[淘宝](https://play.google.com/store/apps/details?id=com.taobao.taobao)、[京东](https://play.google.com/store/apps/details?id=com.jingdong.app.mall)、[YouTube](https://play.google.com/store/apps/details?id=com.google.android.youtube)、[哔哩哔哩下载中心](https://app.bilibili.com/)、[抖音 Android 接入文档](https://open.douyin.com/platform/resource/docs/develop/guide/douyin-sdk/android)、[酷安](https://www.coolapk.com/)。仅包含应用名称、包名和常见域名映射，没有集成这些应用的 SDK 或下载其图标资源。

- [Android 通知超时](https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long))、[Service.onTaskRemoved](https://developer.android.com/reference/android/app/Service#onTaskRemoved(android.content.Intent))、[停止前台服务的系统行为](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)：提示超时、任务移除清理及不能依赖退出回调的边界。

- [Android 电池优化与后台限制](https://developer.android.com/training/monitoring-device-state/doze-standby)：电池白名单状态查询与用户发起的设置入口；后台限制使用 ActivityManager.isBackgroundRestricted。
- [MIUI-Autostart](https://github.com/XomaDev/MIUI-Autostart)：仅参考小米自启动接口名称与明确返回值，未集成该库、HiddenApiBypass 或默认将未知结果判为已授权的逻辑。
- [小米省电管理入口参考](https://code.briarproject.org/briar/briar/-/issues/2132)：尝试系统设置 Activity，失败后回退应用信息，不修改系统策略。
- [小米超级岛官方开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131)：通知 JSON 协议、Action Bundle 和权限查询。
- [scrcpy ClipboardManager](https://github.com/Genymobile/scrcpy/blob/v2.7/server/src/main/java/com/genymobile/scrcpy/wrappers/ClipboardManager.java)：参考 Android / MIUI 剪贴板 Binder 接口签名和 shell 包名使用方式。未复制项目源文件。
- [Xiaomi-SuperIsland-Playground](https://github.com/HuberHaYu/Xiaomi-SuperIsland-Playground)：调研参考；本版未集成 SDK 或其 XMSF 网络策略。

测试版使用自行实现的规则引擎，未集成 URLCheck、LinkSheet 或 Capsulyric 源码。

package dev.linkisland.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

final class TargetAppIcon {
    static Bitmap load(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        Drawable drawable = null;
        if (packageName.isEmpty()) packageName = BrowserAccess.effectivePackage(context, BrowserAccess.installed(context));
        if (!packageName.isEmpty()) {
            try { drawable = pm.getApplicationIcon(packageName); }
            catch (PackageManager.NameNotFoundException | android.content.res.Resources.NotFoundException ignored) { }
        }
        int background = 0;
        if (drawable == null) {
            int brand = switch (packageName) {
                case "com.github.android" -> R.drawable.brand_github;
                case "tv.danmaku.bili" -> R.drawable.brand_bilibili;
                case "com.zhihu.android" -> R.drawable.brand_zhihu;
                case "com.xingin.xhs" -> R.drawable.brand_xiaohongshu;
                case "com.sina.weibo" -> R.drawable.brand_sinaweibo;
                case "com.taobao.taobao" -> R.drawable.brand_taobao;
                case "com.douban.frodo" -> R.drawable.brand_douban;
                case "com.netease.cloudmusic" -> R.drawable.brand_neteasecloudmusic;
                case "com.google.android.youtube" -> R.drawable.brand_youtube;
                default -> 0;
            };
            if (brand != 0) {
                try { drawable = context.getDrawable(brand); }
                catch (android.content.res.Resources.NotFoundException ignored) { drawable = null; }
                background = switch (packageName) {
                    case "tv.danmaku.bili" -> 0xFF00A1D6;
                    case "com.zhihu.android" -> 0xFF0084FF;
                    case "com.xingin.xhs" -> 0xFFFF2442;
                    case "com.sina.weibo" -> 0xFFE6162D;
                    case "com.taobao.taobao" -> 0xFFFF5000;
                    case "com.douban.frodo" -> 0xFF007722;
                    case "com.netease.cloudmusic" -> 0xFFD43C33;
                    case "com.google.android.youtube" -> 0xFFFF0000;
                    default -> 0xFF24292F;
                };
            }
        }
        if (drawable == null) { drawable = context.getDrawable(R.drawable.ic_link); background = 0; }
        Bitmap bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (background != 0) {
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(background);
            canvas.drawRoundRect(0, 0, 224, 224, 48, 48, paint);
            drawable.setBounds(36, 36, 188, 188);
        } else drawable.setBounds(0, 0, 224, 224);
        drawable.draw(canvas);
        return bitmap;
    }
}

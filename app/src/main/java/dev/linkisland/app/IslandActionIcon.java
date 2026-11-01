package dev.linkisland.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

/** Padded artwork for the OS-sized round action slots; glyphs must not fill the slot. */
final class IslandActionIcon {
    static Icon copy(Context c) { return create(c, R.drawable.ic_copy, 0xFF414148); }
    static Icon open(Context c) { return create(c, R.drawable.ic_open, 0xFF287EFF); }
    private static Icon create(Context c, int resource, int color) {
        Bitmap bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawCircle(112, 112, 84, paint);
        Drawable glyph = c.getDrawable(resource);
        glyph.setBounds(70, 70, 154, 154);
        glyph.draw(canvas);
        return Icon.createWithBitmap(bitmap);
    }
}

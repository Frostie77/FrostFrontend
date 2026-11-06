package io.github.frostfrontend.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import io.github.frostfrontend.FrostTouchControls;

/** A game-independent example: the shared base supplies all layout editing and persistence. */
final class DemoTouchControls extends FrostTouchControls {
    private final SharedPreferences settings;
    private float controlRadius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    DemoTouchControls(Context context, SharedPreferences settings) {
        super(context, "demo", "movement", "primary", "interact");
        this.settings = settings;
    }

    @Override protected void computeLayout() {
        boolean swapped = settings.getBoolean("touch_swap", false);
        setLayoutMirrored(swapped);
        float radius = controlRadius = dp(44) * new float[]{0.84f, 1f, 1.16f}[Math.max(0, Math.min(2, settings.getInt("touch_size", 1)))];
        float inset = dp(new float[]{14, 26, 42}[Math.max(0, Math.min(2, settings.getInt("touch_inset", 1)))]);
        float left = inset + radius, right = getWidth() - inset - radius, y = getHeight() - inset - radius;
        positionControl(0, swapped ? right : left, y, radius, radius * 1.1f);
        positionControl(1, swapped ? left : right, y, radius * 0.65f, radius * 0.75f);
        positionControl(2, (swapped ? left : right) + (swapped ? 1 : -1) * radius * 1.7f,
            y, radius * 0.65f, radius * 0.75f);
    }

    @Override protected void drawControls(Canvas canvas) {
        String[] labels = {settings.getBoolean("touch_dpad", false) ? "D-pad" : "Move", "Attack", "Use"};
        for (int i = 0; i < labels.length; i++) {
            float radius = controlRadius * (i == 0 ? 1f : 0.65f);
            paint.setColor(0xBB433147); paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX[i], centerY[i], radius, paint);
            paint.setColor(0xFFECE7EF); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(14));
            canvas.drawText(labels[i], centerX[i], centerY[i] + dp(5), paint);
        }
    }

    @Override protected boolean onGameplayTouch(MotionEvent event) { return false; }
    @Override protected void releaseGameKeys() { /* Preview has no game input. */ }
}

package io.github.frostfrontend;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Base for a port's own touch artwork and gameplay input. FrostFrontend owns
 * drag editing, normalized saved positions, cancellation, reset and bounds.
 * Stable control IDs and a private preference profile keep layouts independent.
 */
public abstract class FrostTouchControls extends View {
    public static final int EDITOR_TOOLBAR_HEIGHT_DP = 76;
    private final String[] controlIds;
    private final SharedPreferences preferences;
    private final float[] customX, customY, extent, hitRadius;
    protected final float[] centerX, centerY;
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean editing, mirrored;
    private int editPointer = -1, editControl = -1;
    private float dragOffsetX, dragOffsetY;

    protected FrostTouchControls(Context context, String profileId, String... controlIds) {
        this(context, context.getSharedPreferences("frost_touch_layout_" + profileId,
            Context.MODE_PRIVATE), controlIds);
        if (profileId == null || profileId.trim().isEmpty()) throw new IllegalArgumentException("A layout profile ID is required");
    }

    /** Also accepts an existing store so a port can retain layouts from older builds. */
    protected FrostTouchControls(Context context, SharedPreferences preferences, String... controlIds) {
        super(context);
        if (preferences == null || controlIds == null || controlIds.length == 0)
            throw new IllegalArgumentException("Preferences and at least one control are required");
        HashSet<String> unique = new HashSet<>();
        for (String id : controlIds) {
            if (id == null || id.trim().isEmpty() || !unique.add(id))
                throw new IllegalArgumentException("Control IDs must be nonempty and unique");
        }
        this.preferences = preferences;
        this.controlIds = controlIds.clone();
        customX = new float[controlIds.length]; customY = new float[controlIds.length];
        centerX = new float[controlIds.length]; centerY = new float[controlIds.length];
        extent = new float[controlIds.length]; hitRadius = new float[controlIds.length];
        loadLayout();
        setClickable(true);
    }

    /** Supply default pixel geometry using positionControl for every registered control. */
    protected abstract void computeLayout();
    protected abstract void drawControls(Canvas canvas);
    protected abstract boolean onGameplayTouch(MotionEvent event);
    protected abstract void releaseGameKeys();

    protected final float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    protected final void setLayoutMirrored(boolean mirrored) { this.mirrored = mirrored; }
    public final boolean isEditing() { return editing; }

    private void loadLayout() {
        for (int i = 0; i < controlIds.length; i++) {
            float x = preferences.getFloat(controlIds[i] + "_x", Float.NaN);
            float y = preferences.getFloat(controlIds[i] + "_y", Float.NaN);
            boolean valid = Float.isFinite(x) && Float.isFinite(y) && x >= 0f && x <= 1f && y >= 0f && y <= 1f;
            customX[i] = valid ? x : Float.NaN; customY[i] = valid ? y : Float.NaN;
        }
    }

    private void saveLayout() {
        SharedPreferences.Editor editor = preferences.edit();
        for (int i = 0; i < controlIds.length; i++) {
            if (Float.isNaN(customX[i])) editor.remove(controlIds[i] + "_x").remove(controlIds[i] + "_y");
            else editor.putFloat(controlIds[i] + "_x", customX[i]).putFloat(controlIds[i] + "_y", customY[i]);
        }
        editor.apply();
    }

    public final boolean hasCustomLayout() {
        for (float x : customX) if (!Float.isNaN(x)) return true;
        return false;
    }

    public final void beginEditing() { releaseAll(); editing = true; invalidate(); }

    public final void finishEditing(boolean save) {
        if (save) {
            computeLayout();
            if (getWidth() > 0 && getHeight() > 0) {
                for (int i = 0; i < controlIds.length; i++)
                    if (!Float.isNaN(customX[i])) storePosition(i, centerX[i], centerY[i]);
            }
            saveLayout();
        } else loadLayout();
        editing = false;
        releaseAll();
    }

    /** In the editor this only previews a reset; Save applies it and Cancel restores it. */
    public final void resetLayout() {
        Arrays.fill(customX, Float.NaN); Arrays.fill(customY, Float.NaN);
        if (!editing) saveLayout();
        releaseAll();
    }

    public final void releaseAll() {
        releaseGameKeys();
        editPointer = editControl = -1;
        invalidate();
    }

    private float bounded(float value, float radius, float length, float topInset) {
        float margin = radius + dp(4);
        if (length <= margin * 2f) return length / 2f;
        return Math.max(Math.min(margin + topInset, length - margin), Math.min(length - margin, value));
    }

    protected final void positionControl(int id, float x, float y, float radius, float touchRadius) {
        extent[id] = radius; hitRadius[id] = touchRadius;
        if (!Float.isNaN(customX[id])) {
            x = (mirrored ? 1f - customX[id] : customX[id]) * getWidth();
            y = customY[id] * getHeight();
        }
        centerX[id] = bounded(x, radius, getWidth(), 0f);
        centerY[id] = bounded(y, radius, getHeight(), editing ? dp(EDITOR_TOOLBAR_HEIGHT_DP) : 0f);
    }

    private void storePosition(int id, float x, float y) {
        customX[id] = mirrored ? 1f - x / getWidth() : x / getWidth();
        customY[id] = y / getHeight();
    }

    @Override protected final void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        computeLayout();
        drawControls(canvas);
        if (editing && editControl >= 0) {
            highlight.setStyle(Paint.Style.STROKE);
            highlight.setStrokeWidth(dp(3)); highlight.setColor(0xFF79D4FF);
            canvas.drawCircle(centerX[editControl], centerY[editControl], extent[editControl] + dp(4), highlight);
        }
    }

    @Override public final boolean onTouchEvent(MotionEvent event) {
        computeLayout();
        if (!editing) return onGameplayTouch(event);
        int action = event.getActionMasked(), changed = event.getActionIndex();
        if (action == MotionEvent.ACTION_CANCEL) editPointer = editControl = -1;
        else if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && editPointer == -1) {
            float x = event.getX(changed), y = event.getY(changed), nearest = Float.MAX_VALUE;
            for (int i = 0; i < controlIds.length; i++) {
                float distance = (float) Math.hypot(x - centerX[i], y - centerY[i]);
                if (distance <= hitRadius[i] && distance < nearest) { editControl = i; nearest = distance; }
            }
            if (editControl >= 0) {
                editPointer = event.getPointerId(changed);
                dragOffsetX = x - centerX[editControl]; dragOffsetY = y - centerY[editControl];
            }
        } else if (action == MotionEvent.ACTION_MOVE && editPointer != -1) {
            int p = event.findPointerIndex(editPointer);
            if (p >= 0 && getWidth() > 0 && getHeight() > 0) {
                storePosition(editControl,
                    bounded(event.getX(p) - dragOffsetX, extent[editControl], getWidth(), 0f),
                    bounded(event.getY(p) - dragOffsetY, extent[editControl], getHeight(), dp(EDITOR_TOOLBAR_HEIGHT_DP)));
            }
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) &&
                event.getPointerId(changed) == editPointer) editPointer = editControl = -1;
        invalidate();
        return true;
    }
}

package io.github.frostfrontend;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable, game-independent FrostFrontend settings overlay.
 *
 * Add this view above a game's content view. The host owns its settings,
 * persistence, gameplay pause/resume, and actions. No Arcane or Ruffle
 * dependency is required.
 */
public final class FrostFrontendView extends FrameLayout {
    public static final class Tab {
        public final String id;
        public final String title;

        public Tab(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public static final class Item {
        public final String id;
        public final String title;
        public final String value;
        public final String description;

        public Item(String id, String title, String value, String description) {
            this.id = id;
            this.title = title;
            this.value = value == null ? "" : value;
            this.description = description == null ? "" : description;
        }
    }

    public interface ButtonCaptureListener {
        /** A supported physical gamepad button was pressed. */
        void onCaptured(int keyCode);

        /** Start or Android Back was pressed before a button was assigned. */
        void onCancelled();
    }

    public interface Adapter {
        /** Called whenever FrostFrontend needs the current settings for a tab. */
        List<Item> itemsForTab(String tabId);

        /** Apply a setting in the host and persist it there if appropriate. */
        void onItemSelected(String tabId, String itemId);

        /** Pause/resume the host game when this view opens/closes. */
        void onVisibilityChanged(boolean visible);
    }

    private static final int PANEL = 0xF5221A2B;
    private static final int GOLD = 0xFFC09A62;
    private static final int SELECTED = 0xFF433147;
    private static final int NORMAL = 0xFF2B2432;
    private static final int TEXT = 0xFFECE7EF;
    private static final int SUBTLE = 0xFFBDB1C8;

    private final List<Tab> tabs = new ArrayList<>();
    private final List<OptionRow> rowViews = new ArrayList<>();
    private List<Item> items = Collections.emptyList();
    private Adapter adapter;
    private LinearLayout panel;
    private LinearLayout tabStrip;
    private LinearLayout rowContainer;
    private ScrollView scroll;
    private TextView captureBanner;
    private TextView mappingNotice;
    private float leftTriggerAxis, rightTriggerAxis;
    private ButtonCaptureListener captureListener;
    private int tabIndex;
    private int selectedRow;
    private boolean isOpen;

    public FrostFrontendView(Context context) {
        super(context);
        init();
    }

    public FrostFrontendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        refresh();
    }

    public void setTabs(List<Tab> tabs) {
        if (tabs == null) throw new IllegalArgumentException("tabs cannot be null");
        this.tabs.clear();
        this.tabs.addAll(tabs);
        tabIndex = Math.max(0, Math.min(tabIndex, this.tabs.size() - 1));
        selectedRow = 0;
        refresh();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void open() {
        if (isOpen || tabs.isEmpty()) return;
        isOpen = true;
        setVisibility(VISIBLE);
        resizePanel();
        refresh();
        requestFocus();
        if (adapter != null) adapter.onVisibilityChanged(true);
    }

    public void close() {
        if (!isOpen) return;
        cancelButtonCapture();
        isOpen = false;
        setVisibility(GONE);
        if (adapter != null) adapter.onVisibilityChanged(false);
    }

    public void toggle() {
        if (isOpen) close();
        else open();
    }

    /**
     * Start listening for a physical controller button to bind. During capture,
     * gamepad navigation is suspended. Start / Android Back cancels capture.
     * The B button is assignable and will NOT close the menu during capture.
     */
    public void beginButtonCapture(String actionName, ButtonCaptureListener listener) {
        if (!isOpen || listener == null)
            throw new IllegalStateException("Open FrostFrontend before binding a button");
        if (captureListener != null) cancelButtonCapture();
        captureListener = listener;
        leftTriggerAxis = 0f;
        rightTriggerAxis = 0f;
        clearMappingNotice();
        captureBanner.setText("ASSIGN " + actionName.toUpperCase(java.util.Locale.ROOT)
            + "  ·  Press A/B/X/Y, L1/L2/R1/R2 or L3/R3  ·  Start to cancel");
        captureBanner.setVisibility(VISIBLE);
        requestFocus();
    }

    public boolean isCapturingButton() {
        return captureListener != null;
    }

    /** Display an in-menu confirmation after a change or mapping conflict. */
    public void showMappingNotice(String notice) {
        if (mappingNotice == null) return;
        if (notice == null || notice.isEmpty()) {
            clearMappingNotice();
            return;
        }
        mappingNotice.setText(notice);
        mappingNotice.setVisibility(VISIBLE);
    }

    public void clearMappingNotice() {
        if (mappingNotice != null) mappingNotice.setVisibility(GONE);
    }

    /**
     * Some Android gamepads report L2/R2 as analogue axes rather than key
     * events. The host Activity can pass its generic motion events here.
     * Return false outside capture so the host game still receives them.
     */
    public boolean handleGenericMotionEvent(MotionEvent event) {
        if (!isOpen || captureListener == null) return false;
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0 ||
            event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        float left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE));
        float right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS));
        // Rising edge only; avoid repeated captures while a trigger is held.
        if (left >= 0.75f && leftTriggerAxis < 0.75f) {
            completeButtonCapture(KeyEvent.KEYCODE_BUTTON_L2);
        } else if (right >= 0.75f && rightTriggerAxis < 0.75f) {
            completeButtonCapture(KeyEvent.KEYCODE_BUTTON_R2);
        }
        leftTriggerAxis = left;
        rightTriggerAxis = right;
        return true;
    }

    private void completeButtonCapture(int code) {
        ButtonCaptureListener selected = captureListener;
        if (selected == null) return;
        captureListener = null;
        captureBanner.setVisibility(GONE);
        selected.onCaptured(code);
        if (isOpen) refresh();
    }

    /** Cancel a pending binding without changing the current assignments. */
    public void cancelButtonCapture() {
        ButtonCaptureListener old = captureListener;
        captureListener = null;
        if (captureBanner != null) captureBanner.setVisibility(GONE);
        if (old != null) old.onCancelled();
    }

    /** Read the host's latest values and update the displayed tab and rows. */
    public void refresh() {
        if (!isOpen || tabs.isEmpty()) return;
        renderTabs();
        renderRows();
    }

    /**
     * A game's Activity should call this before delivering controller keys
     * to gameplay, while the overlay is open. Returning true means consumed.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (!isOpen) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0)
            return true;
        if (captureListener != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_START) {
                cancelButtonCapture();
            } else if (FrostButtonMapper.isAssignableButton(code)) {
                completeButtonCapture(code);
            } else {
                captureBanner.setText("Button not assignable (" + code +
                    "). Choose A/B/X/Y, L1/L2/R1/R2, L3/R3 or Start to cancel.");
            }
            return true;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_PAGE_UP:
                changeTab(-1);
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                changeTab(1);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                changeSelection(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                changeSelection(1);
                break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                activateSelection();
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_START:
                close();
                break;
            default:
                break;
        }
        return true;
    }

    private void init() {
        setVisibility(GONE);
        setBackgroundColor(0xB9000000);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription("FrostFrontend settings");

        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(10));
        panel.setBackground(background(PANEL, GOLD, 16));
        panel.setClickable(true);
        addView(panel, new FrameLayout.LayoutParams(dp(820), dp(600), Gravity.CENTER));

        FrameLayout header = new FrameLayout(getContext());
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(43)));

        tabStrip = new LinearLayout(getContext());
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
            -1, -1, Gravity.START | Gravity.CENTER_VERTICAL);
        stripParams.rightMargin = dp(43);
        header.addView(tabStrip, stripParams);

        View exit = new ExitIcon(getContext());
        exit.setOnClickListener(view -> close());
        exit.setContentDescription("Close FrostFrontend");
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(
            dp(33), dp(33), Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(exit, exitParams);

        captureBanner = new TextView(getContext());
        captureBanner.setTextSize(14);
        captureBanner.setTextColor(0xFFFFE6AD);
        captureBanner.setGravity(Gravity.CENTER);
        captureBanner.setPadding(dp(10), dp(7), dp(10), dp(7));
        captureBanner.setBackground(background(0xFF48323F, GOLD, 7));
        captureBanner.setVisibility(GONE);
        captureBanner.setContentDescription("Waiting for a controller button; Start cancels");
        captureBanner.setOnClickListener(v -> cancelButtonCapture());
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(-1, -2);
        captureParams.bottomMargin = dp(6);
        panel.addView(captureBanner, captureParams);

        mappingNotice = new TextView(getContext());
        mappingNotice.setTextSize(14);
        mappingNotice.setTextColor(0xFFFFE6AD);
        mappingNotice.setGravity(Gravity.CENTER);
        mappingNotice.setPadding(dp(10), dp(7), dp(10), dp(7));
        mappingNotice.setBackground(background(0xFF3B2939, GOLD, 7));
        mappingNotice.setVisibility(GONE);
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2);
        noticeParams.bottomMargin = dp(6);
        panel.addView(mappingNotice, noticeParams);

        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        rowContainer = new LinearLayout(getContext());
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowContainer, new ScrollView.LayoutParams(-1, -2));
    }

    private void renderTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TextView tab = new TextView(getContext());
            tab.setText(tabs.get(i).title);
            tab.setTextSize(18);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
            tab.setTextColor(i == tabIndex ? 0xFFFFDFA2 : 0xFFDDD6E0);
            tab.setBackground(background(i == tabIndex ? SELECTED : NORMAL,
                i == tabIndex ? 0xFFD0AA69 : 0xFF564761, 9));
            tab.setContentDescription(tabs.get(i).title + " settings");
            tab.setOnClickListener(v -> {
                if (captureListener == null) selectTab(index);
            });
            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) params.leftMargin = dp(6);
            tabStrip.addView(tab, params);
        }
    }

    private void renderRows() {
        rowContainer.removeAllViews();
        rowViews.clear();
        List<Item> supplied = adapter == null
            ? Collections.emptyList()
            : adapter.itemsForTab(tabs.get(tabIndex).id);
        items = supplied == null ? Collections.emptyList() : new ArrayList<>(supplied);
        selectedRow = Math.max(0, Math.min(selectedRow, items.size() - 1));
        if (items.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("No settings in this section.");
            empty.setTextColor(SUBTLE);
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            rowContainer.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            OptionRow row = new OptionRow(getContext(), items.get(i));
            row.setOnClickListener(v -> {
                if (captureListener != null) return;
                selectedRow = index;
                updateSelection();
                activateSelection();
            });
            rowContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
            rowViews.add(row);
        }
        updateSelection();
    }

    private void selectTab(int newIndex) {
        if (newIndex < 0 || newIndex >= tabs.size() || newIndex == tabIndex)
            return;
        tabIndex = newIndex;
        selectedRow = 0;
        scroll.scrollTo(0, 0);
        refresh();
    }

    private void changeTab(int direction) {
        if (tabs.isEmpty()) return;
        selectTab((tabIndex + direction + tabs.size()) % tabs.size());
    }

    private void changeSelection(int direction) {
        if (items.isEmpty()) return;
        selectedRow = Math.max(0, Math.min(items.size() - 1, selectedRow + direction));
        updateSelection();
    }

    private void activateSelection() {
        if (adapter == null || items.isEmpty()) return;
        String tabId = tabs.get(tabIndex).id;
        String itemId = items.get(selectedRow).id;
        adapter.onItemSelected(tabId, itemId);
        // The host may change a setting, its label or the available options.
        if (isOpen) refresh();
    }

    private void updateSelection() {
        for (int i = 0; i < rowViews.size(); i++)
            rowViews.get(i).setHighlighted(i == selectedRow);
        if (selectedRow < rowViews.size()) {
            View row = rowViews.get(selectedRow);
            scroll.post(() -> {
                if (!isOpen || row.getParent() != rowContainer) return;
                int top = row.getTop();
                int bottom = row.getBottom();
                if (top < scroll.getScrollY() + dp(4))
                    scroll.smoothScrollTo(0, Math.max(0, top - dp(4)));
                else if (bottom > scroll.getScrollY() + scroll.getHeight() - dp(4))
                    scroll.smoothScrollTo(0,
                        Math.max(0, bottom - scroll.getHeight() + dp(4)));
            });
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resizePanel();
    }

    private void resizePanel() {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        params.width = Math.max(1, Math.min(dp(820), w - dp(24)));
        params.height = Math.max(1, Math.min(dp(600), h - dp(24)));
        panel.setLayoutParams(params);
    }

    private GradientDrawable background(int color, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private final class OptionRow extends LinearLayout {
        private final TextView details;

        OptionRow(Context context, Item item) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(5), dp(10), dp(5));
            setMinimumHeight(dp(36));
            setClickable(true);

            LinearLayout heading = new LinearLayout(context);
            heading.setOrientation(HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            addView(heading, new LinearLayout.LayoutParams(-1, -2));

            TextView title = new TextView(context);
            title.setText(item.title);
            title.setTextSize(14);
            title.setTextColor(TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

            if (!item.value.isEmpty()) {
                TextView value = new TextView(context);
                value.setText(item.value);
                value.setTextSize(13);
                value.setTextColor(0xFFE0CCAA);
                value.setSingleLine(true);
                value.setGravity(Gravity.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = dp(9);
                heading.addView(value, lp);
            }

            details = new TextView(context);
            details.setText(item.description);
            details.setTextSize(11);
            details.setTextColor(SUBTLE);
            details.setPadding(dp(2), dp(2), 0, 0);
            details.setVisibility(GONE);
            addView(details, new LinearLayout.LayoutParams(-1, -2));
            setContentDescription(item.title + (item.value.isEmpty() ? "" : ": " + item.value));
        }

        void setHighlighted(boolean highlighted) {
            setBackground(background(highlighted ? SELECTED : NORMAL,
                highlighted ? 0xFFC09A62 : 0, 8));
            details.setVisibility(highlighted && details.length() > 0 ? VISIBLE : GONE);
        }
    }

    private final class ExitIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ExitIcon(Context context) {
            super(context);
            setBackground(background(0xFF3B2939, 0xFFBA986A, 9));
            paint.setColor(0xFFFFE0A1);
            paint.setStrokeWidth(dp(2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = getWidth() / 2f;
            float y = getHeight() / 2f;
            float d = dp(7);
            canvas.drawLine(x - d, y - d, x + d, y + d, paint);
            canvas.drawLine(x - d, y + d, x + d, y - d, paint);
        }
    }
}

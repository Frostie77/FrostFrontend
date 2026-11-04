package io.github.frostfrontend;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
        public final boolean unmapped;

        public Item(String id, String title, String value, String description) {
            this(id, title, value, description, false);
        }

        public Item(String id, String title, String value, String description, boolean unmapped) {
            this.id = id;
            this.title = title;
            this.value = value == null ? "" : value;
            this.description = description == null ? "" : description;
            this.unmapped = unmapped;
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

        /** Arcane uses left/right to cycle settings in both directions. */
        default void onItemAdjusted(String tabId, String itemId, int direction) {
            onItemSelected(tabId, itemId);
        }
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
    private TextView updateInfo;
    private String installedVersion = "FrostFrontend Demo";
    private String latestVersion = "Not checked";
    private String updateStatus = "Demo only: no verified update feed";
    private float leftTriggerAxis, rightTriggerAxis;
    private ButtonCaptureListener captureListener;
    private int tabIndex;
    private int selectedRow;
    private int menuAxisLatch;
    private boolean isOpen;
    private boolean mappingMode;
    public interface BackListener { boolean onBackRequested(); }
    private BackListener backListener;

    public void setBackListener(BackListener listener) {
        backListener = listener;
    }

    public void back() {
        if (unmappedWarningLayer != null) {
            confirmUnmappedWarning(false);
            return;
        }
        if (captureListener != null) {
            cancelButtonCapture();
            return;
        }
        if (mappingMode) {
            requestMappingBack();
        } else if (backListener != null && backListener.onBackRequested()) {
            refresh();
        } else if (tabIndex != 0) {
            selectTab(0);
        } else {
            close();
        }
    }
    private FrostButtonMapper buttonMapper;
    private Runnable onLeavingMapping;
    private Runnable pendingExit;
    private FrameLayout unmappedWarningLayer;
    private TextView warningKeepEditing, warningContinue;
    private int warningSelection;


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

    public void setUpdateInfo(String installed, String latest, String status) {
        installedVersion = installed;
        latestVersion = latest;
        updateStatus = status;
        if (isOpen) refresh();
    }

    /** Called by a host on entry to Arcane's Button Mapping submenu. */
    public void enterButtonMapping(FrostButtonMapper mapper, Runnable onLeave) {
        buttonMapper = mapper;
        mappingMode = true;
        onLeavingMapping = onLeave;
    }

    /** Back to Controls, respecting Arcane's unmapped-controls warning. */
    public void requestMappingBack() {
        attemptLeaveMapping(() -> {
            if (onLeavingMapping != null) onLeavingMapping.run();
            mappingMode = false;
            selectedRow = 0;
            refresh();
        });
    }

    private void attemptLeaveMapping(Runnable action) {
        if (unmappedWarningLayer != null) return;
        if (mappingMode && buttonMapper != null &&
            !buttonMapper.unmappedActions().isEmpty()) {
            pendingExit = action;
            showUnmappedWarning();
        } else action.run();
    }


    public void open() {
        if (isOpen || tabs.isEmpty()) return;
        isOpen = true;
        menuAxisLatch = 0;
        setVisibility(VISIBLE);
        resizePanel();
        refresh();
        requestFocus();
        if (adapter != null) adapter.onVisibilityChanged(true);
    }

    public void close() {
        if (!isOpen || unmappedWarningLayer != null) return;
        if (mappingMode && captureListener != null) cancelButtonCapture();
        attemptLeaveMapping(this::closeWithoutWarning);
    }

    private void closeWithoutWarning() {
        if (!isOpen) return;
        cancelButtonCapture();
        if (mappingMode && onLeavingMapping != null) onLeavingMapping.run();
        mappingMode = false;
        menuAxisLatch = 0;
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
        captureBanner.setText("WAITING FOR BUTTON…");
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
    /**
     * Arcane's analogue menu navigation: left stick Y selects rows, left
     * stick X selects modal choices, triggers can be captured as L2/R2.
     * The latch prevents one long analogue press scrolling through pages.
     */
    public boolean handleGenericMotionEvent(MotionEvent event) {
        if (!isOpen) return false;
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0 ||
            event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;

        if (unmappedWarningLayer != null) {
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            int direction = x > 0.68f ? 1 : x < -0.68f ? -1 : 0;
            if (direction == 0) {
                if (Math.abs(x) < 0.28f) menuAxisLatch = 0;
            } else if (menuAxisLatch == 0) {
                menuAxisLatch = direction;
                selectWarningChoice(direction > 0 ? 1 : 0);
            }
            return true;
        }

        if (captureListener != null) {
            float left = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE));
            float right = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS));
            if (left > 0.8f && leftTriggerAxis <= 0.8f)
                completeButtonCapture(KeyEvent.KEYCODE_BUTTON_L2);
            else if (right > 0.8f && rightTriggerAxis <= 0.8f)
                completeButtonCapture(KeyEvent.KEYCODE_BUTTON_R2);
            leftTriggerAxis = left;
            rightTriggerAxis = right;
            return true;
        }

        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        int direction = y > 0.68f ? 1 : y < -0.68f ? -1 : 0;
        if (direction == 0) {
            if (Math.abs(y) < 0.28f) menuAxisLatch = 0;
        } else if (menuAxisLatch == 0) {
            menuAxisLatch = direction;
            changeSelection(direction);
        }
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
        if (unmappedWarningLayer != null) {
            handleUnmappedWarningKey(event.getKeyCode());
            return true;
        }
        if (captureListener != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_START) {
                cancelButtonCapture();
            } else if (FrostButtonMapper.isAssignableButton(code)) {
                completeButtonCapture(code);
            } else {
                // Arcane ignores unassignable button inputs during capture.
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
                back();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                adjustSelection(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                adjustSelection(1);
                break;
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
        setBackgroundColor(0xC9000000);
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

        updateInfo = new TextView(getContext());
        updateInfo.setTextSize(15);
        updateInfo.setTextColor(0xFFF0E3D1);
        updateInfo.setPadding(dp(12), dp(8), dp(12), dp(8));
        updateInfo.setBackground(background(0xAA30253B, 0, 9));
        updateInfo.setVisibility(GONE);
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(-1, -2);
        updateParams.bottomMargin = dp(6);
        panel.addView(updateInfo, updateParams);

        captureBanner = new TextView(getContext());
        captureBanner.setTextSize(14);
        captureBanner.setTextColor(0xFFFFE6AD);
        captureBanner.setGravity(Gravity.CENTER);
        captureBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        captureBanner.setBackground(background(0xFF48323F, 0, 9));
        captureBanner.setVisibility(GONE);
        captureBanner.setContentDescription("Waiting for a controller button; Start cancels");
        captureBanner.setOnClickListener(v -> cancelButtonCapture());
        LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(-1, dp(38));
        mappingNotice = new TextView(getContext());
        mappingNotice.setTextSize(14);
        mappingNotice.setTextColor(0xFFFFE6AD);
        mappingNotice.setGravity(Gravity.CENTER);
        mappingNotice.setPadding(dp(10), dp(7), dp(10), dp(7));
        mappingNotice.setBackground(background(0xFF3B2939, GOLD, 7));
        mappingNotice.setVisibility(GONE);

        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        panel.addView(captureBanner, captureParams);
        rowContainer = new LinearLayout(getContext());
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowContainer, new ScrollView.LayoutParams(-1, -2));
    }

    private void renderTabs() {
        if (updateInfo != null) {
            updateInfo.setVisibility("updates".equals(tabs.get(tabIndex).id)
                ? VISIBLE : GONE);
            updateInfo.setText("Installed: " + installedVersion +
                "\nLatest: " + latestVersion + "\n" + updateStatus);
        }
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TextView tab = new TextView(getContext());
            tab.setText(tabs.get(i).title);
            tab.setTextSize(18);
            tab.setPadding(dp(4), dp(1), dp(4), dp(1));
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
                if (captureListener == null && unmappedWarningLayer == null) selectTab(index);
            });
            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) params.leftMargin = dp(6);
            tabStrip.addView(tab, params);
        }
    }

    private void renderRows() {
        int restoreScroll = scroll.getScrollY();
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
            OptionRow row = new OptionRow(items.get(i).unmapped);
            row.setContent(items.get(i).title +
                (items.get(i).value.isEmpty() ? "" : ": " + items.get(i).value),
                items.get(i).description);
            row.setContentDescription(items.get(i).title +
                (items.get(i).value.isEmpty() ? "" : ": " + items.get(i).value));
            row.setOnClickListener(v -> {
                if (captureListener != null || unmappedWarningLayer != null) return;
                selectedRow = index;
                updateSelection();
                activateSelection();
            });
            LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(3);
            rowContainer.addView(row, rowParams);
            rowViews.add(row);
        }
        updateSelection();
        scroll.post(() -> {
            if (isOpen) scroll.scrollTo(0, restoreScroll);
        });
    }

    private void selectTab(int newIndex) {
        if (newIndex < 0 || newIndex >= tabs.size() || newIndex == tabIndex ||
            unmappedWarningLayer != null) return;
        attemptLeaveMapping(() -> {
            if (mappingMode && onLeavingMapping != null) onLeavingMapping.run();
            mappingMode = false;
            tabIndex = newIndex;
            selectedRow = 0;
            menuAxisLatch = 0;
            scroll.scrollTo(0, 0);
            refresh();
        });
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

    private void adjustSelection(int direction) {
        if (adapter == null || items.isEmpty()) return;
        adapter.onItemAdjusted(tabs.get(tabIndex).id, items.get(selectedRow).id, direction);
        if (isOpen) refresh();
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
        params.width = Math.max(1, Math.min(dp(820), w - dp(52)));
        params.height = Math.max(1, Math.min(dp(600), h - dp(44)));
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

private final class WarningOptionRow extends TextView {
        private final Paint warningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();

        WarningOptionRow() {
            super(FrostFrontendView.this.getContext());
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = getWidth() - dp(22);
            float y = getHeight() / 2f;
            float radius = dp(9);
            triangle.reset();
            triangle.moveTo(x, y - radius);
            triangle.lineTo(x + radius, y + radius * 0.8f);
            triangle.lineTo(x - radius, y + radius * 0.8f);
            triangle.close();
            warningPaint.setStyle(Paint.Style.FILL);
            warningPaint.setColor(0xFFFFC65C);
            canvas.drawPath(triangle, warningPaint);
            warningPaint.setColor(0xFF332317);
            warningPaint.setStrokeWidth(dp(2));
            warningPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(x, y - dp(3), x, y + dp(2), warningPaint);
            canvas.drawCircle(x, y + dp(5), dp(1), warningPaint);
        }
    }

    private final class OptionRow extends LinearLayout {
        final TextView nameView;
        final TextView valueView;
        final TextView detailView;
        private String details = "";

        OptionRow(boolean unmapped) {
            super(FrostFrontendView.this.getContext());
            setOrientation(LinearLayout.VERTICAL);
            setPadding(dp(10), dp(5), dp(10), dp(5));
            setMinimumHeight(dp(36));
            setClickable(true);
            setFocusable(false);

            LinearLayout top = new LinearLayout(FrostFrontendView.this.getContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            addView(top, new LinearLayout.LayoutParams(-1, -2));

            nameView = new TextView(FrostFrontendView.this.getContext());
            nameView.setTextSize(14);
            nameView.setTextColor(0xFFECE7EF);
            nameView.setGravity(Gravity.CENTER_VERTICAL);
            nameView.setSingleLine(true);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1f));

            valueView = new TextView(FrostFrontendView.this.getContext());
            valueView.setTextSize(13);
            valueView.setTextColor(0xFFE0CCAA);
            valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            valueView.setSingleLine(true);
            valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.leftMargin = dp(9);
            top.addView(valueView, vlp);

            if (unmapped) {
                TextView warning = new WarningOptionRow();
                top.addView(warning, new LinearLayout.LayoutParams(dp(30), dp(29)));
            }

            detailView = new TextView(FrostFrontendView.this.getContext());
            detailView.setTextSize(11);
            detailView.setTextColor(0xFFBDB1C8);
            detailView.setPadding(dp(2), dp(2), 0, 0);
            detailView.setVisibility(View.GONE);
            addView(detailView, new LinearLayout.LayoutParams(-1, -2));
        }

        void setContent(String text, String detail) {
            int sep = text.indexOf(": ");
            String title = sep < 0 ? text : text.substring(0, sep);
            String value = sep < 0 ? "" : text.substring(sep + 2);
            nameView.setText(title);
            valueView.setText(value);
            valueView.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            details = detail;
            detailView.setText(detail);
        }

        void setHighlighted(boolean selected) {
            nameView.setTextColor(selected ? 0xFFFFE1A2 : 0xFFECE7EF);
            valueView.setTextColor(selected ? 0xFFFFE1A2 : 0xFFE0CCAA);
            detailView.setVisibility(selected && !details.isEmpty()
                ? View.VISIBLE : View.GONE);
            setBackground(background(
                selected ? 0xFF433147 : 0x55201A26,
                selected ? 0xFFD0AA69 : 0x00000000, 8));
        }
    }

    private TextView makeText(String label, int size, int color, int fill) {
        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setBackground(background(fill, 0, 9));
        text.setPadding(dp(14), dp(10), dp(14), dp(10));
        return text;
    }

    private void dismissUnmappedWarning() {
        if (unmappedWarningLayer != null) removeView(unmappedWarningLayer);
        unmappedWarningLayer = null;
        warningKeepEditing = warningContinue = null;
        requestFocus();
    }

    private void confirmUnmappedWarning(boolean continueAnyway) {
        if (unmappedWarningLayer == null) return;
        dismissUnmappedWarning();
        Runnable pending = pendingExit;
        pendingExit = null;
        if (continueAnyway && pending != null) pending.run();
    }

    private void selectWarningChoice(int index) {
        warningSelection = Math.max(0, Math.min(1, index));
        if (warningKeepEditing != null && warningContinue != null) {
            TextView[] choices = {warningKeepEditing, warningContinue};
            for (int i = 0; i < choices.length; i++) {
                boolean selected = i == warningSelection;
                choices[i].setTextColor(selected ? 0xFFFFE1A2 : 0xFFEAE7EA);
                choices[i].setBackground(background(
                    selected ? 0xFF433147 : 0xFF292331,
                    selected ? 0xFFD0AA69 : 0xFF615064, 9));
            }
        }
    }


    private void showUnmappedWarning() {
        if (unmappedWarningLayer != null) return;
        StringBuilder names = new StringBuilder();
        for (String actionName : buttonMapper.unmappedActions()) {
            if (names.length() > 0) names.append("\n");
            names.append("• ").append(actionName);
        }
        if (names.length() == 0) {
            if (pendingExit != null) pendingExit.run();
            return;
        }

        
        warningSelection = 0;
        menuAxisLatch = 0;
        unmappedWarningLayer = new FrameLayout(getContext());
        unmappedWarningLayer.setBackgroundColor(0x99000000);
        unmappedWarningLayer.setClickable(true);
        unmappedWarningLayer.setFocusableInTouchMode(true);
        FrostFrontendView.this.addView(unmappedWarningLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout dialog = new LinearLayout(getContext());
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dp(20), dp(17), dp(20), dp(19));
        dialog.setBackground(background(0xFF251D2D, 0xFFD0AA69, 12));
        int screenW = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams dialogParams = new FrameLayout.LayoutParams(
            Math.min(dp(460), screenW - dp(56)), -2, Gravity.CENTER);
        unmappedWarningLayer.addView(dialog, dialogParams);

        TextView title = makeText("UNMAPPED CONTROLS", 19, 0xFFFFC65C, 0x00000000);
        title.setGravity(Gravity.CENTER);
        dialog.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = makeText(
            "These actions have no button assigned:\n\n" + names +
            "\n\nYou can continue, but those actions won't work in-game.",
            14, 0xFFEAE7EA, 0x00000000);
        dialog.addView(message, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(10);
        dialog.addView(actions, actionParams);
        warningKeepEditing = makeText("Keep Editing", 14, 0xFFFFFFFF, 0xFF433147);
        warningContinue = makeText("Continue Anyway", 14, 0xFFFFFFFF, 0xFF292331);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            TextView choice = i == 0 ? warningKeepEditing : warningContinue;
            choice.setGravity(Gravity.CENTER);
            choice.setOnClickListener(v -> confirmUnmappedWarning(index == 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(47), 1f);
            if (i == 1) lp.leftMargin = dp(9);
            actions.addView(choice, lp);
        }
        selectWarningChoice(0);
        unmappedWarningLayer.requestFocus();
    }


    private boolean handleUnmappedWarningKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                selectWarningChoice(0); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selectWarningChoice(1); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                confirmUnmappedWarning(warningSelection == 1); break;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_START:
                confirmUnmappedWarning(false); break;
            default: break;
        }
        return true;
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

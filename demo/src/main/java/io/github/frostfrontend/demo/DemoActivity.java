package io.github.frostfrontend.demo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.frostfrontend.FrostFrontendView;
import io.github.frostfrontend.FrostButtonMapper;
import io.github.frostfrontend.FrostSpeakerTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Isolated host-app demonstration of the reusable FrostFrontend library.
 * No game, original game assets, network access or Arcane app data involved.
 */
public final class DemoActivity extends Activity {
    private FrostFrontendView frontend;
    private SharedPreferences preferences;
    private TextView status;
    private FrostButtonMapper mapper;
    private final FrostSpeakerTest speakerTest = new FrostSpeakerTest();
    private boolean showingButtonMap, showingMovementSettings;
    private static final float[] AIM_SPEEDS = {0.6f, 0.9f, 1.2f, 1.5f, 1.8f};
    private static final String[] TOUCH_MODES = {"Auto", "On", "Off"};
    private static final String[] TOUCH_SIZES = {"Small", "Medium", "Large"};
    private static final String[] TOUCH_OPACITIES = {"Low", "Medium", "High"};
    private static final String[] TOUCH_POSITIONS = {"Edge", "Normal", "Inset"};
    private static final String[] DEADZONE_NAMES = {"Low", "Normal", "High"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("demo_settings", MODE_PRIVATE);
        mapper = new FrostButtonMapper(Arrays.asList(
            new FrostButtonMapper.Action("attack", "Attack", KeyEvent.KEYCODE_BUTTON_R2),
            new FrostButtonMapper.Action("alt_attack", "Alternate Attack", KeyEvent.KEYCODE_BUTTON_R1),
            new FrostButtonMapper.Action("weapon", "Switch Weapon", KeyEvent.KEYCODE_BUTTON_A),
            new FrostButtonMapper.Action("armour", "Swap Armour", KeyEvent.KEYCODE_BUTTON_B),
            new FrostButtonMapper.Action("throw", "Throw", KeyEvent.KEYCODE_BUTTON_X),
            new FrostButtonMapper.Action("prev", "Previous Throwable", KeyEvent.KEYCODE_BUTTON_Y),
            new FrostButtonMapper.Action("next", "Next Throwable", KeyEvent.KEYCODE_BUTTON_L1)
        ));
        mapper.load(preferences, "binding_");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF17121F);
        setContentView(root);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(dp(22), dp(22), dp(22), dp(22));

        TextView title = new TextView(this);
        title.setText("FrostFrontend");
        title.setTextSize(31);
        title.setTextColor(0xFFFFDFA2);
        title.setGravity(Gravity.CENTER);
        welcome.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Standalone Android library demo — no game included");
        subtitle.setTextSize(15);
        subtitle.setTextColor(0xFFECE7EF);
        subtitle.setGravity(Gravity.CENTER);
        welcome.addView(subtitle);

        Button open = new Button(this);
        open.setText("OPEN FROSTFRONTEND");
        open.setOnClickListener(v -> frontend.open());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
            -2, -2);
        openParams.topMargin = dp(20);
        welcome.addView(open, openParams);

        TextView instructions = new TextView(this);
        instructions.setText("Controller: Start open/close · L1/R1 tabs · D-pad navigate/adjust · A select · B back");
        instructions.setTextColor(0xFFCCBCCF);
        instructions.setTextSize(12);
        instructions.setGravity(Gravity.CENTER);
        welcome.addView(instructions);

        status = new TextView(this);
        status.setText("Demo ready. Settings are stored in this app only.");
        status.setTextColor(0xFFC09A62);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(14);
        welcome.addView(status, statusParams);

        root.addView(welcome, new FrameLayout.LayoutParams(-1, -1));

        frontend = new FrostFrontendView(this);
        root.addView(frontend, new FrameLayout.LayoutParams(-1, -1));
        frontend.setUpdateInfo("FrostFrontend Demo v0.5.0", "Not checked",
            "Standalone demo; update checks belong to the host game.");
        frontend.setTabs(Arrays.asList(
            new FrostFrontendView.Tab("graphics", "Graphics"),
            new FrostFrontendView.Tab("sound", "Sound"),
            new FrostFrontendView.Tab("controls", "Controls"),
            new FrostFrontendView.Tab("touch", "Touch Settings"),
            new FrostFrontendView.Tab("updates", "Updates")
        ));
        frontend.setBackListener(() -> {
            if (showingMovementSettings) {
                showingMovementSettings = false;
                return true;
            }
            return false;
        });
        frontend.setAdapter(new FrostFrontendView.Adapter() {
            @Override public List<FrostFrontendView.Item> itemsForTab(String tabId) {
                switch (tabId) {
                    case "graphics":
                        return Collections.singletonList(item("display", "Display Mode",
                            preferences.getString("display_mode", "original").equals("stretch")
                                ? "Stretch to Screen" : "Original 4:3",
                            preferences.getString("display_mode", "original").equals("stretch")
                                ? "Full widescreen · stretched" : "Unstretched · side bars"));
                    case "sound":
                        return Collections.singletonList(item("speaker_test",
                            "Speaker Test: Left → Right → Both", "",
                            "Listen to each speaker in order; game audio stays paused"));
                    case "controls":
                        if (showingButtonMap) {
                            List<FrostFrontendView.Item> rows = new ArrayList<>();
                            rows.add(item("map_back", "Back to Controls", "", ""));
                            for (FrostButtonMapper.Action action : mapper.actions()) {
                                int button = mapper.buttonFor(action.id);
                                rows.add(new FrostFrontendView.Item(
                                    "map:" + action.id, action.name,
                                    FrostButtonMapper.buttonName(button),
                                    "", button == FrostButtonMapper.UNMAPPED));
                            }
                            rows.add(item("invert", "Invert Right-Stick Y",
                                preferences.getBoolean("invert_aim_y", false) ? "On" : "Off",
                                "Flip vertical aim"));
                            rows.add(item("sensitivity", "Aim Sensitivity",
                                AIM_SPEEDS[index("aim_speed", 1, 4)] + "×",
                                "Right-stick speed"));
                            rows.add(item("map_reset", "Restore Default Controls", "",
                                "Restore original mappings"));
                            return rows;
                        }
                        if (showingMovementSettings) {
                            return Arrays.asList(
                                item("movement_back", "Back to Controls", "", ""),
                                item("directions", "Stick Directions",
                                    index("movement_mode", 0, 1) == 0 ? "8-way" : "4-way",
                                    "WASD · D-pad unchanged"),
                                item("deadzone", "Stick Deadzone",
                                    DEADZONE_NAMES[index("movement_deadzone", 1, 2)],
                                    "Physical + touch sticks"),
                                item("invert_move_x", "Invert Movement X",
                                    onOff("invert_move_x", false), ""),
                                item("invert_move_y", "Invert Movement Y",
                                    onOff("invert_move_y", false), ""),
                                item("movement_reset", "Reset Movement Settings", "",
                                    "Restore defaults"));
                        }
                        return Arrays.asList(
                            item("button_mapping", "Button Mapping", "", "Assign controller buttons"),
                            item("movement_settings", "Movement Settings", "",
                                "Stick directions, deadzone and inversion"));
                    case "touch":
                        return Arrays.asList(
                            item("touch_mode", "Touch Controls",
                                TOUCH_MODES[index("touch_mode", 0, 2)],
                                index("touch_mode", 0, 2) == 0 ? "Auto-hide with controller" :
                                index("touch_mode", 0, 2) == 1 ? "Always show" : "Hidden"),
                            item("touch_size", "Control Size",
                                TOUCH_SIZES[index("touch_size", 1, 2)], ""),
                            item("touch_opacity", "Opacity",
                                TOUCH_OPACITIES[index("touch_opacity", 1, 2)], ""),
                            item("touch_dpad", "Movement Pad",
                                preferences.getBoolean("touch_dpad", false) ? "D-pad" : "Joystick",
                                "Digital D-pad or analogue joystick"),
                            item("touch_relative_aim", "Touch Aiming",
                                preferences.getBoolean("touch_relative_aim", true) ? "Relative" : "Direct",
                                preferences.getBoolean("touch_relative_aim", true)
                                    ? "Swipe to aim · touch to fire" : "Tap the target to fire"),
                            item("touch_swap", "Sides",
                                preferences.getBoolean("touch_swap", false) ? "Swapped" : "Standard", ""),
                            item("touch_inset", "Position",
                                TOUCH_POSITIONS[index("touch_inset", 1, 2)], "Distance from edge"),
                            item("touch_reset", "Reset Touch Controls", "", "Restore defaults"));
                    case "updates":
                        return Arrays.asList(
                            item("check", "Check for Updates", "",
                                "Check the verified public Android release on GitHub"),
                            item("automatic", "Check Automatically",
                                onOff("updates_automatic", true),
                                "Check at startup without interrupting gameplay"));
                    default:
                        return Collections.emptyList();
                }
            }

            @Override public void onItemSelected(String tabId, String itemId) {
                adjust(tabId, itemId, 1);
            }

            @Override public void onItemAdjusted(String tabId, String itemId, int direction) {
                // Left/right changes values; action rows still require A.
                if (itemId.equals("display") || itemId.equals("sensitivity") ||
                    itemId.equals("directions") || itemId.equals("deadzone") ||
                    itemId.startsWith("invert") || itemId.equals("automatic") ||
                    itemId.startsWith("touch_") && !itemId.equals("touch_reset"))
                    adjust(tabId, itemId, direction);
            }

            private void adjust(String tabId, String itemId, int direction) {
                if ("sound".equals(tabId) && "speaker_test".equals(itemId)) {
                    if (speakerTest.start()) {
                        Toast.makeText(DemoActivity.this,
                            "Speaker test: LEFT, then RIGHT, then BOTH. Check media volume.",
                            Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(DemoActivity.this, "Speaker test is playing",
                            Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                if ("controls".equals(tabId)) {
                    if ("button_mapping".equals(itemId)) {
                        showingButtonMap = true;
                        showingMovementSettings = false;
                        frontend.enterButtonMapping(mapper, () -> showingButtonMap = false);
                        return;
                    }
                    if ("movement_settings".equals(itemId)) {
                        showingMovementSettings = true;
                        return;
                    }
                    if ("movement_back".equals(itemId)) {
                        showingMovementSettings = false;
                        return;
                    }
                    if ("map_back".equals(itemId)) {
                        frontend.requestMappingBack();
                        return;
                    }
                    if ("map_reset".equals(itemId)) {
                        mapper.resetDefaults();
                        mapper.save(preferences, "binding_");
                        preferences.edit().putBoolean("invert_aim_y", false)
                            .putInt("aim_speed", 1).apply();
                        return;
                    }
                    if ("movement_reset".equals(itemId)) {
                        preferences.edit().putInt("movement_mode", 0)
                            .putInt("movement_deadzone", 1)
                            .putBoolean("invert_move_x", false)
                            .putBoolean("invert_move_y", false).apply();
                        return;
                    }
                    if (itemId.startsWith("map:")) {
                        String actionId = itemId.substring(4);
                        frontend.beginButtonCapture(actionId,
                            new FrostFrontendView.ButtonCaptureListener() {
                                @Override public void onCaptured(int code) {
                                    mapper.assign(actionId, code);
                                    mapper.save(preferences, "binding_");
                                    frontend.refresh();
                                }
                                @Override public void onCancelled() {}
                            });
                        return;
                    }
                }

                SharedPreferences.Editor edit = preferences.edit();
                switch (itemId) {
                    case "display":
                        edit.putString("display_mode",
                            preferences.getString("display_mode", "original").equals("stretch")
                                ? "original" : "stretch"); break;
                    case "invert":
                        edit.putBoolean("invert_aim_y",
                            !preferences.getBoolean("invert_aim_y", false)); break;
                    case "sensitivity":
                        edit.putInt("aim_speed", cycle("aim_speed", 1, 5, direction)); break;
                    case "directions":
                        edit.putInt("movement_mode", cycle("movement_mode", 0, 2, direction)); break;
                    case "deadzone":
                        edit.putInt("movement_deadzone", cycle("movement_deadzone", 1, 3, direction)); break;
                    case "invert_move_x": case "invert_move_y":
                        edit.putBoolean(itemId, !preferences.getBoolean(itemId, false)); break;
                    case "touch_mode": case "touch_size": case "touch_opacity":
                    case "touch_inset":
                        edit.putInt(itemId, cycle(itemId,
                            itemId.equals("touch_mode") ? 0 : 1, 3, direction)); break;
                    case "touch_dpad": case "touch_swap":
                        edit.putBoolean(itemId, !preferences.getBoolean(itemId, false)); break;
                    case "touch_relative_aim":
                        edit.putBoolean(itemId,
                            !preferences.getBoolean(itemId, true)); break;
                    case "touch_reset":
                        edit.putInt("touch_mode", 0).putInt("touch_size", 1)
                            .putInt("touch_opacity", 1).putInt("touch_inset", 1)
                            .putBoolean("touch_dpad", false).putBoolean("touch_swap", false)
                            .putBoolean("touch_relative_aim", true); break;
                    case "automatic":
                        edit.putBoolean("updates_automatic",
                            !preferences.getBoolean("updates_automatic", true)); break;
                    case "check":
                        Toast.makeText(DemoActivity.this,
                            "Demo only: your game supplies its own update checker.",
                            Toast.LENGTH_SHORT).show();
                        break;
                    default: return;
                }
                edit.apply();
            }

            @Override public void onVisibilityChanged(boolean visible) {
                if (!visible) {
                    showingButtonMap = false;
                    showingMovementSettings = false;
                    speakerTest.stop();
                }
                status.setText(visible
                    ? "FrostFrontend open — game would be paused."
                    : "FrostFrontend closed — game would resume.");
            }
        });
    }

    private FrostFrontendView.Item item(String id, String title, String value, String detail) {
        return new FrostFrontendView.Item(id, title, value, detail);
    }

    private int index(String key, int fallback, int maximum) {
        return Math.max(0, Math.min(maximum, preferences.getInt(key, fallback)));
    }

    private String onOff(String key, boolean fallback) {
        return preferences.getBoolean(key, fallback) ? "On" : "Off";
    }

    private int cycle(String key, int fallback, int count, int direction) {
        return (preferences.getInt(key, fallback) + direction + count) % count;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (frontend != null && frontend.isOpen())
            return frontend.handleKeyEvent(event);
        if (frontend != null && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getRepeatCount() == 0) frontend.open();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (frontend != null && frontend.handleGenericMotionEvent(event))
            return true;
        return super.dispatchGenericMotionEvent(event);
    }

    @Override protected void onDestroy() {
        speakerTest.stop();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (frontend != null && frontend.isOpen()) frontend.back();
        else super.onBackPressed();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

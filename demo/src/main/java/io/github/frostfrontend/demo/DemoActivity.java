package io.github.frostfrontend.demo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
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
    private boolean showingButtonMap;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("demo_settings", MODE_PRIVATE);
        mapper = new FrostButtonMapper(Arrays.asList(
            new FrostButtonMapper.Action("attack", "Attack", KeyEvent.KEYCODE_BUTTON_R1),
            new FrostButtonMapper.Action("alt_attack", "Alternate Attack", KeyEvent.KEYCODE_BUTTON_R2),
            new FrostButtonMapper.Action("weapon", "Switch Weapon", KeyEvent.KEYCODE_BUTTON_A),
            new FrostButtonMapper.Action("armour", "Swap Armour", KeyEvent.KEYCODE_BUTTON_B),
            new FrostButtonMapper.Action("throw", "Throw", KeyEvent.KEYCODE_BUTTON_X),
            new FrostButtonMapper.Action("prev", "Previous Throwable", KeyEvent.KEYCODE_BUTTON_L1),
            new FrostButtonMapper.Action("next", "Next Throwable", KeyEvent.KEYCODE_BUTTON_Y)
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
        instructions.setText("Controller: Start to open · L1/R1 tabs · D-pad navigate · A select · B close");
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
        frontend.setTabs(Arrays.asList(
            new FrostFrontendView.Tab("graphics", "Graphics"),
            new FrostFrontendView.Tab("sound", "Sound"),
            new FrostFrontendView.Tab("controls", "Controls"),
            new FrostFrontendView.Tab("touch", "Touch Settings"),
            new FrostFrontendView.Tab("updates", "Updates")
        ));
        frontend.setAdapter(new FrostFrontendView.Adapter() {
            @Override public List<FrostFrontendView.Item> itemsForTab(String tabId) {
                switch (tabId) {
                    case "graphics":
                        return Collections.singletonList(new FrostFrontendView.Item(
                            "display", "Display Mode",
                            preferences.getBoolean("widescreen", false) ? "Stretch to Screen" : "Original 4:3",
                            "Demo setting only; no game output is altered"));
                    case "sound":
                        return Collections.singletonList(new FrostFrontendView.Item(
                            "speaker_test", "Speaker Test: Left → Right → Both", "",
                            "Play three short native stereo tones at your device media volume"));
                    case "controls":
                        if (showingButtonMap) {
                            List<FrostFrontendView.Item> mappingRows = new ArrayList<>();
                            mappingRows.add(new FrostFrontendView.Item(
                                "map_back", "Back to Controls", "", "Return to other controller settings"));
                            for (FrostButtonMapper.Action action : mapper.actions()) {
                                int code = mapper.buttonFor(action.id);
                                mappingRows.add(new FrostFrontendView.Item(
                                    "map:" + action.id, action.name,
                                    FrostButtonMapper.buttonName(code),
                                    code == FrostButtonMapper.UNMAPPED
                                        ? "Unmapped! Select to assign a button"
                                        : "Select and press a controller button to reassign"));
                            }
                            mappingRows.add(new FrostFrontendView.Item(
                                "map_reset", "Restore Default Buttons", "",
                                "Reset all gamepad assignments"));
                            return mappingRows;
                        }
                        return Arrays.asList(
                            new FrostFrontendView.Item(
                                "button_mapping", "Button Mapping", "",
                                "Reassign physical controller buttons"),
                            new FrostFrontendView.Item(
                                "invert", "Invert Aim Y",
                                preferences.getBoolean("invert", false) ? "On" : "Off",
                                "Demo setting: toggle vertical aiming"),
                            new FrostFrontendView.Item(
                                "sensitivity", "Aim Sensitivity",
                                new String[]{"Low", "Normal", "High"}[
                                    preferences.getInt("sensitivity", 1)],
                                "Demo setting: cycle aiming speed"));
                    case "touch":
                        return Arrays.asList(
                            new FrostFrontendView.Item(
                                "enabled", "Touch Controls",
                                preferences.getBoolean("touch_enabled", true) ? "On" : "Off",
                                "Demo setting: toggle on-screen controls"),
                            new FrostFrontendView.Item(
                                "movement", "Movement Pad",
                                preferences.getBoolean("dpad", false) ? "D-pad" : "Joystick",
                                "Demo setting: choose a movement pad"));
                    case "updates":
                        return Arrays.asList(
                            new FrostFrontendView.Item(
                                "automatic", "Check Automatically",
                                preferences.getBoolean("auto_updates", true) ? "On" : "Off",
                                "Demo setting: saved in this sample application"),
                            new FrostFrontendView.Item(
                                "check", "Check for Updates", "",
                                "Demo only: actual update checks belong to the host game"));
                    default:
                        return Collections.emptyList();
                }
            }

            @Override public void onItemSelected(String tabId, String itemId) {
                if ("sound".equals(tabId) && "speaker_test".equals(itemId)) {
                    if (speakerTest.start()) {
                        Toast.makeText(DemoActivity.this,
                            "Speaker test: LEFT, RIGHT, BOTH. Check your media volume.",
                            Toast.LENGTH_LONG).show();
                        status.setText("Native stereo speaker test playing.");
                    } else {
                        Toast.makeText(DemoActivity.this,
                            "Speaker test already playing.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if ("controls".equals(tabId)) {
                    if ("button_mapping".equals(itemId)) {
                        showingButtonMap = true;
                        return;
                    }
                    if ("map_back".equals(itemId)) {
                        showingButtonMap = false;
                        return;
                    }
                    if ("map_reset".equals(itemId)) {
                        mapper.resetDefaults();
                        mapper.save(preferences, "binding_");
                        status.setText("Controller buttons restored to defaults.");
                        return;
                    }
                    if (itemId.startsWith("map:")) {
                        String actionId = itemId.substring(4);
                        String actionName = actionId;
                        for (FrostButtonMapper.Action action : mapper.actions()) {
                            if (action.id.equals(actionId)) {
                                actionName = action.name;
                                break;
                            }
                        }
                        frontend.beginButtonCapture(actionName,
                            new FrostFrontendView.ButtonCaptureListener() {
                                @Override public void onCaptured(int code) {
                                    FrostButtonMapper.Change change = mapper.assign(actionId, code);
                                    mapper.save(preferences, "binding_");
                                    status.setText("Assigned " + actionId + " to " +
                                        FrostButtonMapper.buttonName(code));
                                    if (change.unmappedAction != null)
                                        Toast.makeText(DemoActivity.this,
                                            change.unmappedAction + " is now Unmapped.",
                                            Toast.LENGTH_LONG).show();
                                    frontend.refresh();
                                }

                                @Override public void onCancelled() {
                                    status.setText("Button assignment cancelled.");
                                }
                            });
                        return;
                    }
                }
                SharedPreferences.Editor edit = preferences.edit();
                switch (itemId) {
                    case "display":
                        edit.putBoolean("widescreen",
                            !preferences.getBoolean("widescreen", false));
                        break;
                    case "invert":
                        edit.putBoolean("invert", !preferences.getBoolean("invert", false));
                        break;
                    case "sensitivity":
                        edit.putInt("sensitivity",
                            (preferences.getInt("sensitivity", 1) + 1) % 3);
                        break;
                    case "enabled":
                        edit.putBoolean("touch_enabled",
                            !preferences.getBoolean("touch_enabled", true));
                        break;
                    case "movement":
                        edit.putBoolean("dpad", !preferences.getBoolean("dpad", false));
                        break;
                    case "automatic":
                        edit.putBoolean("auto_updates",
                            !preferences.getBoolean("auto_updates", true));
                        break;
                    case "check":
                        Toast.makeText(DemoActivity.this,
                            "Demo only: your game supplies its own update checker.",
                            Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        return;
                }
                edit.apply();
                status.setText("Last selected: " + tabId + " / " + itemId);
            }

            @Override public void onVisibilityChanged(boolean visible) {
                if (!visible) {
                    showingButtonMap = false;
                    speakerTest.stop();
                }
                status.setText(visible
                    ? "FrostFrontend open — game would be paused."
                    : "FrostFrontend closed — game would resume.");
            }
        });
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

    @Override protected void onDestroy() {
        speakerTest.stop();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (frontend != null && frontend.isOpen()) frontend.close();
        else super.onBackPressed();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

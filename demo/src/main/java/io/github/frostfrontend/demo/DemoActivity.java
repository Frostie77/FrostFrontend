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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("demo_settings", MODE_PRIVATE);

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
                    case "controls":
                        return Arrays.asList(
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

    @Override public void onBackPressed() {
        if (frontend != null && frontend.isOpen()) frontend.close();
        else super.onBackPressed();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

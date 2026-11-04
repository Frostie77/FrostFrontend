package io.github.frostfrontend;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Game-independent controller bindings. A physical button belongs to at most
 * one action. Reassigning an occupied button unmaps its previous action.
 *
 * The host game supplies the action names, default buttons and actual input
 * behaviour. The mapper never sends gameplay input or touches another app.
 */
public final class FrostButtonMapper {
    public static final int UNMAPPED = -1;

    public static final class Action {
        public final String id;
        public final String name;
        public final int defaultKeyCode;

        public Action(String id, String name, int defaultKeyCode) {
            if (id == null || id.trim().isEmpty() ||
                name == null || name.trim().isEmpty())
                throw new IllegalArgumentException("An action needs an ID and name");
            if (defaultKeyCode != UNMAPPED && !isAssignableButton(defaultKeyCode))
                throw new IllegalArgumentException("Unsupported default button: " + name);
            this.id = id;
            this.name = name;
            this.defaultKeyCode = defaultKeyCode;
        }
    }

    public static final class Change {
        /** Name of the action displaced by the new binding, or null. */
        public final String unmappedAction;

        Change(String unmappedAction) {
            this.unmappedAction = unmappedAction;
        }
    }

    private final List<Action> actions;
    private final Map<String, Integer> bindings = new LinkedHashMap<>();

    public FrostButtonMapper(List<Action> actions) {
        if (actions == null || actions.isEmpty())
            throw new IllegalArgumentException("At least one action is required");
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
        Set<String> ids = new HashSet<>();
        Set<Integer> buttons = new HashSet<>();
        for (Action action : actions) {
            if (action == null || !ids.add(action.id))
                throw new IllegalArgumentException("Null or duplicate action");
            if (action.defaultKeyCode != UNMAPPED &&
                !buttons.add(action.defaultKeyCode))
                throw new IllegalArgumentException("Duplicate default controller button");
        }
        resetDefaults();
    }

    public List<Action> actions() {
        return actions;
    }

    public int buttonFor(String actionId) {
        Integer code = bindings.get(actionId);
        if (code == null) throw new IllegalArgumentException("Unknown action: " + actionId);
        return code;
    }

    public Change assign(String actionId, int keyCode) {
        if (!bindings.containsKey(actionId))
            throw new IllegalArgumentException("Unknown action: " + actionId);
        if (keyCode != UNMAPPED && !isAssignableButton(keyCode))
            throw new IllegalArgumentException("Unsupported button: " + keyCode);
        String displaced = null;
        if (keyCode != UNMAPPED) {
            for (Action action : actions) {
                if (!action.id.equals(actionId) && buttonFor(action.id) == keyCode) {
                    bindings.put(action.id, UNMAPPED);
                    displaced = action.name;
                    break;
                }
            }
        }
        bindings.put(actionId, keyCode);
        return new Change(displaced);
    }

    public void resetDefaults() {
        bindings.clear();
        for (Action action : actions) bindings.put(action.id, action.defaultKeyCode);
    }

    public List<String> unmappedActions() {
        List<String> missing = new ArrayList<>();
        for (Action action : actions)
            if (buttonFor(action.id) == UNMAPPED) missing.add(action.name);
        return missing;
    }

    /** Save all bindings as one preferences transaction. */
    public void save(SharedPreferences preferences, String prefix) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Action action : actions)
            editor.putInt(prefix + action.id, buttonFor(action.id));
        editor.apply();
    }

    /** Restore previously saved mapping, rejecting invalid or duplicate buttons. */
    public void load(SharedPreferences preferences, String prefix) {
        resetDefaults();
        for (Action action : actions) {
            String key = prefix + action.id;
            if (!preferences.contains(key)) continue;
            int value = preferences.getInt(key, action.defaultKeyCode);
            if (value == UNMAPPED || isAssignableButton(value))
                assign(action.id, value);
        }
    }

    public static boolean isAssignableButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return true;
            default:
                return false; // Start, D-pad and Android Back stay reserved.
        }
    }

    public static String buttonName(int code) {
        switch (code) {
            case UNMAPPED: return "Unmapped";
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            default: return "Unknown";
        }
    }
}

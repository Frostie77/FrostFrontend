package io.github.frostfrontend;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Game-specific physical-button configuration for the shared FrostFrontend.
 *
 * A port defines its own action IDs, labels and defaults. The frontend keeps
 * the same Arcane-style mapping screen, conflict behaviour and unmapped warning.
 * Actual gameplay input remains the responsibility of the host game.
 *
 * This first version covers discrete gamepad buttons only. Analogue axes,
 * controller layouts and game-specific touch controls remain host-defined.
 */
public final class FrostControlProfile {
    private final String gameId;
    private final List<FrostButtonMapper.Action> actions;

    public FrostControlProfile(String gameId, List<FrostButtonMapper.Action> actions) {
        if (gameId == null || !gameId.matches("[A-Za-z0-9_.-]+"))
            throw new IllegalArgumentException("Use a stable game ID (letters, digits, _, -, .)");
        if (actions == null)
            throw new IllegalArgumentException("A control profile needs actions");

        this.gameId = gameId;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));

        // Reuse the mapper's validation of action IDs and unique defaults.
        new FrostButtonMapper(this.actions);
    }

    public static FrostControlProfile of(String gameId, FrostButtonMapper.Action... actions) {
        if (actions == null)
            throw new IllegalArgumentException("A control profile needs actions");
        return new FrostControlProfile(gameId, Arrays.asList(actions));
    }

    /** Stable identifier for the host game or a distinct control scheme. */
    public String gameId() {
        return gameId;
    }

    public List<FrostButtonMapper.Action> actions() {
        return actions;
    }

    /** A fresh set of defaults, which the host can then restore from storage. */
    public FrostButtonMapper createMapper() {
        return new FrostButtonMapper(actions);
    }

    /**
     * Storage prefix isolates profiles when a host supports multiple schemes.
     * A game should also use its own private SharedPreferences file.
     */
    public String preferencePrefix() {
        return "frost_control_" + gameId + "_";
    }

    /** Load the saved bindings into a new mapper for this profile. */
    public FrostButtonMapper load(SharedPreferences preferences) {
        return load(preferences, preferencePrefix());
    }

    /**
     * Allows an existing game to retain its old binding keys during migration.
     * E.g. the FrostFrontend demo has saved keys prefixed with "binding_".
     */
    public FrostButtonMapper load(SharedPreferences preferences, String prefix) {
        FrostButtonMapper mapper = createMapper();
        mapper.load(preferences, prefix);
        return mapper;
    }
}

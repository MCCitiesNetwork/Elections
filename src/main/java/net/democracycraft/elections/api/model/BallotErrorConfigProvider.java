package net.democracycraft.elections.api.model;

import net.democracycraft.elections.internal.util.config.DataFolder;
import net.democracycraft.elections.internal.util.yml.AutoYML; /**
 * Internal utility responsible for providing a shared {@link BallotError.Config}
 * instance. This provider is self-contained: it creates and loads the
 * underlying {@link AutoYML} store on demand when {@link #init()} is called,
 * so callers never have to deal with YAML handling directly.
 */
public final class BallotErrorConfigProvider {

    /** Backing configuration instance used by all {@link BallotError} lookups. */
    private static BallotError.Config CONFIG = new BallotError.Config();

    private BallotErrorConfigProvider() {
        // Utility class
    }

    /**
     * Initializes the provider by creating an {@link AutoYML} store for
     * {@link BallotError.Config} and loading (or creating) its contents.
     * <p>
     * The configuration is stored under
     * {@code <pluginDataFolder>/config/ballot-errors.yml}.
     */
    public static void init() {
        AutoYML<BallotError.Config> store = AutoYML.create(
                BallotError.Config.class,
                "ballot-errors",
                DataFolder.ERRORS,
                "Ballot error messages. Placeholders: %min%, %max%, %rank%."
        );
        BallotError.Config loaded = store.loadOrCreate(BallotError.Config::new);
        CONFIG = (loaded != null ? loaded : new BallotError.Config());
    }

    /**
     * Returns the currently active configuration instance.
     *
     * @return active {@link BallotError.Config}
     */
    static BallotError.Config get() {
        return CONFIG;
    }
}

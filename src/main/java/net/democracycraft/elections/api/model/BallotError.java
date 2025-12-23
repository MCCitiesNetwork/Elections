package net.democracycraft.elections.api.model;

import net.democracycraft.elections.internal.data.Dto;
import net.democracycraft.elections.internal.util.text.MiniMessageUtil;
import net.kyori.adventure.text.Component;

import java.util.function.Function;

/**
 * Represents validation or submission errors that can occur while working
 * with ballots.
 *<p>
 * Optimization:
 * - Converted to Enum to remove redundant 'Impl' record and 'ErrorCode' wrapper.
 * - Uses Function mapping for direct config field access (no switch statements).
 */
public enum BallotError {

    DUPLICATE_RANKING(config -> config.duplicateRanking),
    INSUFFICIENT_PREFERENCES(config -> config.insufficientPreferences),
    INVALID_RANK(config -> config.invalidRank),
    MISSING_RANK(config -> config.missingRank),
    MUST_SELECT_EXACTLY_MIN(config -> config.mustSelectExactlyMin),
    SUBMISSION_FAILED(config -> config.submissionFailed);

    private final Function<Config, String> messageMapper;

    BallotError(Function<Config, String> messageMapper) {
        this.messageMapper = messageMapper;
    }

    /**
     * Returns the configured message for this error code using the provided
     * configuration, parsed as a MiniMessage component.
     */
    public Component getMessage() {
        return MiniMessageUtil.parseOrPlain(errorString());
    }

    /**
     * Convenience method returning the configured MiniMessage string for this
     * error code using the internally loaded {@link Config} instance.
     */
    public String errorString() {
        return errorString(BallotErrorConfigProvider.get());
    }

    /**
     * Returns the configured MiniMessage string for this error code.
     */
    public String errorString(Config config) {
        return messageMapper.apply(config);
    }

    /**
     * YAML-backed configuration for ballot error messages.
     */
    public static class Config implements Dto {
        public String duplicateRanking = "<red>You have ranked the same rank more than once.</red>";
        public String insufficientPreferences = "<red>You must rank more candidates before submitting.</red>";
        public String invalidRank = "<red>One or more ranks are invalid.</red>";
        public String missingRank = "<red>A selected candidate is missing a rank.</red>";
        public String mustSelectExactlyMin = "<red>You must select exactly the required number of candidates.</red>";
        public String submissionFailed = "<red>Ballot submission failed. Please try again.</red>";

        public String yamlHeader = "BallotError configuration. Placeholders: %min%, %max%, %rank%.";
    }
}
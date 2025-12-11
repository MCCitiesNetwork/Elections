package net.democracycraft.elections.src.util.export;

import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.model.Vote;
import net.democracycraft.elections.src.data.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class that renders an {@link Election} into a human friendly Markdown document.
 * <p>
 * This formatter is intended for use when exporting election data to external systems such as
 * GitHub Gists. All content is generated in English so that it is readable for a broad audience.
 */
public final class ElectionMarkdownFormatter {

    private ElectionMarkdownFormatter() {
        // Utility class
    }

    /**
     * Renders the given {@link Election} as a Markdown document.
     *
     * @param election           the election snapshot to render (must not be {@code null})
     * @param voterNameProvider  function used to resolve voter identifiers to display names
     *                           when describing ballots; may be {@code null} if names should
     *                           not be resolved
     * @param includeVoterInBallots whether to include voter information in the ballots section
     * @return Markdown string representing the election in a human readable format
     */
    public static String toMarkdown(
            Election election,
            Function<Integer, String> voterNameProvider,
            boolean includeVoterInBallots
    ) {
        Objects.requireNonNull(election, "election");

        StringBuilder sb = new StringBuilder();

        // Title
        sb.append("# Election: ").append(nullToUnknown(election.getTitle())).append("\n\n");

        // Basic info
        sb.append("**ID:** ").append(election.getId()).append("  \n");
        sb.append("**Status:** ").append(election.getStatus()).append("  \n");
        sb.append("**System:** ").append(election.getSystem()).append("  \n");
        sb.append("**Minimum selections:** ").append(election.getMinimumVotes()).append("  \n");
        sb.append("**Total registered voters:** ").append(election.getVoterCount()).append("  \n\n");

        // Timing
        sb.append("## Timing\n\n");
        sb.append("- Created at: ").append(formatAuditTimestamp(election.getCreatedAt())).append("\n");
        sb.append("- Closes at: ").append(formatAuditTimestampOrNotConfigured(election.getClosesAt())).append("\n");
        sb.append("- Configured duration: ").append(formatHumanDuration(election.getDurationDays(), election.getDurationTime())).append("\n\n");

        // Requirements
        sb.append("## Voter requirements\n\n");
        formatRequirements(sb, election);

        // Candidates with vote summary
        sb.append("## Candidates\n\n");
        List<Candidate> candidates = election.getCandidates();
        List<Vote> ballots = election.getBallots();

        // Build candidate id -> name map for later lookups in ballots detail
        Map<Integer, String> candidateNameById = new LinkedHashMap<>();
        if (candidates != null) {
            for (Candidate c : candidates) {
                candidateNameById.put(c.getId(), c.getName());
            }
        }

        // Build vote counts per candidate id
        Map<Integer, Integer> voteCounts = new HashMap<>();
        int totalVotes = 0;
        if (ballots != null) {
            for (Vote vote : ballots) {
                if (vote.getSelections() == null) continue;
                for (Integer cid : vote.getSelections()) {
                    if (cid == null) continue;
                    voteCounts.merge(cid, 1, Integer::sum);
                    totalVotes++;
                }
            }
        }

        if (candidates == null || candidates.isEmpty()) {
            sb.append("No candidates have been registered for this election.\n\n");
        } else {
            int index = 1;
            for (Candidate candidate : candidates) {
                int candidateVotes = voteCounts.getOrDefault(candidate.getId(), 0);
                String bar = buildAsciiBar(candidateVotes, totalVotes);
                sb.append("- Candidate ").append(index++).append(": ")
                        .append(nullToUnknown(candidate.getName()))
                        .append(" (ID: ").append(candidate.getId()).append(")");
                if (candidate.getParty() != null && !candidate.getParty().isBlank()) {
                    sb.append(" — Party: ").append(candidate.getParty());
                }
                sb.append("\n  Votes: ").append(candidateVotes).append(" / ").append(totalVotes)
                        .append(" ").append(bar)
                        .append("\n");
            }
            sb.append("\n");
        }

        // Ballots overview (reutiliza la lista ballots ya calculada)
        sb.append("## Ballots overview\n\n");
        if (ballots == null || ballots.isEmpty()) {
            sb.append("No ballots have been submitted yet.\n\n");
        } else {
            sb.append("Total ballots submitted: ").append(ballots.size()).append("\n\n");

            // Optional detailed ballots section
            if (includeVoterInBallots) {
                sb.append("### Ballots detail\n\n");
                int index = 1;
                for (Vote vote : ballots) {
                    sb.append("- Ballot ").append(index++).append(": ");
                    Integer voterId = vote.getVoterId();
                    if (voterId != null) {
                        String name = (voterNameProvider != null) ? voterNameProvider.apply(voterId) : null;
                        if (name != null && !name.isBlank()) {
                            sb.append("Voter ").append(name).append(" (ID: ").append(voterId).append(")");
                        } else {
                            sb.append("Voter ID ").append(voterId);
                        }
                    } else {
                        sb.append("Voter: unknown");
                    }
                    if (vote.getSelections() != null && !vote.getSelections().isEmpty()) {
                        String joined = vote.getSelections().stream()
                                .map(cid -> {
                                    String cname = candidateNameById.get(cid);
                                    return (cname != null && !cname.isBlank())
                                            ? (cname + " (" + cid + ")")
                                            : String.valueOf(cid);
                                })
                                .collect(Collectors.joining(", "));
                        sb.append(" — Selections: ").append(joined);
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        // Status history
        List<StatusChangeDto> changes = election.getStatusChanges();
        sb.append("## Status history\n\n");
        if (changes == null || changes.isEmpty()) {
            sb.append("No status changes recorded.\n");
        } else {
            for (StatusChangeDto change : changes) {
                sb.append(renderStatusChange(change)).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Renders a single {@link StatusChangeDto} entry as a human-friendly, auditable
     * Markdown list item.
     *
     * <p>Example output:</p>
     * <pre>
     * - 2025-11-24 22:03:45 — REQUIREMENTS_CHANGED — by Alepando (perms=0, minutes=0)
     * </pre>
     *
     * @param change status change descriptor (must not be {@code null})
     * @return markdown line starting with "- " describing the change
     */
    private static String renderStatusChange(StatusChangeDto change) {
        if (change == null) {
            return "- (unknown change)";
        }

        StringBuilder line = new StringBuilder("- ");

        line.append("Status change");

        String actor = change.actor();
        if (actor != null && !actor.isBlank()) {
            line.append(" by ").append(actor.trim());
        } else {
            line.append(" by (unknown)");
        }

        if (change.type() != null) {
            line.append(": ").append(change.type());
        } else {
            line.append(": (unknown type)");
        }

        String rawDetails = change.details();
        String formattedDetails = (rawDetails != null && !rawDetails.isBlank())
                ? formatStatusDetails(rawDetails)
                : null;
        if (formattedDetails != null && !formattedDetails.isBlank()) {
            line.append(" — changed: ").append(formattedDetails);
        }

        line.append(" — at ").append(formatAuditTimestamp(change.at()));

        return line.toString();
    }

    /**
     * Formats the {@link TimeStampDto} in a compact ISO-like form suitable for
     * human reading and log-style audits, e.g. {@code 2025-11-24 22:03:45}.
     */
    private static String formatAuditTimestamp(TimeStampDto ts) {
        if (ts == null || ts.date() == null || ts.time() == null) {
            return "unknown";
        }
        DateDto date = ts.date();
        TimeDto time = ts.time();
        return String.format(
                "%04d-%02d-%02d %02d:%02d:%02d",
                date.year(),
                date.month(),
                date.day(),
                time.hour(),
                time.minute(),
                time.second()
        );
    }

    /**
     * Same as {@link #formatAuditTimestamp(TimeStampDto)} but returns a
     * human-friendly placeholder when the timestamp is not configured.
     */
    private static String formatAuditTimestampOrNotConfigured(TimeStampDto ts) {
        if (ts == null) {
            return "not configured";
        }
        return formatAuditTimestamp(ts);
    }

    /**
     * Formats the configured election duration in a human-friendly way.
     * Examples:
     * <ul>
     *     <li>"not configured" when both components are null</li>
     *     <li>"2 days"</li>
     *     <li>"3 days, 4 hours 30 minutes"</li>
     *     <li>"4 hours 15 minutes"</li>
     * </ul>
     */
    private static String formatHumanDuration(Integer days, TimeDto time) {
        if (days == null && time == null) {
            return "not configured";
        }

        StringBuilder builder = new StringBuilder();

        if (days != null && days > 0) {
            builder.append(days).append(" day");
            if (days != 1) {
                builder.append('s');
            }
        }

        if (time != null) {
            int hours = time.hour();
            int minutes = time.minute();

            if (hours > 0 || minutes > 0) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                if (hours > 0) {
                    builder.append(hours).append(" hour");
                    if (hours != 1) {
                        builder.append('s');
                    }
                    if (minutes > 0) {
                        builder.append(' ');
                    }
                }
                if (minutes > 0) {
                    builder.append(minutes).append(" minute");
                    if (minutes != 1) {
                        builder.append('s');
                    }
                }
            }
        }

        if (builder.isEmpty()) {
            return "0 minutes";
        }

        return builder.toString();
    }

    /**
     * Formats the free-form {@code details} string into something more legible while
     * preserving all key=value pairs so it remains auditable.
     * <p>
     * Example: {@code "perms=0,minutes=0"} becomes
     * {@code "perms=0, minutes=0"}.
     * </p>
     */
    private static String formatStatusDetails(String details) {
        String trimmed = details.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String[] parts = trimmed.split(",");
        return java.util.Arrays.stream(parts)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(", "));
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Builds a high-precision smooth Unicode progress bar.
     * <p>Example output: ▊▊▊▊▊▊▊▊▊▊▌         (52.5%)</p>
     *
     * @param candidateVotes Votes for the specific candidate
     * @param totalVotes     Total votes in the election
     * @return A smooth progress bar string of length 20
     */
    private static String buildAsciiBar(int candidateVotes, int totalVotes) {
        int width = 20;
        String[] blocks = {" ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█"};

        if (totalVotes <= 0) {
            return " ".repeat(width);
        }

        double percentage = (double) candidateVotes / totalVotes;
        double preciseWidth = percentage * width;

        int fullBlocks = (int) preciseWidth;
        double remainder = preciseWidth - fullBlocks;

        int partialBlockIndex = (int) (remainder * (blocks.length - 1));

        StringBuilder bar = new StringBuilder();

        bar.append(blocks[8].repeat(Math.max(0, fullBlocks)));

        if (fullBlocks < width) {
            bar.append(blocks[partialBlockIndex]);
        }


        while (bar.length() < width) {
            bar.append("░"); // Using light shade for empty track looks better than space
        }

        return bar.toString();
    }

    private static String nullToUnknown(String value) {
        return (value == null || value.isBlank()) ? "(unknown)" : value;
    }

    private static String formatTimestamp(TimeStampDto ts) {
        if (ts == null) {
            return "(unknown)";
        }
        return ts.toString();
    }

    private static String formatNullableTimestamp(TimeStampDto ts) {
        return ts == null ? "(not configured)" : formatTimestamp(ts);
    }

    /**
     * Appends a human-readable, auditable description of the voter requirements
     * for the given election.
     *
     * <p>If the election has no requirements configured, a short sentence is
     * written. Otherwise, a bullet list describes each requirement in plain
     * English while keeping the underlying values visible for auditing.</p>
     */
    private static void formatRequirements(StringBuilder sb, Election election) {
        if (election.getRequirements() == null) {
            sb.append("No specific voter requirements configured.\n\n");
            return;
        }

        var requirements = election.getRequirements();

        List<String> permissions = requirements.permissions();
        long minMinutes = requirements.minActivePlaytimeMinutes();

        boolean hasPermissions = permissions != null && !permissions.isEmpty();
        boolean hasPlaytime = minMinutes > 0;

        if (!hasPermissions && !hasPlaytime) {
            sb.append("No specific voter requirements configured.\n\n");
            return;
        }

        sb.append("The following conditions must be met in order to be allowed to vote:\n\n");

        if (hasPermissions) {
            sb.append("- Must have <b>all</b> of the following permission nodes:\n");
            for (String perm : permissions) {
                if (perm == null || perm.isBlank()) {
                    continue;
                }
                sb.append("  - `").append(perm.trim()).append("`\n");
            }
            sb.append("\n");
        }

        if (hasPlaytime) {
            long hours = minMinutes / 60L;
            long minutesRemainder = minMinutes % 60L;

            sb.append("- Must have at least ");
            if (hours > 0) {
                sb.append(hours).append(" hour");
                if (hours != 1) {
                    sb.append('s');
                }
                if (minutesRemainder > 0) {
                    sb.append(' ');
                }
            }
            if (minutesRemainder > 0 || hours == 0) {
                sb.append(minutesRemainder).append(" minute");
                if (minutesRemainder != 1) {
                    sb.append('s');
                }
            }
            sb.append(" of active playtime on record");
            sb.append(" (" ).append(minMinutes).append(" minutes total");
            sb.append(").\n\n");
        }
    }
}

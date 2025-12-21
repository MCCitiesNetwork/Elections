package net.democracycraft.elections.src.util.export;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Utility class to format ballot data into a robust CSV format compatible with
 * Microsoft Excel, Google Sheets, and LibreOffice.
 */
public final class BallotCsvFormatter {

    private static final String CSV_SEPARATOR = ",";
    private static final String BOM_UTF8 = "\uFEFF";

    private BallotCsvFormatter() {
        // Static utility class
    }

    /**
     * Converts a collection of ballots into a CSV string with dynamic columns.
     * Includes a UTF-8 BOM and explicit separator instruction for maximum compatibility.
     *
     * @param ballots List of ballot maps containing election data.
     * @param isAdmin Whether to include sensitive voter identification.
     * @return A formatted CSV string ready for export.
     */
    public static String toCsv(List<Map<String, Object>> ballots, boolean isAdmin) {
        List<Map<String, Object>> safeBallots = (ballots == null) ? List.of() : ballots;

        int maxSelections = calculateMaxSelections(safeBallots);
        StringBuilder sb = new StringBuilder();

        // Enforce UTF-8 encoding and explicit separator for Excel
        sb.append(BOM_UTF8);
        // Excel-specific separator declaration
        sb.append("sep=").append(CSV_SEPARATOR).append("\n");

        // Construct Header Row
        StringJoiner header = new StringJoiner(CSV_SEPARATOR);
        header.add("Ballot ID");
        if (isAdmin) {
            header.add("Voter Name");
        }
        for (int i = 1; i <= maxSelections; i++) {
            header.add("Preference " + i);
        }
        sb.append(header).append("\n");

        // Construct Data Rows
        int index = 1;
        for (Map<String, Object> ballot : safeBallots) {
            StringJoiner row = new StringJoiner(CSV_SEPARATOR);

            row.add(String.valueOf(index++));

            if (isAdmin) {
                Object voterObj = ballot.get("voter");
                row.add(escapeCsv(voterObj != null ? voterObj.toString() : "Unknown"));
            }

            List<?> selections = getSelectionsList(ballot);
            for (int i = 0; i < maxSelections; i++) {
                if (i < selections.size()) {
                    row.add(escapeCsv(String.valueOf(selections.get(i))));
                } else {
                    row.add(""); // Fill empty preference slots
                }
            }
            sb.append(row).append("\n");
        }

        return sb.toString();
    }

    /**
     * Determines the required number of columns based on the ballot with the most votes.
     */
    private static int calculateMaxSelections(List<Map<String, Object>> ballots) {
        return ballots.stream()
                .map(b -> getSelectionsList(b).size())
                .max(Integer::compare)
                .orElse(1);
    }

    /**
     * Safely extracts the selections list from the ballot map.
     */
    private static List<?> getSelectionsList(Map<String, Object> ballot) {
        Object obj = ballot.get("selections");
        return (obj instanceof List<?>) ? (List<?>) obj : List.of();
    }

    /**
     * Ensures data integrity by escaping special characters.
     * Wraps values in quotes and escapes internal double-quotes.
     */
    private static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(CSV_SEPARATOR) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
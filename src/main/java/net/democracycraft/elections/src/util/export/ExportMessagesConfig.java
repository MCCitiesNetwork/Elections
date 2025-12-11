package net.democracycraft.elections.src.util.export;

import java.io.Serializable;

/**
 * Configuration backing bean for all user-facing export messages.
 *
 * <p>Values are MiniMessage-capable strings and support simple %placeholders%
 * replaced at runtime (e.g. %actor%, %election_id%, %url%). The messages
 * are loaded and saved via {@code AutoYML} under the {@code EXPORT_MESSAGES}
 * data folder.</p>
 */
public class ExportMessagesConfig implements Serializable {

    /** Generic lookup failure (e.g. when fetching an election). Placeholders: %error%. */
    public String errorLookupFailed = "<red>Lookup failed: %error%</red>";
    /** Used when an election cannot be found. */
    public String errorElectionNotFound = "<red>Election not found.</red>";
    /** Generic no-permission message. */
    public String errorNoPermission = "<red>You don't have permission.</red>";
    /** Local save failure. Placeholders: %error%. */
    public String errorCouldNotSaveLocal = "<red>Could not save locally: %error%</red>";
    /** Remote publish failed but a local copy was saved. Placeholders: %file%. */
    public String errorRemoteFailedLocalSaved = "<yellow>Remote publish failed, saved to local queue: %file%</yellow>";

    /** User export: local queue saved successfully. Placeholders: %file%. */
    public String userLocalSaved = "<green>Saved to local queue:</green> %file%";
    /** User export: published successfully. Placeholders: %url%. */
    public String userPublished = "<green>Published:</green> <click:open_url:'%url%'><underlined>%url%</underlined></click>";

    /** Admin export: local queue saved. Placeholders: %file%. */
    public String adminLocalSaved = "<green>Saved to local queue (admin):</green> %file%";
    /** Admin export: published successfully. Placeholders: %url%. */
    public String adminPublished = "<gold>Published (admin):</gold> <click:open_url:'%url%'><underlined>%url%</underlined></click>";

    /** Queue dispatch summary. Placeholders: %total%, %uploaded%, %skipped%, %failed%. */
    public String dispatchProcessed = "<green>Queue processed:</green> total=%total%, uploaded=%uploaded%, skipped=%skipped%, failed=%failed%";
    /** Queue dispatch failure. Placeholders: %error%. */
    public String dispatchFailed = "<red>Queue processing failed:</red> %error%";

    /** Ballots export: local save. Placeholders: %file%, %count%. */
    public String ballotsSavedLocal = "<green>Saved ballots to:</green> %file% (<yellow>%count%</yellow>)";
    /** Ballots export: local save failed. Placeholders: %error%. */
    public String ballotsLocalFailed = "<red>Ballots local save failed:</red> %error%";
    /** Ballots export: published online. Placeholders: %url%, %count%. */
    public String ballotsPublished = "<green>Published ballots:</green> <click:open_url:'%url%'><underlined>%url%</underlined></click> (%count%)";
    /** Ballots export: publish failed. Placeholders: %error%. */
    public String ballotsPublishFailed = "<red>Ballots publish failed:</red> %error%";

    /** Admin ballots export: local save. Placeholders: %file%, %count%. */
    public String ballotsAdminSavedLocal = "<green>Saved ballots (admin) to:</green> %file% (%count%)";
    /** Admin ballots export: local save failed. Placeholders: %error%. */
    public String ballotsAdminLocalFailed = "<red>Ballots (admin) local save failed:</red> %error%";
    /** Admin ballots export: published online. Placeholders: %url%, %count%. */
    public String ballotsAdminPublished = "<green>Published ballots (admin):</green> <click:open_url:'%url%'><underlined>%url%</underlined></click> (%count%)";
    /** Admin ballots export: publish failed. Placeholders: %error%. */
    public String ballotsAdminPublishFailed = "<red>Ballots (admin) publish failed:</red> %error%";

    /** Delete subcommand: ID argument missing. */
    public String deleteIdRequired = "<red>You must specify an ID to delete.</red>";
    /** Delete subcommand: feature unsupported. Placeholders: %id%. */
    public String deleteUnsupported = "<yellow>Delete is not supported for exported resources (ID %id%).</yellow>";

    /** Ballots usage line for non-admin. Placeholders: %label%. */
    public String ballotsUsage = "<gray>Usage:</gray> /%label% export ballots &lt;local|online&gt; &lt;electionId&gt;";
    /** Ballots usage line for admin. Placeholders: %label%. */
    public String ballotsAdminUsage = "<gray>Usage:</gray> /%label% export ballots admin &lt;local|online&gt; &lt;electionId&gt;";
    /** Ballots invalid mode message. */
    public String ballotsInvalidMode = "<red>Invalid mode. Use 'local' or 'online'.</red>";

    /** Ballots local export: directory creation failed. */
    public String ballotsDirectoryCreateFailed = "<red>Could not create ballots export directory.</red>";

    /**
     * Returns the multi-line header written at the top of {@code export-messages.yml}
     * when it is first created.
     *
     * <p>The header documents all placeholders that may appear in the values
     * of this configuration so that server administrators can safely customise
     * the messages without guessing their meaning.</p>
     */
    public static String defaultHeader() {
        return String.join("\n",
                "Export messages configuration",
                "Values in this file support MiniMessage formatting and the following placeholders:",
                "",
                "Global placeholders:",
                "  %error%          - Error message or exception summary (for lookup / IO failures).",
                "",
                "User/admin export:",
                "  %url%            - Public URL of the exported election (GitHub Gist or other service).",
                "  %file%           - Local file name used when exports fall back to local queue.",
                "",
                "Dispatch (queue processing):",
                "  %total%          - Total number of queued exports that were examined.",
                "  %uploaded%       - Number of queued exports successfully uploaded.",
                "  %skipped%        - Number of queued exports skipped (already exported or invalid).",
                "  %failed%         - Number of queued exports that failed to upload.",
                "",
                "Ballots exports (user and admin):",
                "  %url%            - Public URL of the ballots export.",
                "  %file%           - Local JSON file name that was written.",
                "  %count%          - Number of ballots included in the export.",
                "",
                "Delete command:",
                "  %id%             - Identifier of the resource / election passed to the delete subcommand.",
                "",
                "Ballots subcommand usage:",
                "  %label%          - Root command label used to invoke the plugin (usually 'elections').",
                "",
                "You may remove or recolour any text in these messages, but keep placeholders intact,",
                "including the surrounding '%' characters, so that runtime values can be substituted correctly.");
    }
}

package net.democracycraft.elections.src.util.export.github;

import java.io.Serializable;

/**
 * Configuration backing file for the GitHub Gist export service.
 *
 * Instances of this class are persisted via {@code AutoYML} under the plugin data folder.
 */
public class GitHubGistConfig implements Serializable {

    /** Whether GitHub Gist export is enabled. */
    public boolean enabled = true;

    /**
     * Personal access token used to authenticate against the GitHub API.
     * <p>
     * The token must have permissions to create gists for the target account.
     */
    public String personalAccessToken = "";

    /** Base API URL (defaults to https://api.github.com). */
    public String apiBase = "https://api.github.com";

    /** Whether created gists should be public. */
    public boolean publicGists = false;
}

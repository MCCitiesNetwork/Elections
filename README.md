# Elections — In‑Game Usage Guide

This guide shows players and staff how to run elections entirely in‑game: commands, permissions, menus, voting, exports, and health checks.

Quick tip: the base command is `/elections` (alias: `/democracyelections`).

## Installation (server owner)
1) Drop the plugin jar into `plugins/` and start the server once.
2) Edit `plugins/Elections/config.yml`:
   - `mysql.*` — set host, port, database, user, password, and SSL.
   - `pastegg.apiKey` (optional) — set if you want to delete pastes via command.
   - Sweep intervals under `elections.*` (auto‑close and deleted purge).
3) Restart. The plugin creates missing config keys with defaults.
4) Grant permissions using your permissions plugin (LuckPerms, etc.).

## Permissions
- `elections.user` (default: true)
  - Regular player actions (open polls, standard exports).
- `elections.manager` (default: op)
  - Full in‑game management: open Manager UI, create/edit/open/close elections, admin exports, delete pastes, health.
- `elections.admin` (default: op)
  - Broader admin umbrella; treated as manager where relevant.
- `elections.health` (default: op)
  - Allows `/elections health`.
- `elections.permissions.reload` (default: op)
  - Allows `/elections reloadperms`.
- `elections.paste` (optional)
  - Extra gate for paste deletion; otherwise `elections.manager`/`elections.admin` also work.

Note: Check your `plugin.yml` for final defaults in your build.

## Commands (in‑game)
- `/elections` or `/elections manager`
  - Opens the Elections Manager (GUI). Must be executed by a player. Requires `elections.manager`.
- `/elections export <id>`
  - Exports an election snapshot to paste.gg (no voter names). Requires `elections.user`.
- `/elections export admin <id>`
  - Admin export (includes voter names embedded in ballots). Requires `elections.manager` or `elections.admin`.
- `/elections export delete <pasteId> confirm`
  - Deletes a paste at paste.gg if your API key has rights. Requires `elections.manager`/`elections.paste`/`elections.admin`.
- `/elections reloadperms`
  - Reloads the YAML permission nodes filter used by the plugin. Requires `elections.permissions.reload`.
- `/elections health`
  - Prints basic health stats (counts, DB latency, config warnings). Requires `elections.health`.

Tab completion:
- After `/elections`, you’ll see `export` and, if you’re a manager, also `manager`, `health`, `reloadperms`.
- For `export`, IDs and sub‑options (`admin`, `delete`) are suggested contextually.

## Staff workflow (Manager UI)
1) Open the Manager: `/elections`.
2) Create or edit an election:
   - Title
   - Voting System:
     - `BLOCK` — players must select exactly the configured minimum number of candidates.
     - `PREFERENTIAL` — players order candidates by preference (no duplicates) and must meet the minimum.
   - Minimum Votes — interpreted per system (see above).
   - Duration — days and time (H:M:S). When set, elections auto‑close after the configured duration.
   - Voter Requirements — one or both:
     - Specific permission nodes the player must have.
     - Minimum active playtime (minutes).
   - Ballot Mode — UI presentation for ballots.
3) Candidates — add or remove candidates as needed.
4) Polls (booths) — define locations where players can cast ballots:
   - Choose “Define Poll” and right‑click a block/head in the world to register the booth.
   - “Undefine Poll” to remove it; click the same block.
   - Booth locations are globally unique: two different elections cannot use the same block. If you try, you’ll get an error telling you which election already owns that location (ID and title).
5) Open/Close — when ready, open the election. It will auto‑close if a duration is set, or you can close manually.

## Player workflow (voting)
1) Go to a defined poll (booth) and right‑click the block/head.
2) If you have `elections.user` and meet any configured requirements (extra nodes or playtime), the ballot UI opens.
3) Fill your ballot:
   - `BLOCK`: select exactly the required number of candidates.
   - `PREFERENTIAL`: order candidates by preference (no duplicates), meeting the minimum.
4) Submit the ballot. You can’t vote more than once per election; duplicates are blocked.

## Exports
- Standard: `/elections export <id>` — posts JSON snapshot to paste.gg (no voter names) and returns the view URL.
- Admin: `/elections export admin <id>` — includes voter names; restricted to managers/admins.
- Delete: `/elections export delete <pasteId> confirm` — removes an existing paste when your API key allows it.

Notes:
- Set `pastegg.apiKey` in config to enable authorized deletions; otherwise delete will likely fail.
- Successful exports are recorded in the election’s status log.

## Health
- `/elections health` shows:
  - Count of elections (open/closed/deleted), voters, ballots.
  - Auto‑close sweep interval.
  - Current DB latency (quick `SELECT 1`).
  - Warnings for common misconfigurations (e.g., missing paste.gg API key).

## Configuration quick reference
In `config.yml`:
- `mysql.host`, `mysql.port`, `mysql.database`, `mysql.user`, `mysql.password`, `mysql.useSSL` — database connection.
- `elections.autoCloseSweepSecods` — seconds between auto‑close sweeps (typo kept for compatibility).
- `elections.deletedPurgeSweepSeconds` — how often to purge elections marked DELETED beyond retention.
- `elections.deletedRetentionDays` — retention for DELETED elections before purge.
- `pastegg.apiBase`, `pastegg.viewBase`, `pastegg.apiKey` — paste.gg endpoints and key.

> MariaDB compatibility: You can point the above `mysql.*` settings at a MariaDB server. The plugin ships with MySQL Connector/J (8.x), which is wire‑protocol compatible with MariaDB for the SQL features used here (DDL, indexes, FKs, `utf8mb4`). No extra driver is required in typical setups—just use your MariaDB host/port/database/user/password. If your environment mandates MariaDB Connector/J specifically, the SQL remains compatible; reach out if you need a build that bundles it.

## Troubleshooting
- “This election is not open.” — staff must open it in the Manager.
- “You don’t have permission to vote.” — ensure `elections.user` and any extra nodes in the election requirements.
- “You are not eligible to vote.” — you’re missing a required node or playtime minutes.
- “You have already submitted a ballot for this election.” — duplicates are blocked per voter.
- “A poll already exists here” — the block is already used by another election; the message shows which election owns it.
- “Export failed” — check paste.gg connectivity and that `pastegg.apiKey` is set for delete operations.

## UI customization (AutoYML)
The entire in‑game UI is configurable. Each menu defines a small Config class, and the plugin uses an AutoYML utility to generate a YAML file for that class the first time the menu is opened.

- Where configs live
  - Under your plugin data folder: `plugins/Elections/menus/`
  - File names match the menu and, for some menus, include a context suffix (e.g., an election id).
- How to edit
  - Open the menu once to have the YAML created with default values and a header comment.
  - Edit only the values (strings, colors via MiniMessage, etc.). Don’t rename keys unless you know what you’re doing.
  - Placeholders are supported (e.g., `%player%` plus menu‑specific ones). Each YAML includes a header listing the placeholders available for that menu.
- Reloading changes
  - Just close and re‑open the menu. The config is loaded when the dialog is built; no server restart is required.
- Tips
  - Keep files UTF‑8. Back up before large edits.
  - If multiple staff edit the same YAML, avoid concurrent edits while the server is writing.


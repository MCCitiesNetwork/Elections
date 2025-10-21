package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.RequirementsDto;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.democracycraft.democracyelections.util.permissions.PermissionScanner;
import net.democracycraft.democracyelections.util.permissions.PermissionNodesStore;
import net.democracycraft.democracyelections.util.permissions.data.PermissionNodesDto;
import org.bukkit.permissions.Permission;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Child dialog to configure election requirements (permissions and active playtime).
 */
public class RequirementsMenu extends ChildMenuImp {

    enum Keys { PLAYTIME, PLAYTIME_TEXT }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public RequirementsMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "requirements_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        long currentMinutes = election == null || election.getRequirements() == null ? 0L : election.getRequirements().getMinActivePlaytimeMinutes();
        List<String> currentPerms = election == null || election.getRequirements() == null ? List.of() : election.getRequirements().getPermissions();

        AutoDialog.Builder builder = getAutoDialogBuilder();
        builder.title(title("Requirements"));
        builder.canCloseWithEscape(true);
        builder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        builder.addBody(DialogBody.plainMessage(info("Toggle permissions required to vote and set minimum active playtime (minutes).")));

        // Use configured nodes to filter permissions displayed (prefix match)
        PermissionNodesStore store = DemocracyElections.getInstance().getPermissionNodesStore();
        PermissionNodesDto nodesDto = store.get();

        // Collect permissions that match configured nodes (exact node or child under node.)
        List<Permission> filtered = PermissionScanner.getPermissionsForNodesPrefix(nodesDto);
        Map<String, Permission> byName = new LinkedHashMap<>();
        for (Permission p : filtered) byName.put(p.getName(), p);

        for (Permission perm : byName.values()) {
            String key = permKey(perm.getName());
            boolean initial = currentPerms.contains(perm.getName());
            builder.addInput(DialogInput.bool(key, info(perm.getName())).initial(initial).build());
        }

        builder.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), info("Active playtime (minutes)"), 0f, 1_000_000f).step(1f).initial((float) currentMinutes).build());
        // Fine adjustment text input. If valid, it overrides the slider value
        builder.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), key("Fine adjust (minutes)")).labelVisible(true).build());

        builder.buttonWithPlayer(good("Save"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            List<String> newPerms = new ArrayList<>();
            for (Permission perm : byName.values()) {
                String key = permKey(perm.getName());
                Boolean selection = resp.getBoolean(key);
                if (selection != null && selection) newPerms.add(perm.getName());
            }
            Long minutes = null;
            String txt = resp.getText(Keys.PLAYTIME_TEXT.name());
            if (txt != null && !txt.isBlank()) {
                try {
                    long parsed = Long.parseLong(txt.trim());
                    if (parsed >= 0 && parsed <= 1_000_000L) {
                        minutes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minutes == null) {
                Float floatInput = resp.getFloat(Keys.PLAYTIME.name());
                minutes = floatInput == null ? currentMinutes : Math.max(0, Math.round(floatInput));
            }
            electionsService.setRequirements(electionId, new RequirementsDto(newPerms, minutes));
            p.sendMessage(good("Requirements updated."));
            new ElectionManagerMenu(p, electionsService, electionId).open();
        });

        builder.button(warn("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return builder.build();
    }

    private String permKey(String permName) {
        return "PERM_" + permName.replace(':', '_').replace('.', '_').replace('-', '_');
    }
}

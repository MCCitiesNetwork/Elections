package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.data.RequirementsDto;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.democracycraft.democracyelections.util.permissions.PermissionScanner;
import org.bukkit.permissions.Permission;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
        Election e = electionsService.getElection(electionId).orElse(null);
        long currentMinutes = e == null || e.getRequirements() == null ? 0L : e.getRequirements().getMinActivePlaytimeMinutes();
        List<String> currentPerms = e == null || e.getRequirements() == null ? List.of() : e.getRequirements().getPermissions();

        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Requirements"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Toggle permissions required to vote and set minimum active playtime (minutes).")));

        for (Permission perm : PermissionScanner.getAllPermissions()) {
            String key = permKey(perm.getName());
            boolean initial = currentPerms.contains(perm.getName());
            b.addInput(DialogInput.bool(key, info(perm.getName())).initial(initial).build());
        }

        b.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), info("Active playtime (minutes)"), 0f, 1_000_000f).step(1f).initial((float) currentMinutes).build());
        // Fine adjustment text input. If valid, it overrides the slider value
        b.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), key("Fine adjust (minutes)")).labelVisible(true).build());

        b.buttonWithPlayer(good("Save"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            List<String> newPerms = new ArrayList<>();
            for (Permission perm : PermissionScanner.getAllPermissions()) {
                String k = permKey(perm.getName());
                Boolean sel = resp.getBoolean(k);
                if (sel != null && sel) newPerms.add(perm.getName());
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
                Float f = resp.getFloat(Keys.PLAYTIME.name());
                minutes = f == null ? currentMinutes : Math.max(0, Math.round(f));
            }
            electionsService.setRequirements(electionId, new RequirementsDto(newPerms, minutes));
            p.sendMessage(good("Requirements updated."));
            new ElectionManagerMenu(p, electionsService, electionId).open();
        });

        b.button(warn("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return b.build();
    }

    private String permKey(String permName) {
        return "PERM_" + permName.replace(':', '_').replace('.', '_').replace('-', '_');
    }
}

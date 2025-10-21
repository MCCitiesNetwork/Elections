package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.data.RequirementsDto;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.democracycraft.democracyelections.util.permissions.PermissionNodesStore;
import net.democracycraft.democracyelections.util.permissions.PermissionScanner;
import net.democracycraft.democracyelections.util.permissions.data.PermissionNodesDto;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElectionCreateRequirementsMenu extends ChildMenuImp {

    enum Keys { PLAYTIME, PLAYTIME_TEXT }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateRequirementsMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_requirements");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create: Requirements"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Toggle permissions required and set minimum active playtime (minutes).")));

        // Use configured nodes (prefix match)
        PermissionNodesStore store = DemocracyElections.getInstance().getPermissionNodesStore();
        PermissionNodesDto nodesDto = store.get();
        List<Permission> filtered = PermissionScanner.getPermissionsForNodesPrefix(nodesDto);
        Map<String, Permission> byName = new LinkedHashMap<>();
        for (Permission p : filtered) byName.put(p.getName(), p);

        for (Permission perm : byName.values()) {
            String key = permKey(perm.getName());
            boolean initial = draft.requirements.getPermissions().contains(perm.getName());
            b.addInput(DialogInput.bool(key, info(perm.getName())).initial(initial).build());
        }

        b.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), info("Active playtime (minutes)"), 0f, 1_000_000f)
                .step(1f)
                .initial((float) draft.requirements.getMinActivePlaytimeMinutes())
                .build());
        // Fine adjustment text input. If valid, it overrides the slider value
        b.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), key("Fine adjust (minutes)")).labelVisible(true).build());

        b.buttonWithPlayer(good("Next"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            List<String> newPerms = new ArrayList<>();
            for (Permission perm : byName.values()) {
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
                minutes = f == null ? draft.requirements.getMinActivePlaytimeMinutes() : Math.max(0, Math.round(f));
            }
            draft.requirements = new RequirementsDto(newPerms, minutes);
            new ElectionCreateSystemMenu(p, wizard, draft).open();
        });

        b.button(info("Back"), ctx -> new ElectionCreateDurationMenu(ctx.player(), wizard, draft).open());
        return b.build();
    }

    private String permKey(String permName) {
        return "PERM_" + permName.replace(':', '_').replace('.', '_').replace('-', '_');
    }
}

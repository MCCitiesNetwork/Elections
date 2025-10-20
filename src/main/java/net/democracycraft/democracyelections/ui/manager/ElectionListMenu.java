package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ParentMenuImp;
import net.democracycraft.democracyelections.ui.list.ElectionListItemMenu;
import net.democracycraft.democracyelections.ui.manager.create.ElectionCreateWizard;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/**
 * Parent dialog listing all elections and providing navigation to item view and creation wizard.
 */
public class ElectionListMenu extends ParentMenuImp {

    private final ElectionsService electionsService;

    /**
     * @param player player opening the dialog
     * @param electionsService service to retrieve and create elections
     */
    public ElectionListMenu(Player player, ElectionsService electionsService) {
        super(player, "election_list");
        this.electionsService = electionsService;
        this.setDialog(build());
    }

    private Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Election Manager"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        List<Election> elections = new ArrayList<>(electionsService.listElections());
        elections.sort(Comparator.comparingInt(Election::getId));

        if (elections.isEmpty()) {
            b.addBody(DialogBody.plainMessage(info("No elections created yet.")));
        } else {
            b.addBody(DialogBody.plainMessage(key("Elections:")));
            for (Election e : elections) {
                Component label = Component.text("#" + e.getId() + " ")
                        .append(Component.text(e.getTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        .append(Component.text("  "))
                        .append(info("[" + e.getStatus().name() + "]  Voters:" + e.getVoterCount() + "  Cand:" + e.getCandidates().size()));
                int id = e.getId();
                b.button(label, ctx -> new ElectionListItemMenu(ctx.player(), this, electionsService, id).open());
            }
        }

        b.button(warn("Create Election"), ctx -> new ElectionCreateWizard(ctx.player(), electionsService).open());
        b.button(info("Refresh"), ctx -> new ElectionListMenu(ctx.player(), electionsService).open());
        b.button(neg("Close"), ctx -> {});

        return b.build();
    }
}

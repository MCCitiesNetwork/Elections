package net.democracycraft.democracyelections.ui.list;

import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.ui.manager.ElectionManagerMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public class ElectionListItemMenu extends ChildMenuImp {

    private final ElectionsService svc;
    private final int electionId;

    public ElectionListItemMenu(Player player, ParentMenu parent, ElectionsService svc, int electionId) {
        super(player, parent, "election_list_item_" + electionId);
        this.svc = svc;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Component title(String t) { return Component.text(t).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD); }
    private Component info(String t) { return Component.text(t).color(NamedTextColor.GRAY); }
    private Component key(String t) { return Component.text(t).color(NamedTextColor.AQUA); }
    private Component warn(String t) { return Component.text(t).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); }

    private io.papermc.paper.dialog.Dialog build() {
        Election e = svc.getElection(electionId).orElse(null);
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title(e == null ? "Election" : ("Election #" + e.getId())));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        if (e == null) {
            b.addBody(DialogBody.plainMessage(info("Election not found.")));
            b.button(warn("Back"), ctx -> ctx.player().closeInventory());
            return b.build();
        }

        b.addBody(DialogBody.plainMessage(Component.newline()
                .append(key("Title: ")).append(Component.text(e.getTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .appendNewline().append(key("Status: ")).append(info(e.getStatus().name()))
                .appendNewline().append(key("Voters: ")).append(info(String.valueOf(e.getVoterCount())))
                .appendNewline().append(key("Candidates: ")).append(info(String.valueOf(e.getCandidates().size())))
                .appendNewline().append(key("Polls: ")).append(info(String.valueOf(e.getPolls().size())))
        ));

        b.button(warn("Open Manager"), ctx -> new ElectionManagerMenu(ctx.player(), svc, electionId).open());
        b.button(warn("Back"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}


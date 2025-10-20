package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ParentMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public class ElectionCreateWizard extends ParentMenuImp {

    private final ElectionsService svc;
    private final DraftElection draft = new DraftElection();

    public ElectionCreateWizard(Player player, ElectionsService svc) {
        super(player, "election_create_wizard");
        this.svc = svc;
        this.setDialog(build());
    }

    public DraftElection getDraft() { return draft; }
    public ElectionsService getService() { return svc; }

    private Component title(String t) { return Component.text(t).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD); }
    private Component info(String t) { return Component.text(t).color(NamedTextColor.GRAY); }
    private Component warn(String t) { return Component.text(t).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); }

    private io.papermc.paper.dialog.Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create Election"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("This wizard will guide you through creating a new election.")));
        b.addBody(DialogBody.plainMessage(info("Steps: Basics -> Duration -> Requirements -> System & Minimum -> Confirm")));

        b.button(warn("Start"), ctx -> new ElectionCreateBasicsMenu(ctx.player(), this, svc, draft).open());
        b.button(info("Cancel"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}


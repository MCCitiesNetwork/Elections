package net.democracycraft.democracyelections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Child dialog to edit an election title.
 */
public class TitleEditMenu extends ChildMenuImp {

    enum Keys { TITLE }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public TitleEditMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "title_edit_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    private Dialog build() {
        var e = electionsService.getElection(electionId).orElse(null);
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Edit Title"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);
        if (e != null) {
            b.addBody(DialogBody.plainMessage(info("Current: ").append(Component.text(e.getTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))));
        }
        b.addInput(DialogInput.text(Keys.TITLE.name(), key("Title")).labelVisible(true).build());
        b.buttonWithPlayer(good("Save"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            String t = resp.getText(Keys.TITLE.name());
            if (t == null || t.isBlank()) {
                p.sendMessage(neg("Title cannot be empty."));
                new TitleEditMenu(p, getParentMenu(), electionsService, electionId).open();
                return;
            }
            electionsService.setTitle(electionId, t);
            p.sendMessage(good("Title updated."));
            new ElectionManagerMenu(p, electionsService, electionId).open();
        });
        b.button(info("Back"), ctx -> new ElectionManagerMenu(ctx.player(), electionsService, electionId).open());
        return b.build();
    }
}

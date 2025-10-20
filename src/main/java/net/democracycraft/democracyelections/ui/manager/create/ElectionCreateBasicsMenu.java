package net.democracycraft.democracyelections.ui.manager.create;

import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.democracyelections.api.ui.ParentMenu;
import net.democracycraft.democracyelections.ui.ChildMenuImp;
import net.democracycraft.democracyelections.util.dialog.AutoDialog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Create wizard step: Basics (title).
 */
public class ElectionCreateBasicsMenu extends ChildMenuImp {

    enum Keys { TITLE }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    /**
     * @param player player opening the dialog
     * @param parent wizard parent
     * @param draft draft state container
     */
    public ElectionCreateBasicsMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_basics");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    private io.papermc.paper.dialog.Dialog build() {
        AutoDialog.Builder b = getAutoDialogBuilder();
        b.title(title("Create: Basics"));
        b.canCloseWithEscape(true);
        b.afterAction(DialogBase.DialogAfterAction.CLOSE);

        b.addBody(DialogBody.plainMessage(info("Enter the election title.")));
        b.addInput(DialogInput.text(Keys.TITLE.name(), info("Title")).labelVisible(true).build());

        b.buttonWithPlayer(good("Next"), null, Duration.ofMinutes(5), 1, (p, resp) -> {
            String t = resp.getText(Keys.TITLE.name());
            if (t == null || t.isBlank()) {
                p.sendMessage(neg("Title cannot be empty."));
                new ElectionCreateBasicsMenu(p, wizard, draft).open();
                return;
            }
            draft.title = t;
            new ElectionCreateDurationMenu(p, wizard, draft).open();
        });

        b.button(warn("Cancel"), ctx -> ctx.player().closeInventory());
        return b.build();
    }
}

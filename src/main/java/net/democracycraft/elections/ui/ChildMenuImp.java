package net.democracycraft.elections.ui;

import net.democracycraft.elections.api.ui.ChildMenu;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import org.bukkit.entity.Player;

public class ChildMenuImp extends MenuImp implements ChildMenu {
    private final ParentMenu parentMenu;

    /**
     *
     * @param player
     * @param parent
     * @param id
     */
    public ChildMenuImp(Player player, ParentMenu parent, String id) {
        super(player, id);
        this.parentMenu = parent;
    }

    @Override
    public ParentMenu getParentMenu() {
        return parentMenu;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public AutoDialog.Builder getAutoDialogBuilder() {
        return AutoDialog.builder(parentMenu);
    }
}

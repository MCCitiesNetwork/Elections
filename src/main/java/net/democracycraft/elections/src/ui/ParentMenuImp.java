package net.democracycraft.elections.src.ui;

import net.democracycraft.elections.api.ui.ChildMenu;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ParentMenuImp extends MenuImp implements ParentMenu {

    private List<ChildMenu> childMenus = new ArrayList<>();

    /**
     *
     * @param player
     */
    public ParentMenuImp(Player player, String id) {
        super(player, id);
    }

    @Override
    public List<ChildMenu> getChildMenus() {
        return childMenus;
    }

    @Override
    public void addChildMenu(ChildMenu childMenu) {
        this.childMenus.add(childMenu);
    }

    @Override
    public void addChildMenus(List<ChildMenu> childMenus) {
        this.childMenus.addAll(childMenus);
    }

     @Override
     public AutoDialog.Builder getAutoDialogBuilder() {
        return AutoDialog.builder(this);
    }
}

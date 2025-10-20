package net.democracycraft.democracyelections.menu;

import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public abstract class MenuImp {
    protected final Component title;
    protected final Player player;
    protected final Dialog dialog;

    public MenuImp(Component title, Player player, Dialog dialog) {
        this.title = title;
        this.player = player;
        this.dialog = dialog;
    }

    public Component getTitle() {
        return title;
    }

    public Player getPlayer() {
        return player;
    }
}


package net.democracycraft.democracyelections.ui;

import io.papermc.paper.dialog.Dialog;
import net.democracycraft.democracyelections.api.ui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public abstract class MenuImp implements Menu {
    protected final Player player;
    protected final String id;
    protected Dialog dialog = null;

    /**
     *
     * @param player
     */

    public MenuImp(Player player, String id) {
        this.player = player;
        this.id = id;
    }

    @Override
    public String getId(){
        return id;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Nullable
    @Override
    public Dialog getDialog() {
        return dialog;
    }

    @Override
    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public void open() {
        player.showDialog(dialog);
    }

    // Common UI helpers
    protected Component key(String t) { return Component.text(t).color(NamedTextColor.AQUA); }
    protected Component title(String text) { return Component.text(text).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD); }
    protected Component info(String text) { return Component.text(text).color(NamedTextColor.GRAY); }
    protected Component good(String text) { return Component.text(text).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD); }
    protected Component warn(String text) { return Component.text(text).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); }
    protected Component neg(String text) { return Component.text(text).color(NamedTextColor.RED).decorate(TextDecoration.BOLD); }
}

package net.democracycraft.elections.api.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.InlinedRegistryBuilderProvider;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AutoDialog {
    private static Duration DEFAULT_BUTTON_EXPIRE = Duration.ofMinutes(10);
    private static int DEFAULT_BUTTON_MAX_USES = ClickCallback.UNLIMITED_USES;

    private final Dialog internal;

    private AutoDialog(Dialog internal) {
        this.internal = internal;
    }

    public Dialog dialog() {
        return internal;
    }

    public void show(@NotNull Player player) {
        player.showDialog(internal);
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() {
        }


        private Component title = Component.text("Dialog");
        private Component externalTitle = null;
        private boolean canCloseWithEscape = true;
        private boolean pause = true;
        private DialogBase.DialogAfterAction afterAction = DialogBase.DialogAfterAction.CLOSE;

        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private final List<ActionButton> buttons = new ArrayList<>();

        public Builder title(Component title) { this.title = title; return this; }
        public Builder externalTitle(Component externalTitle) { this.externalTitle = externalTitle; return this; }
        public Builder canCloseWithEscape(boolean value) { this.canCloseWithEscape = value; return this; }
        public Builder pause(boolean value) { this.pause = value; return this; }
        public Builder afterAction(DialogBase.DialogAfterAction value) { this.afterAction = value; return this; }

        public Builder body(List<DialogBody> elements) { this.body.addAll(elements); return this; }
        public Builder addBody(DialogBody element) { this.body.add(element); return this; }

        public Builder inputs(List<DialogInput> elements) { this.inputs.addAll(elements); return this; }
        public Builder addInput(DialogInput element) { this.inputs.add(element); return this; }

        public Builder addButton(ActionButton button) { this.buttons.add(button); return this; }

        public Builder button(Component label, Component tooltip, Consumer<Context> handler) {
            DialogActionCallback callback = (response, audience) -> {
                Player player = (audience instanceof Player) ? (Player) audience : null;
                if (player == null) return;
                Context ctx = new Context(player, audience, response);
                handler.accept(ctx);
            };

            DialogAction action = DialogInstancesProvider.instance().register(callback, defaultButtonOptions());

            ActionButton btn = ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(action)
                    .build();

            this.buttons.add(btn);
            return this;
        }

        public Builder button(Component label, Consumer<Context> handler) {
            return button(label, null, handler);
        }

        public Builder buttonWithResponse(Component label, Component tooltip, BiConsumer<DialogResponseView, Audience> handler) {
            DialogActionCallback callback = (response, audience) -> handler.accept(response, audience);

            DialogAction action = DialogInstancesProvider.instance().register(callback, defaultButtonOptions());

            ActionButton btn = ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(action)
                    .build();

            this.buttons.add(btn);
            return this;
        }

        public Builder buttonWithPlayer(Component label, Component tooltip, BiConsumer<Player, DialogResponseView> handler) {
            DialogActionCallback callback = (response, audience) -> {
                Player player = (audience instanceof Player) ? (Player) audience : null;
                if (player == null) return;
                handler.accept(player, response);
            };

            DialogAction action = DialogInstancesProvider.instance().register(callback, defaultButtonOptions());

            ActionButton btn = ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(action)
                    .build();

            this.buttons.add(btn);
            return this;
        }

        public Builder commandButton(Component label, Component tooltip, String commandTemplate) {
            DialogAction action = DialogAction.commandTemplate(commandTemplate);
            ActionButton btn = ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(action)
                    .build();
            this.buttons.add(btn);
            return this;
        }

        public Builder staticButton(Component label, Component tooltip, DialogAction.StaticAction staticAction) {
            ActionButton btn = ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(staticAction)
                    .build();
            this.buttons.add(btn);
            return this;
        }

        public Dialog build() {
            DialogBase.Builder baseBuilder = DialogBase.builder(title)
                    .canCloseWithEscape(canCloseWithEscape)
                    .pause(pause)
                    .afterAction(afterAction);

            if (externalTitle != null) baseBuilder.externalTitle(externalTitle);
            if (!body.isEmpty()) baseBuilder.body(body);
            if (!inputs.isEmpty()) baseBuilder.inputs(inputs);

            return InlinedRegistryBuilderProvider.instance().createDialog(factory -> {
                var builder = factory.empty();

                builder.base(baseBuilder.build());

                if (!buttons.isEmpty()) {
                    builder.type(DialogType.multiAction(buttons).build());
                } else {
                    builder.type(DialogInstancesProvider.instance().notice());
                }
            }
            );
        }
        private ClickCallback.Options defaultButtonOptions() {
            return ClickCallback.Options.builder()
                    .lifetime(DEFAULT_BUTTON_EXPIRE)
                    .uses(DEFAULT_BUTTON_MAX_USES)
                    .build();
        }
    }

    public record Context(Player player, Audience audience, DialogResponseView response) {
    }
}

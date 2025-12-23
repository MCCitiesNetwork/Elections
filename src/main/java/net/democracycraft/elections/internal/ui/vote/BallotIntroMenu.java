package net.democracycraft.elections.internal.ui.vote;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.internal.data.BallotMode;
import net.democracycraft.elections.internal.data.VotingSystem;
import net.democracycraft.elections.internal.ui.ParentMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * Intro screen for a ballot. Summarizes the election and explains how to vote,
 * offering a button to proceed to the candidate list menu. All texts are configurable via per-menu YAML.
 */
public class BallotIntroMenu extends ParentMenuImp {

    private final ElectionsService electionsService;
    private final int electionId;
    private final Elections plugin;

    public BallotIntroMenu(Player player, ElectionsService electionsService, int electionId, Elections plugin) {
        super(player, "ballot_intro_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.plugin = plugin;
        this.setDialog(build());
    }

    /** Config DTO for this menu. */
    public static class Config implements Serializable {
        public String titleFallback = "<gold><bold>Ballot</bold></gold>";
        public String notFound = "<red><bold>Election not found.</bold></red>";
        public String titleFormat = "<gold><bold>%election_title% Ballot</bold></gold>";
        public String systemLabel = "<aqua>System: </aqua>";
        public String minLabel = "<aqua>Minimum: </aqua>";
        public String howLabel = "<aqua>How to vote: </aqua>";
        public String howBlock = "<gray>You have exactly <white><bold>%min%</bold></white> votes. Order does not matter.</gray>";
        public String howPreferential = "<gray>Rank candidates. You can submit after at least <white><bold>%min%</bold></white> preferences.</gray>";
        public String startBtn = "<green><bold>Start Voting</bold></green>";
        public String closeBtn = "<red><bold>Close</bold></red>";
        public String yamlHeader = "BallotIntroMenu configuration. Placeholders: %election_title%, %system%, %min%.";
        public String valueGrayFormat = "<gray>%value%</gray>";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(BallotIntroMenu.Config.class, "BallotIntroMenu.yml", new BallotIntroMenu.Config().yamlHeader);
            yml.loadOrCreate(BallotIntroMenu.Config::new);
        }
    }

    private Dialog build() {
        Optional<Election> optionalElection = electionsService.getElection(electionId);
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        if (optionalElection.isEmpty()) {
            dialogBuilder.title(miniMessage(config.titleFallback));
            dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.notFound)));
            return dialogBuilder.build();
        }

        Election election = optionalElection.get();
        String systemName = election.getSystem().name();
        String minVotes = String.valueOf(election.getMinimumVotes());
        Map<String, String> placeholders = Map.of(
                "%election_title%", election.getTitle(),
                "%system%", systemName,
                "%min%", minVotes
        );

        dialogBuilder.title(miniMessage(config.titleFormat, placeholders));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        String howTo = election.getSystem() == VotingSystem.BLOCK ? applyPlaceholders(config.howBlock, placeholders) : applyPlaceholders(config.howPreferential, placeholders);

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.systemLabel, placeholders)).append(miniMessage(applyPlaceholders(config.valueGrayFormat, Map.of("%value%", systemName)), null))
                .appendNewline().append(miniMessage(config.minLabel, placeholders)).append(miniMessage(applyPlaceholders(config.valueGrayFormat, Map.of("%value%", minVotes)), null))
                .appendNewline().append(miniMessage(config.howLabel, placeholders)).append(miniMessage(howTo))));


        dialogBuilder.button(miniMessage(config.startBtn, placeholders), context -> {
            BallotMode mode = election.getBallotMode();
            if (mode == BallotMode.SIMPLE) {
                if (election.getSystem() == VotingSystem.PREFERENTIAL) {
                    new SimplePreferentialBallotMenu(context.player(), this, electionsService, electionId).open();
                } else {
                    new SimpleBlockBallotMenu(context.player(), this, electionsService, electionId).open();
                }
            } else {
                if (election.getSystem() == VotingSystem.PREFERENTIAL) {
                    new PreferentialBallotMenu(context.player(), this, electionsService, electionId).open();
                } else {
                    new CandidateVoteListMenu(context.player(), this, electionsService, electionId, plugin).open();
                }
            }
        });

        dialogBuilder.button(miniMessage(config.closeBtn, placeholders), context -> {});

        return dialogBuilder.build();
    }
}
package net.democracycraft.elections.src.ui.manager.create;

import net.democracycraft.elections.src.data.RequirementsDto;
import net.democracycraft.elections.src.data.TimeDto;
import net.democracycraft.elections.src.data.VotingSystem;

import java.util.ArrayList;

public class DraftElection {
    public String title = "";
    public VotingSystem system = VotingSystem.PREFERENTIAL;
    public int minimumVotes = 1;
    public RequirementsDto requirements = new RequirementsDto(new ArrayList<>(), 0);
    public Integer durationDays = null;
    public TimeDto durationTime = null;
}


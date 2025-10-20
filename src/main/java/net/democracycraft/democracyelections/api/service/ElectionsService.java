package net.democracycraft.democracyelections.api.service;

import net.democracycraft.democracyelections.api.model.*;
import net.democracycraft.democracyelections.data.*;

import java.util.List;
import java.util.Optional;

public interface ElectionsService {
    // Basic election CRUD
    Election createElection(String title, VotingSystem system, int minimumVotes, RequirementsDto requirements);
    Optional<Election> getElection(int id);
    List<Election> listElections();
    boolean deleteElection(int id); // keeps data, marks as DELETED

    // Metadata editing
    boolean setTitle(int electionId, String title);
    boolean setSystem(int electionId, VotingSystem system);
    boolean setMinimumVotes(int electionId, int minimum);
    boolean setRequirements(int electionId, RequirementsDto requirements);
    boolean openElection(int electionId);
    boolean closeElection(int electionId);
    boolean setClosesAt(int electionId, TimeStampDto closesAt); // null = never

    boolean setDuration(int electionId, Integer days, TimeDto time); // null to clear

    // Candidates
    Optional<Candidate> addCandidate(int electionId, String name);
    boolean removeCandidate(int electionId, int candidateId);

    // Polls (polling booths)
    Optional<Poll> addPoll(int electionId, String world, int x, int y, int z);
    boolean removePoll(int electionId, String world, int x, int y, int z);

    // Voters
    Voter registerVoter(int electionId, String name);
    Optional<Voter> getVoterById(int electionId, int voterId);
    List<Voter> listVoters(int electionId);

    // Voting
    boolean submitPreferentialBallot(int electionId, int voterId, List<Integer> orderedCandidateIds);
    boolean submitBlockBallot(int electionId, int voterId, List<Integer> candidateIds);
}

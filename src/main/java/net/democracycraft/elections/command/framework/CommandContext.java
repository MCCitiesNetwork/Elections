package net.democracycraft.elections.command.framework;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public record CommandContext(
        Elections plugin,
        ElectionsService electionsService,
        CommandSender sender,
        String label,
        String[] args
) {

    public CommandContext next() {
        if (args.length <= 1) return new CommandContext(plugin, electionsService, sender, label, new String[0]);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return new CommandContext(plugin, electionsService, sender, label, rest);
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public Player asPlayer() {
        return (Player) sender;
    }

    public String require(int index, String name) {
        if (index >= args.length) throw new IllegalArgumentException(name + " is required");
        return args[index];
    }

    public int requireInt(int index, String name) {
        return parseInt(require(index, name), name);
    }

    public long requireLong(int index, String name) {
        try {
            return Long.parseLong(require(index, name));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    public int parseInt(String s, String name) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    public int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            return -1;
        }
    }

    public List<String> electionIds() {
        return electionsService.listElectionsSnapshot().stream().map(e -> Integer.toString(e.getId())).toList();
    }

    public List<String> filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).limit(50).toList();
    }

    public VotingSystem parseSystem(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "preferential", "pref", "p" -> VotingSystem.PREFERENTIAL;
            case "block", "b" -> VotingSystem.BLOCK;
            default -> throw new IllegalArgumentException("system must be preferential|block");
        };
    }

    public static String tsToString(TimeStampDto ts) {
        DateDto d = ts.date();
        TimeDto t = ts.time();
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02dZ", d.getYear(), d.getMonth(), d.getDay(), t.hour(), t.minute(), t.second());
    }

    public static TimeStampDto plusNow(int days, int hours, int minutes) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.add(Calendar.DAY_OF_MONTH, days);
        c.add(Calendar.HOUR_OF_DAY, hours);
        c.add(Calendar.MINUTE, minutes);
        return new TimeStampDto(new DateDto(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR)), new TimeDto(c.get(Calendar.SECOND), c.get(Calendar.MINUTE), c.get(Calendar.HOUR_OF_DAY)));
    }

    public void usage(String usage) {
        sender.sendMessage("Usage: /" + label + " " + usage);
    }
}

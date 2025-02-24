package me.leon.sniffer.handlers;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import me.leon.sniffer.commands.subcommands.*;
import me.leon.sniffer.utils.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final Sniffer plugin;
    private final Map<String, AbstractCommand> commands;
    private final Map<String, List<String>> completions;

    public CommandHandler(Sniffer plugin) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
        this.completions = new HashMap<>();
        registerCommands();
        setupCompletions();
    }

    private void registerCommands() {
        registerCommand(new StartCommand(plugin));
        registerCommand(new StopCommand(plugin));
        registerCommand(new InfoCommand(plugin));
        registerCommand(new ReloadCommand(plugin));
        registerCommand(new AnalyzeCommand(plugin));
        registerCommand(new ExportCommand(plugin));

    }

    private void registerCommand(AbstractCommand command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    private void setupCompletions() {
        // Main command completions
        List<String> mainCompletions = new ArrayList<>();
        commands.values().stream().distinct().forEach(cmd ->
                mainCompletions.add(cmd.getName()));
        completions.put("main", mainCompletions);

        // Sniff type completions
        completions.put("types", Arrays.asList("movement", "combat", "rotation", "block", "all"));

        // Debug level completions
        completions.put("debug", Arrays.asList("on", "off", "verbose"));

        // Export format completions
        completions.put("format", Arrays.asList("json", "csv", "yaml"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtil.translate("&cThis command can only be used by players!"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("sniffer.use")) {
            player.sendMessage(ColorUtil.translate("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length == 0) {
            commands.get("help").execute(player, args);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        AbstractCommand cmd = commands.get(subCommand);

        if (cmd == null) {
            player.sendMessage(ColorUtil.translate("&cUnknown sub-command! Use /sniff help for help."));
            return true;
        }

        if (!player.hasPermission(cmd.getPermission())) {
            player.sendMessage(ColorUtil.translate("&cYou don't have permission to use this command!"));
            return true;
        }

        try {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            if (subArgs.length < cmd.getMinArgs()) {
                player.sendMessage(ColorUtil.translate("&cUsage: " + cmd.getUsage()));
                return true;
            }

            cmd.execute(player, subArgs);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&cAn error occurred while executing the command!"));
            plugin.getLogger().severe("Error executing command '" + subCommand + "': " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        if (!player.hasPermission("sniffer.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterCompletions(completions.get("main"), args[0]);
        }

        if (args.length >= 2) {
            AbstractCommand cmd = commands.get(args[0].toLowerCase());
            if (cmd != null) {
                return cmd.getTabCompletions(player, args.length - 1, args[args.length - 1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> completions, String partial) {
        if (completions == null) return Collections.emptyList();

        String lowercasePartial = partial.toLowerCase();
        List<String> filtered = new ArrayList<>();

        for (String str : completions) {
            if (str.toLowerCase().startsWith(lowercasePartial)) {
                filtered.add(str);
            }
        }

        return filtered;
    }

    public Map<String, AbstractCommand> getCommands() {
        return commands;
    }

    public List<String> getCompletions(String type) {
        return completions.getOrDefault(type, Collections.emptyList());
    }
}

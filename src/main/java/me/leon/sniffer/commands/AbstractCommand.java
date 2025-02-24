package me.leon.sniffer.commands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.utils.ColorUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractCommand {
    protected final Sniffer plugin;
    private final String name;
    private final String permission;
    private final String usage;
    private final String description;
    private final int minArgs;
    private final List<String> aliases;

    public AbstractCommand(Sniffer plugin, String name, String permission, String usage,
                           String description, int minArgs) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.usage = usage;
        this.description = description;
        this.minArgs = minArgs;
        this.aliases = new ArrayList<>();
    }

    public abstract void execute(Player player, String[] args);

    public List<String> getTabCompletions(Player player, int position, String current) {
        return Collections.emptyList();
    }

    protected void sendMessage(Player player, String message) {
        player.sendMessage(ColorUtil.translate(message));
    }

    protected void sendUsage(Player player) {
        sendMessage(player, "&cUsage: " + usage);
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    public int getMinArgs() {
        return minArgs;
    }

    public List<String> getAliases() {
        return aliases;
    }

    protected void addAlias(String alias) {
        aliases.add(alias);
    }
}

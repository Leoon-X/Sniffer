package me.leon.sniffer.commands.subcommands;

import me.leon.sniffer.Sniffer;
import me.leon.sniffer.commands.AbstractCommand;
import org.bukkit.entity.Player;

public class ReloadCommand extends AbstractCommand {

    public ReloadCommand(Sniffer plugin) {
        super(plugin,
                "reload",
                "sniffer.command.reload",
                "/sniff reload",
                "Reloads the plugin configuration",
                0);

        addAlias("rl");
    }

    @Override
    public void execute(Player player, String[] args) {
        try {
            // Save all current data
            plugin.getDataManager().saveAllData();

            // Reload config
            plugin.reloadConfig();

            // Reload any necessary components
            // TODO: Add specific reload logic if needed

            sendMessage(player, "&aConfiguration reloaded successfully!");
        } catch (Exception e) {
            sendMessage(player, "&cError reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
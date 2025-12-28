package me.perch.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import me.perch.VillagerOptimisation;
import me.perch.utils.VillagerUtilities;

public class ReloadCommand implements CommandExecutor {

    VillagerOptimisation plugin;

    public ReloadCommand(VillagerOptimisation plugin) {
        this.plugin = plugin;
    }
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pvoreload")) {
            if(!sender.hasPermission("pvo.reload")) {
                sender.sendMessage(VillagerUtilities.colorcodes.cm(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            sender.sendMessage(VillagerUtilities.colorcodes.cm(plugin.getConfig().getString("messages.reload-message")));
            plugin.reloadConfig();

            //update the workblock blocks
            VillagerUtilities.updateNameTags(plugin);
            VillagerUtilities.updateStandingOnBlocks(plugin);
            VillagerUtilities.updateWorkstationBlocks(plugin);
            VillagerUtilities.updateRestockTimes(plugin);
        }
        return true;
    }
}

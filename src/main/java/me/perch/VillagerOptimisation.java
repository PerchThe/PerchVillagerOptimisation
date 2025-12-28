package me.perch;

import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import me.perch.commands.OptimizeCommand;
import me.perch.commands.ReloadCommand;
import me.perch.commands.RemoveChangesCommand;
import me.perch.commands.UnoptimizeCommand;
import me.perch.events.EventListener;
import me.perch.utils.VillagerUtilities;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class VillagerOptimisation extends JavaPlugin {

    @Override
    public void onEnable() {

        //  Command Registration
        getCommand("pvoreload").setExecutor(new ReloadCommand(this));
        getCommand("pvooptimize").setExecutor(new OptimizeCommand(this));
        getCommand("pvounoptimize").setExecutor(new UnoptimizeCommand(this));
        getCommand("pvoremove").setExecutor(new RemoveChangesCommand(this));

        //  Event Registration
        getServer().getPluginManager().registerEvents(new EventListener(this), this);


        //  Config Stuff
        saveDefaultConfig();
        updateConfig();

        VillagerUtilities.updateNameTags(this);
        VillagerUtilities.updateStandingOnBlocks(this);
        VillagerUtilities.updateWorkstationBlocks(this);
        VillagerUtilities.updateRestockTimes(this);
    }

    @Override
    public void onDisable() {
        //  Plugin shutdown logic
    }

    //  Configuration File Updater
    public Configuration cfg = this.getConfig().getDefaults();
    public void updateConfig() {
        try {
            if(new File(getDataFolder() + "/config.yml").exists()) {
                boolean changesMade = false;
                YamlConfiguration tmp = new YamlConfiguration();
                tmp.load(getDataFolder() + "/config.yml");
                for(String str : cfg.getKeys(true)) {
                    if(!tmp.getKeys(true).contains(str)) {
                        tmp.set(str, cfg.get(str));
                        changesMade = true;
                    }
                }
                if(changesMade)
                    tmp.save(getDataFolder() + "/config.yml");
            }
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }
}

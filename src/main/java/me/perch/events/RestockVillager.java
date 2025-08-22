package me.perch.events;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import me.perch.VillagerOptimisation;
import me.perch.utils.VillagerUtilities;

public class RestockVillager {

    private static void restockMessage(long timeTillNextRestock, Player player, VillagerOptimisation plugin) {
        long totalsec = timeTillNextRestock / 20;
        long sec = totalsec % 60;
        long min = (totalsec - sec) / 60;
        String message = plugin.getConfig().getString("messages.next-restock");
        message = message.replaceAll("%pvorestockmin%", Long.toString(min));
        message = message.replaceAll("%pvorestocksec%", Long.toString(sec));
        player.sendMessage(VillagerUtilities.colorcodes.cm(message));
    }

    public static void call(Villager vil, VillagerOptimisation plugin, Player player) {

        // Permission to Bypass restock cooldown
        if (player.hasPermission("pvo.restockcooldown.bypass")) {
            VillagerUtilities.restock(vil);
            VillagerUtilities.setLastRestock(vil, plugin);
            return;
        }

        // Check if it's time to restock
        long worldTick = vil.getWorld().getFullTime();
        long currentDayTick = vil.getWorld().getTime();
        long beginningOfDayTick = worldTick - currentDayTick;
        long vilTick = VillagerUtilities.getLastRestock(vil, plugin);

        for (long restockTime : VillagerUtilities.restock_times) {
            long todayRestock = beginningOfDayTick + restockTime;
            if (worldTick >= todayRestock && vilTick < todayRestock) {
                VillagerUtilities.restock(vil);
                VillagerUtilities.setLastRestock(vil, plugin);
                return;
            }
        }

        // check if he gets to see cool-down time
        if (player.hasPermission("pvo.message.nextrestock")) {
            long timeTillNextRestock = Long.MAX_VALUE;
            for (long restockTime : VillagerUtilities.restock_times) {
                long restockTick = beginningOfDayTick + restockTime;
                if (worldTick < restockTick) {
                    timeTillNextRestock = Math.min(timeTillNextRestock, restockTick - worldTick);
                }
            }

            if (timeTillNextRestock == Long.MAX_VALUE) {
                timeTillNextRestock = (24000 + beginningOfDayTick + VillagerUtilities.restock_times.get(0)) - worldTick;
            }

            restockMessage(timeTillNextRestock, player, plugin);
        }
    }



}

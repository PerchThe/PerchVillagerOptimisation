package me.perch.events;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import me.perch.VillagerOptimisation;
import me.perch.utils.VillagerUtilities;

// Optional: claim trust check for GriefPrevention (legacy v16 API)
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;

public class EventListener implements Listener {

    VillagerOptimisation plugin;

    public EventListener(VillagerOptimisation plugin) {
        this.plugin = plugin;
    }

    // --- ROTATE VILLAGER ON LEFT-CLICK (HIT) WITH DISABLING NAME-TAG ---
    // Run even if another plugin (e.g., GP/GPFlags ProtectNamedMobs) cancelled the damage event.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVillagerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        Villager villager = (Villager) event.getEntity();

        // Respect GriefPrevention claim trust (skip rotation if player isn't trusted).
        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(villager.getLocation(), true, null);
            if (claim != null && claim.allowAccess(player) != null && !player.hasPermission("pvo.rotate.bypassclaims")) {
                // Not trusted here; leave any existing cancellation intact.
                return;
            }
        } catch (Throwable ignored) {
            // GP not present or API changed; fail open to avoid hard dependency.
        }

        // Only rotate if player is holding the disabling name-tag in main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() == Material.NAME_TAG && mainHand.hasItemMeta() && mainHand.getItemMeta().hasDisplayName()) {
            boolean nametag_result = NameTagAI.call(villager, plugin, player);
            if (nametag_result) {
                float rotateBy = player.isSneaking() ? 1.0F : 45.0F;
                Location loc = villager.getLocation();
                float newYaw = loc.getYaw() + rotateBy;
                if (newYaw >= 360.0F) newYaw -= 360.0F;
                loc.setYaw(newYaw);
                loc.setPitch(0.0F);
                villager.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
                // Prevent damage regardless of other plugins' decisions.
                event.setCancelled(true);
                return;
            }
        }
        // Otherwise, allow normal damage (or let other plugins keep it cancelled).
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!event.getRightClicked().getType().equals(EntityType.VILLAGER)) return;
        Villager villager = (Villager) event.getRightClicked();

        //  Setup new Villagers
        if (!VillagerUtilities.hasMarker(villager, plugin)) {
            VillagerUtilities.setAiCooldown(villager, plugin, 0L);
            VillagerUtilities.setLevelCooldown(villager, plugin, 0L);
            VillagerUtilities.setLastRestock(villager, plugin);
            VillagerUtilities.setMarker(villager, plugin, true);
        }

        //  Get Times
        long currentTime = System.currentTimeMillis() / 1000;
        long vilLevelCooldown = VillagerUtilities.getLevelCooldown(villager, plugin);
        long vilAiCooldown = VillagerUtilities.getAiCooldown(villager, plugin);
        long totalSeconds = vilAiCooldown - currentTime;
        long sec = totalSeconds % 60;
        long min = totalSeconds / 60;

        //  If the villager is leveling up
        if (vilLevelCooldown > currentTime) {
            String message = plugin.getConfig().getString("messages.cooldown-levelup-message");
            long level_sec = vilLevelCooldown - currentTime;
            message = message.replaceAll("%pvoseconds%", Long.toString(level_sec));
            event.getPlayer().sendMessage(VillagerUtilities.colorcodes.cm(message));
            villager.shakeHead();
            event.setCancelled(true);
            return;
        }

        boolean nametag_result = NameTagAI.call(villager, plugin, player);
        boolean block_result = BlockAI.call(villager, plugin, player);
        boolean workblock_result = WorkblockAI.call(villager, plugin, player);
        boolean should_be_disabled = nametag_result || block_result || workblock_result;

        //  If villager AI is being toggled
        if (should_be_disabled == VillagerUtilities.getMarker(villager, plugin)) {
            //  If toggling is on cooldown
            if ((vilAiCooldown > currentTime) && !player.hasPermission("pvo.cooldown.bypass")) {
                //Tell player it's on cooldown
                String message = plugin.getConfig().getString("messages.cooldown-ai-message");
                message = message.replaceAll("%pvominutes%", Long.toString(min));
                message = message.replaceAll("%pvoseconds%", Long.toString(sec));
                event.getPlayer().sendMessage(VillagerUtilities.colorcodes.cm(message));
                event.setCancelled(true);
                //  If cooldown is over
            } else {
                VillagerUtilities.setMarker(villager, plugin, !should_be_disabled);
                villager.setAware(!should_be_disabled);
                VillagerUtilities.setAiCooldown(villager, plugin, plugin.getConfig().getLong("ai-toggle-cooldown"));
                //  If nametag shouldn't be consumed, give one back
                if (player.getInventory().getItemInMainHand().getType().equals(Material.NAME_TAG) && !plugin.getConfig().getBoolean("toggleableoptions.usenametags")) {
                    ItemStack nametag = player.getInventory().getItemInMainHand();
                    if (!nametag.getItemMeta().hasDisplayName()) return;
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() + 1);
                }
            }
        }
        //  If the villager AI is not being toggled
        else {
            //  If nametag shouldn't be consumed, give one back
            if (!VillagerUtilities.hasMarker(villager, plugin)) return;
            if (player.getInventory().getItemInMainHand().getType().equals(Material.NAME_TAG) && !plugin.getConfig().getBoolean("toggleableoptions.usenametags")) {
                ItemStack nametag = player.getInventory().getItemInMainHand();
                if (!nametag.getItemMeta().hasDisplayName()) return;
                player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() + 1);
            }
        }

        //  Restock
        if (!VillagerUtilities.getMarker(villager, plugin)) {
            RestockVillager.call(villager, plugin, player);
        }
    }

    // Code for forcing players to disable villagers

    @EventHandler
    public void inventoryMove(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("toggleableoptions.preventtrading")) return;
        if (!(event.getInventory().getHolder() instanceof Villager)) return;
        Villager vil = (Villager) event.getInventory().getHolder();
        if (!VillagerUtilities.hasMarker(vil, plugin)) return;
        if (!VillagerUtilities.getMarker(vil, plugin)) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        //player.closeInventory();
        player.sendMessage(VillagerUtilities.colorcodes.cm(plugin.getConfig().getString("messages.VillagerMustBeDisabled")));
    }

    @EventHandler
    public void villagerTradeClick(TradeSelectEvent event) {
        if (event.isCancelled()) return;
        if (!plugin.getConfig().getBoolean("toggleableoptions.preventtrading")) return;
        if (!(event.getInventory().getHolder() instanceof Villager)) return;
        Villager vil = (Villager) event.getInventory().getHolder();
        if (!VillagerUtilities.hasMarker(vil, plugin)) return;
        if (!VillagerUtilities.getMarker(vil, plugin)) return;
        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage(VillagerUtilities.colorcodes.cm(plugin.getConfig().getString("messages.VillagerMustBeDisabled")));
    }

    // Event to handle cancellation of damage to villagers disable by the plugin
    @EventHandler
    public void onCancelVillagerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Villager && event.getDamager() instanceof Zombie)) return;

        Villager vil = (Villager) event.getEntity();

        if (VillagerUtilities.hasMarker(vil, plugin) && !VillagerUtilities.getMarker(vil, plugin)) {
            event.setCancelled(true);
        }
    }


    // Event to handle Villager updating
    @EventHandler
    public void afterTrade(InventoryCloseEvent event) {

        Player player = (Player) event.getPlayer();
        if(player.hasPermission("pvo.disable"))
            return;
        // check if inventory belongs to a Villager Trade Screen
        if (event.getInventory().getHolder() == null) return;
        if (event.getInventory().getHolder() instanceof WanderingTrader) return;
        if(event.getInventory().getType() != InventoryType.MERCHANT) return;

        Villager vil = (Villager) event.getInventory().getHolder();
        // make sure the villager is disabled
        if (!VillagerUtilities.hasMarker(vil, plugin)) return;
        if (VillagerUtilities.getMarker(vil, plugin)) return;

        // handle leveling
        VillagerLevelManager.call(vil, plugin, player);
    }

}

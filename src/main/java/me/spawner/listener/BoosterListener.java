package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class BoosterListener implements Listener {

    private final Spawner plugin;

    public BoosterListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTicketUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null) return;

        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || item == null || block.getType() != Material.SPAWNER) return;

        BoosterManager.Ticket ticket = plugin.boosters().readTicket(item);
        if (ticket == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!plugin.settings().boosterEnabled) {
            player.sendMessage(plugin.language().message("booster-disabled"));
            return;
        }
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        boolean perSpawner = plugin.stacks().enabled() && plugin.settings().perSpawnerBoost;
        int stack = plugin.stacks().size(spawner);
        if (perSpawner && stack > 1) {
            plugin.boosterSelect().open(player, block.getLocation(), event.getHand());
            return;
        }

        BoosterManager.ApplyResult result = plugin.boosters().applyTicket(spawner, ticket, perSpawner ? 1 : 0);
        for (String message : result.messages()) {
            player.sendMessage(message);
        }
        if (!result.applied()) return;

        if (item.getAmount() <= 1) {
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        plugin.jsonLogger().log(player, block, "BOOSTED");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoostedMobDeath(EntityDeathEvent event) {
        Integer percent = event.getEntity().getPersistentDataContainer()
                .get(plugin.keys().mobXpBoost, PersistentDataType.INTEGER);
        if (percent == null || percent <= 0) return;

        event.setDroppedExp(BoosterManager.boostXp(event.getDroppedExp(), percent));
    }
}

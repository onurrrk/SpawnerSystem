package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.menu.MenuLocks;
import me.spawner.utils.Scheduler;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.EnumSet;
import java.util.Set;

public final class SpawnerItemListener implements Listener {
    private static final Set<InventoryType> CONTAINERS = EnumSet.of(
            InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX,
            InventoryType.HOPPER, InventoryType.DISPENSER, InventoryType.DROPPER);

    private final Spawner plugin;

    public SpawnerItemListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Scheduler.runEntityTaskLater(plugin, player, () -> plugin.items().normalize(player.getInventory()), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        normalizeView(event.getPlayer() instanceof Player player ? player : null, event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        normalizeView(event.getPlayer() instanceof Player player ? player : null, event.getInventory());
    }

    private void normalizeView(Player player, Inventory top) {
        if (player != null) {
            plugin.items().normalize(player.getInventory());
        }
        if (isWorldContainer(top)) {
            plugin.items().normalize(top);
        }
    }

    private boolean isWorldContainer(Inventory inventory) {
        if (inventory == null || !CONTAINERS.contains(inventory.getType())) return false;
        if (MenuLocks.isPluginMenu(inventory)) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Container || inventory instanceof DoubleChestInventory;
    }
}

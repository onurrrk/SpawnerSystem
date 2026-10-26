package me.spawner.menu;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import me.spawner.booster.BoosterSelectMenu;
import me.spawner.storage.StorageMenu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuLocks {
    private final Map<String, UUID> owners = new ConcurrentHashMap<>();
    private final Set<UUID> transferring = ConcurrentHashMap.newKeySet();

    private static String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static boolean isPluginMenu(Inventory inventory) {
        if (inventory == null) return false;
        Object holder = inventory.getHolder();
        return holder instanceof SpawnerMenu.SpawnerMenuHolder
                || holder instanceof StorageMenu.StorageHolder
                || holder instanceof BoosterSelectMenu.SelectHolder;
    }

    public boolean tryAcquire(Location loc, Player player) {
        UUID me = player.getUniqueId();
        UUID result = owners.compute(key(loc), (k, current) -> {
            if (current == null || current.equals(me)) {
                return me;
            }
            Player other = Bukkit.getPlayer(current);
            return other != null && other.isOnline() ? current : me;
        });
        return me.equals(result);
    }

    public void release(Location loc, Player player) {
        owners.remove(key(loc), player.getUniqueId());
    }

    public void releaseAll(UUID id) {
        owners.values().removeIf(id::equals);
    }

    public void beginTransfer(UUID id) {
        transferring.add(id);
    }

    public void endTransfer(UUID id) {
        transferring.remove(id);
    }

    public boolean isTransferring(UUID id) {
        return transferring.contains(id);
    }
}

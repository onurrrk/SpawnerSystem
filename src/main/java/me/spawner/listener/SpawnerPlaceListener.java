package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.item.SpawnerItems;
import me.spawner.utils.Compat;
import me.spawner.utils.PDCUtils;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SpawnerPlaceListener implements Listener {
    private final Spawner plugin;

    public SpawnerPlaceListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;

        if (plugin.settings().chunkLimitEnabled) {
            String type = plugin.stacks().typeKey(event.getItemInHand());
            if (type == null) type = "EMPTY";
            int remaining = plugin.stacks().chunkRemaining(event.getBlock().getChunk(), event.getBlockPlaced(), type);
            if (remaining <= 0) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.language().message("chunk-limit-exceeded"));
                return;
            }
        }

        if (event.getBlockPlaced().getState() instanceof CreatureSpawner spawnerState) {
            PDCUtils.setBoolean(spawnerState.getPersistentDataContainer(), plugin.keys().playerPlaced, true);

            ItemMeta meta = event.getItemInHand().getItemMeta();
            if (meta != null) {
                if (meta.getPersistentDataContainer().has(plugin.keys().spawnerType, PersistentDataType.STRING)) {
                    String typeStr = meta.getPersistentDataContainer().get(plugin.keys().spawnerType, PersistentDataType.STRING);
                    if (typeStr != null && typeStr.startsWith(SpawnerItems.ITEM_PREFIX)) {
                        String material = typeStr.substring(SpawnerItems.ITEM_PREFIX.length());
                        if (plugin.menu().hasItemSpawner(material)) {
                            spawnerState.getPersistentDataContainer()
                                    .set(plugin.keys().itemSpawner, PersistentDataType.STRING, material);
                            plugin.items().ensureItemSpawnerType(spawnerState);
                        }
                    } else if (typeStr != null && !"EMPTY".equals(typeStr)) {
                        EntityType parsed = Compat.entityType(typeStr);
                        if (parsed != null) {
                            spawnerState.setSpawnedType(parsed);
                        }
                    }
                } else if (meta instanceof BlockStateMeta bsm
                        && bsm.getBlockState() instanceof CreatureSpawner itemSpawnerState) {
                    spawnerState.setSpawnedType(itemSpawnerState.getSpawnedType());
                }
            }

            plugin.spawnerSettings().apply(spawnerState);
            plugin.ticker().kickstart(spawnerState);
            spawnerState.update();
            plugin.holograms().createForSpawner(spawnerState);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlaceLog(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;
        if (!(event.getBlockPlaced().getState() instanceof CreatureSpawner spawnerState)) return;

        plugin.jsonLogger().log(event.getPlayer(), event.getBlockPlaced(), "PLACED");

        String itemType = plugin.items().itemSpawnerType(spawnerState);
        String typeName = itemType != null
                ? SpawnerItems.ITEM_PREFIX + itemType
                : (spawnerState.getSpawnedType() != null ? spawnerState.getSpawnedType().name() : "UNKNOWN");
        plugin.webhooks().sendPlaceWebhook(event.getPlayer(), event.getBlock().getLocation(), typeName);
    }
}

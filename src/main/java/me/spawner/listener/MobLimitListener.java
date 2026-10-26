package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.hologram.Hologram;
import me.spawner.spawner.ChunkMobCounter;
import me.spawner.utils.Compat;
import me.spawner.utils.Scheduler;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MobLimitListener implements Listener {
    private static final String EGG_SUFFIX = "_SPAWN_EGG";

    private final Spawner plugin;

    public MobLimitListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEggOnBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null || event.getItem() == null || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.SPAWNER) {
            handleSpawnEgg(event.getPlayer(), event.getItem(), block.getChunk(), event);
            return;
        }

        if (event.getItem().getType().name().endsWith(EGG_SUFFIX)) {
            if (block.getState() instanceof CreatureSpawner clicked) {
                if (plugin.items().itemSpawnerType(clicked) != null) {
                    event.setCancelled(true);
                    return;
                }
                if (plugin.stacks().size(clicked) > 1) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(plugin.language().message("stack-egg-denied"));
                    return;
                }
            }

            Location loc = block.getLocation();
            if (plugin.holograms().has(loc)) {
                Scheduler.runLocationTaskLater(plugin, loc, () -> {
                    if (loc.getBlock().getState() instanceof CreatureSpawner newSpawner) {
                        Hologram hologram = plugin.holograms().get(loc);
                        if (hologram != null) {
                            hologram.updateSpawnerState(newSpawner);
                        }
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onSpawnEggOnEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (event.getRightClicked() instanceof Ageable) {
            handleSpawnEgg(player, item, event.getRightClicked().getChunk(), event);
        }
    }

    private void handleSpawnEgg(Player player, ItemStack item, Chunk chunk, Cancellable event) {
        if (item == null || !item.getType().name().endsWith(EGG_SUFFIX)) return;

        EntityType spawnType = Compat.entityType(item.getType().name().replace(EGG_SUFFIX, ""));
        if (spawnType == null) return;

        int limit = plugin.settings().maxMobsPerChunk;
        ChunkMobCounter.ChunkCount chunkCount = plugin.mobCounter().get(chunk, spawnType);
        if (chunkCount.count.get() < limit) {
            chunkCount.count.incrementAndGet();
        } else {
            event.setCancelled(true);
            player.sendMessage(plugin.language().message("egg-chunk-limit-exceeded")
                    .replace("%type%", plugin.language().entityName(spawnType))
                    .replace("%limit%", String.valueOf(limit)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        Entity child = event.getEntity();
        EntityType type = child.getType();

        int currentCount = 0;
        for (Entity entity : child.getChunk().getEntities()) {
            if (entity.getType() == type && !entity.getUniqueId().equals(child.getUniqueId())) {
                currentCount++;
            }
        }

        int limit = plugin.settings().maxMobsPerChunk;
        if (currentCount >= limit) {
            event.setCancelled(true);
            event.setExperience(0);

            if (event.getBreeder() instanceof Player player) {
                player.sendMessage(plugin.language().message("egg-chunk-limit-exceeded")
                        .replace("%type%", plugin.language().entityName(type))
                        .replace("%limit%", String.valueOf(limit)));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        plugin.culling().onMobAdded(event.getEntity());
    }
}

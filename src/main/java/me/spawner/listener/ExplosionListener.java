package me.spawner.listener;

import org.bukkit.Material;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class ExplosionListener implements Listener {

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.SPAWNER);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.SPAWNER);
    }

    @EventHandler
    public void onWitherBreak(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Wither && event.getBlock().getType() == Material.SPAWNER) {
            event.setCancelled(true);
        }
    }
}

package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.utils.Scheduler;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class ChunkListener implements Listener {
    private final Spawner plugin;

    public ChunkListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        final Chunk chunk = event.getChunk();
        plugin.culling().chunkLoaded(chunk);
        Scheduler.runLocationTaskLater(plugin, chunk.getBlock(0, 0, 0).getLocation(),
                () -> plugin.spawnerSettings().processChunk(chunk), 2L);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.culling().trackAll(event.getEntities());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.culling().chunkUnloaded(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        plugin.ticker().untrackChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
        plugin.holograms().removeChunk(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }
}

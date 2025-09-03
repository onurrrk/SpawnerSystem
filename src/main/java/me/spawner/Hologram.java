package me.spawner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Hologram {
    private final JavaPlugin plugin;
    private CreatureSpawner spawner;
    private final Location location;
    private double visibleDistance;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Boolean> viewingPlayers = new ConcurrentHashMap<>();
    private FileConfiguration languageConfig;
    private boolean lastSpawnerState = false;

    private final List<Integer> entityIds = new ArrayList<>();
    private BukkitTask updaterTask;

    private final boolean isMobHologram;
    private LivingEntity targetMob;
    private List<String> currentMobHologramText;
    
    private Hologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, LivingEntity targetMob, Location location, double visibleDistance, boolean isMob, List<String> initialText) {
        this.plugin = plugin;
        this.languageConfig = languageConfig;
        this.spawner = spawner;
        this.location = location;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.isMobHologram = isMob;
        this.targetMob = targetMob;
        this.visibleDistance = visibleDistance;
        this.currentMobHologramText = (initialText != null) ? initialText : new ArrayList<>();

        if (isMobHologram) {
            this.entityIds.add((int) (Math.random() * Integer.MAX_VALUE));
            this.entityIds.add((int) (Math.random() * Integer.MAX_VALUE));
            startMobUpdater();
        } else {
            this.entityIds.add((int) (Math.random() * Integer.MAX_VALUE));
            this.entityIds.add((int) (Math.random() * Integer.MAX_VALUE));
            this.entityIds.add((int) (Math.random() * Integer.MAX_VALUE));
            startSpawnerUpdater();
        }
    }
    
    public static Hologram createSpawnerHologram(JavaPlugin plugin, FileConfiguration languageConfig, CreatureSpawner spawner, double visibleDistance) {
        return new Hologram(plugin, languageConfig, spawner, null, spawner.getLocation(), visibleDistance, false, null);
    }
    
    public static Hologram createMobHologram(JavaPlugin plugin, FileConfiguration languageConfig, LivingEntity mob, double visibleDistance, List<String> initialText) {
        return new Hologram(plugin, languageConfig, null, mob, mob.getLocation(), visibleDistance, true, initialText);
    }
    
    public Location getLocation() {
        return isMobHologram ? (targetMob != null ? targetMob.getLocation() : location) : location;
    }

    public double getVisibleDistance() {
        return this.visibleDistance;
    }
    
    public boolean isMobHologram() {
        return this.isMobHologram;
    }

    public void showTo(Player player) {
        if (viewingPlayers.containsKey(player.getUniqueId())) return;

        if (isMobHologram) {
            for (int i = 0; i < entityIds.size(); i++) {
                String text = (i < currentMobHologramText.size()) ? currentMobHologramText.get(i) : "";
                spawnArmorStand(player, entityIds.get(i), getMobHologramLocation(i), text);
            }
        } else {
            String[] lines = getHologramLines();
            for(int i = 0; i < entityIds.size(); i++) {
                spawnArmorStand(player, entityIds.get(i), getSpawnerHologramLocation(i), lines[i]);
            }
        }
        
        viewingPlayers.put(player.getUniqueId(), true);
    }

    public void hideFrom(Player player) {
        if (viewingPlayers.remove(player.getUniqueId()) == null) return;
        destroyEntities(player, this.entityIds);
    }

    public void updateMobHologramText(List<String> textLines) {
        if (!isMobHologram) return;
        this.currentMobHologramText = textLines;
        for (UUID viewerId : viewingPlayers.keySet()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                for (int i = 0; i < textLines.size() && i < entityIds.size(); i++) {
                    sendMetadataUpdate(viewer, entityIds.get(i), textLines.get(i), false);
                }
            }
        }
    }

    public void removeHologram() {
        if (this.updaterTask != null) {
            this.updaterTask.cancel();
            this.updaterTask = null;
        }

        if (this.entityIds.isEmpty()) return;

        try {
            PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, this.entityIds);
            
            for (UUID uuid : new ArrayList<>(viewingPlayers.keySet())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                     protocolManager.sendServerPacket(player, destroyPacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        viewingPlayers.clear();
    }

    private void spawnArmorStand(Player player, int entityId, Location loc, String text) {
        try {
            PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawnPacket.getIntegers().write(0, entityId);
            spawnPacket.getUUIDs().write(0, UUID.randomUUID());
            spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
            spawnPacket.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());
            spawnPacket.getBytes().write(0, (byte) (loc.getYaw() * 256.0F / 360.0F)).write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
            
            PacketContainer metadataPacket = createMetadataPacket(entityId, text, true);
            
            protocolManager.sendServerPacket(player, spawnPacket);
            protocolManager.sendServerPacket(player, metadataPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private PacketContainer createMetadataPacket(int entityId, String text, boolean isInitialSpawn) {
        PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadataPacket.getIntegers().write(0, entityId);
        WrappedDataWatcher watcher = new WrappedDataWatcher();

        if (text != null && !text.isEmpty()) {
            Optional<?> optionalComponent = Optional.of(WrappedChatComponent.fromChatMessage(text)[0].getHandle());
            watcher.setObject(new WrappedDataWatcherObject(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true)), optionalComponent);
            watcher.setObject(new WrappedDataWatcherObject(3, WrappedDataWatcher.Registry.get(Boolean.class)), true);
        }

        if (isInitialSpawn) {
            watcher.setObject(new WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class)), (byte) 0x20);
            watcher.setObject(new WrappedDataWatcherObject(15, WrappedDataWatcher.Registry.get(Byte.class)), (byte) 0x10);
        }
        
        List<WrappedDataValue> dataValues = watcher.getWatchableObjects().stream()
            .filter(Objects::nonNull)
            .map(entry -> new WrappedDataValue(entry.getWatcherObject().getIndex(), entry.getWatcherObject().getSerializer(), entry.getRawValue()))
            .collect(Collectors.toList());
        metadataPacket.getDataValueCollectionModifier().write(0, dataValues);
        return metadataPacket;
    }
    
    private void sendMetadataUpdate(Player player, int entityId, String text, boolean isInitialSpawn) {
        try {
            protocolManager.sendServerPacket(player, createMetadataPacket(entityId, text, isInitialSpawn));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void moveArmorStand(Player player, int entityId, Location newLoc, String text) {
        try {
            PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, Collections.singletonList(entityId));
            
            protocolManager.sendServerPacket(player, destroyPacket);
            spawnArmorStand(player, entityId, newLoc, text);
        } catch (Exception e) {
             e.printStackTrace();
        }
    }

    private void destroyEntities(Player player, List<Integer> entityIdsToDestroy) {
        try {
            PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, entityIdsToDestroy);
            protocolManager.sendServerPacket(player, destroyPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String lower = str.toLowerCase();
        return lower.substring(0, 1).toUpperCase() + lower.substring(1);
    }

    private String[] getHologramLines() {
        EntityType type = spawner.getSpawnedType();
        String translatedTypeName = (type != null) ? languageConfig.getString("entity-types." + type.name(), capitalize(type.name())) : languageConfig.getString("entity-types.EMPTY", "Empty");
        String modeTag = getLangString("mode-tags." + plugin.getConfig().getString("system", "advanced"));
        String[] lines = new String[3];
        lines[0] = getLangString("spawnable-status.mob_type").replace("%type%", translatedTypeName);
        lines[1] = getLangString("spawnable-status.break_with").replace("%mode%", modeTag);
        lines[2] = isSpawnerActive() ? getLangString("spawnable-status.can_spawn") : getLangString("spawnable-status.cannot_spawn");
        return lines;
    }

    private void startSpawnerUpdater() {
        this.lastSpawnerState = isSpawnerActive();
        this.updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisibilityAndState, 5L, 5L);
    }

    private void startMobUpdater() {
        this.updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (targetMob == null || targetMob.isDead() || !targetMob.isValid()) {
                removeHologram();
                return;
            }
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(targetMob.getWorld())) {
                    if (viewingPlayers.containsKey(player.getUniqueId())) {
                        hideFrom(player);
                    }
                    continue;
                }

                boolean canSee = player.getLocation().distanceSquared(targetMob.getLocation()) <= visibleDistance * visibleDistance;

                if (canSee && !viewingPlayers.containsKey(player.getUniqueId())) {
                    showTo(player);
                } else if (!canSee && viewingPlayers.containsKey(player.getUniqueId())) {
                    hideFrom(player);
                } else if (canSee) {
                    for (int i = 0; i < entityIds.size(); i++) {
                        String text = (i < currentMobHologramText.size()) ? currentMobHologramText.get(i) : "";
                        moveArmorStand(player, entityIds.get(i), getMobHologramLocation(i), text);
                    }
                }
            }
        }, 0L, 2L);
    }
    
    private void updateVisibilityAndState() {
        if (location.getBlock().getType() != Material.SPAWNER) {
            removeHologram(); 
            return;
        }
        
        refreshSpawner();
        
        boolean currentState = isSpawnerActive();
        boolean stateChanged = currentState != lastSpawnerState;
        if (stateChanged) lastSpawnerState = currentState;

        String stateLine = getHologramLines()[2];

        for (Player player : location.getWorld().getPlayers()) {
            boolean isVisible = viewingPlayers.containsKey(player.getUniqueId());
            if (player.getLocation().distanceSquared(location) <= visibleDistance * visibleDistance) {
                if (!isVisible) {
                    showTo(player);
                } else if (stateChanged) {
                    sendMetadataUpdate(player, this.entityIds.get(2), stateLine, false);
                }
            } else {
                if (isVisible) {
                    hideFrom(player);
                }
            }
        }
    }
    
    private void refreshSpawner() {
        if (location.getBlock().getState() instanceof CreatureSpawner) {
            spawner = (CreatureSpawner) location.getBlock().getState();
        }
    }
    
    private boolean isSpawnerActive() {
        if (spawner == null) return false;
        return spawner.getDelay() > 20;
    }
    
    private String getLangString(String path) {
        return ChatColor.translateAlternateColorCodes('&', languageConfig.getString(path, path));
    }

    private Location getSpawnerHologramLocation(int lineIndex) {
        return location.clone().add(0.5, 1.0 + (0.24 * (2 - lineIndex)), 0.5);
    }

    private Location getMobHologramLocation(int lineIndex) {
        Location baseLocation = targetMob.getLocation();
        
        final double bottomLineOffset = 0.3;
        final double lineSpacing = 0.25;
        int totalLines = entityIds.size();
        
        int linesFromTop = lineIndex;
        
        double yOffset = targetMob.getHeight() + bottomLineOffset + ((totalLines - 1) * lineSpacing) - (linesFromTop * lineSpacing);
        
        return baseLocation.clone().add(0, yOffset, 0);
    }
}
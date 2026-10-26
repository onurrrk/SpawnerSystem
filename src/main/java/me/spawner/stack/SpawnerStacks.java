package me.spawner.stack;

import me.spawner.Spawner;
import me.spawner.config.PluginSettings;
import me.spawner.hologram.Hologram;
import me.spawner.item.SpawnerItems;
import me.spawner.utils.ChunkUtils;
import me.spawner.utils.PDCUtils;
import org.bukkit.GameMode;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Predicate;

public final class SpawnerStacks {
    private final Spawner plugin;
    private final NamespacedKey stackKey;
    private final NamespacedKey carryKey;

    public SpawnerStacks(Spawner plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey(plugin, "spawner_stack");
        this.carryKey = new NamespacedKey(plugin, "stack_carry");
    }

    public NamespacedKey stackKey() {
        return stackKey;
    }

    public NamespacedKey carryKey() {
        return carryKey;
    }

    public boolean enabled() {
        return plugin.settings().stackingEnabled;
    }

    public int maxStack() {
        return plugin.settings().maxStack;
    }

    public String maxText() {
        return maxStack() >= Integer.MAX_VALUE / 4 ? "∞" : String.valueOf(maxStack());
    }

    public int size(CreatureSpawner spawner) {
        Integer value = spawner.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, value);
    }

    public void setSize(CreatureSpawner spawner, int size) {
        if (size <= 1) {
            spawner.getPersistentDataContainer().remove(stackKey);
            spawner.getPersistentDataContainer().remove(carryKey);
        } else {
            spawner.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, size);
        }
    }

    public String typeKey(CreatureSpawner spawner) {
        String item = plugin.items().itemSpawnerType(spawner);
        if (item != null) {
            return SpawnerItems.ITEM_PREFIX + item;
        }
        EntityType type = spawner.getSpawnedType();
        return type == null ? "EMPTY" : type.name();
    }

    public String typeKey(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        if (!item.hasItemMeta()) return "EMPTY";

        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(plugin.keys().spawnerType, PersistentDataType.STRING);
        if (stored != null) return stored;
        if (meta instanceof BlockStateMeta stateMeta && stateMeta.getBlockState() instanceof CreatureSpawner state) {
            EntityType type = state.getSpawnedType();
            return type == null ? "EMPTY" : type.name();
        }
        return "EMPTY";
    }

    public int chunkRemaining(Chunk chunk, Block excluded, String typeKey) {
        PluginSettings s = plugin.settings();
        if (!s.chunkLimitEnabled) return Integer.MAX_VALUE;

        boolean itemKind = typeKey.startsWith(SpawnerItems.ITEM_PREFIX);
        int limit;
        Predicate<CreatureSpawner> filter;
        if (s.separateChunkLimits) {
            limit = itemKind ? s.itemSpawnerLimitPerChunk : s.mobSpawnerLimitPerChunk;
            filter = spawner -> typeKey(spawner).startsWith(SpawnerItems.ITEM_PREFIX) == itemKind;
        } else {
            limit = s.spawnerLimitPerChunk;
            filter = spawner -> true;
        }
        if (s.perMobTypeLimit) {
            Predicate<CreatureSpawner> kind = filter;
            filter = spawner -> kind.test(spawner) && typeKey(spawner).equals(typeKey);
        }

        if (limit < 0) return Integer.MAX_VALUE;

        int used = ChunkUtils.countStacked(chunk, excluded, stackKey, filter);
        return (int) Math.max(0L, Math.min((long) limit - used, Integer.MAX_VALUE));
    }

    public void merge(Player player, Block block, boolean all) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        if (!PDCUtils.hasBoolean(spawner.getPersistentDataContainer(), plugin.keys().playerPlaced)) {
            player.sendMessage(plugin.language().message("stack-natural-denied"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        String heldType = typeKey(inHand);
        String blockType = typeKey(spawner);
        if (heldType == null || !heldType.equals(blockType)) {
            player.sendMessage(plugin.language().message("stack-type-mismatch"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int current = size(spawner);
        int room = maxStack() - current;
        if (room <= 0) {
            player.sendMessage(plugin.language().message("stack-full").replace("%max%", maxText()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int chunkRoom = chunkRemaining(block.getChunk(), null, blockType);
        if (chunkRoom <= 0) {
            player.sendMessage(plugin.language().message("chunk-limit-exceeded"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int want = all ? inHand.getAmount() : 1;
        int added = Math.min(want, Math.min(room, chunkRoom));
        int newSize = current + added;

        setSize(spawner, newSize);
        spawner.update();
        plugin.spawnerSettings().apply(spawner);

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (inHand.getAmount() <= added) {
                player.getInventory().setItemInMainHand(null);
            } else {
                inHand.setAmount(inHand.getAmount() - added);
            }
        }

        Hologram hologram = plugin.holograms().get(block.getLocation());
        if (hologram != null) {
            hologram.updateSpawnerState(spawner);
        }
        plugin.boosterSelect().refreshViewers(block.getLocation());

        player.sendMessage(plugin.language().message("stack-merged")
                .replace("%amount%", String.valueOf(added))
                .replace("%stack%", String.valueOf(newSize))
                .replace("%max%", maxText()));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.4f);
        plugin.jsonLogger().log(player, block, "STACKED");
    }
}

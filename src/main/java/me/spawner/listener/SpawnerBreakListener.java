package me.spawner.listener;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import me.spawner.hologram.Hologram;
import me.spawner.item.SpawnerItems;
import me.spawner.spawner.BreakChance;
import me.spawner.utils.PDCUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SpawnerBreakListener implements Listener {
    private static final String NATURAL_BREAK_PERMISSION = "spawner.break.natural";

    private record PendingBreak(CreatureSpawner state, String itemType, EntityType brokenType,
                                boolean lost, double chance, ItemStack tool) {
    }

    private final Map<BlockBreakEvent, PendingBreak> pendingBreaks = new ConcurrentHashMap<>();

    private final Spawner plugin;

    public SpawnerBreakListener(Spawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() != Material.SPAWNER) {
            if (plugin.items().isSpawnerPickaxe(player.getInventory().getItemInMainHand())) {
                player.sendMessage(plugin.language().message("sp-pickaxe-only"));
                event.setCancelled(true);
            }
            return;
        }

        if (!(block.getState() instanceof CreatureSpawner spawnerState)) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        boolean isNatural = !PDCUtils.hasBoolean(spawnerState.getPersistentDataContainer(), plugin.keys().playerPlaced);
        if (isNatural) {
            if (!plugin.settings().naturalSpawnerBreak) {
                player.sendMessage(plugin.language().message("natural-spawner-break-denied"));
                event.setCancelled(true);
                return;
            }
            if (plugin.settings().naturalBreakPermissionRequired && !player.hasPermission(NATURAL_BREAK_PERMISSION)) {
                player.sendMessage(plugin.language().message("natural-spawner-no-permission"));
                event.setCancelled(true);
                return;
            }
        }

        EntityType brokenType = spawnerState.getSpawnedType();
        String itemType = plugin.items().itemSpawnerType(spawnerState);
        if (itemType == null && brokenType == null && !plugin.settings().allowEmptySpawnerBreak) {
            player.sendMessage(plugin.language().message("empty-spawner-break-denied"));
            event.setCancelled(true);
            return;
        }

        int stack = plugin.stacks().size(spawnerState);
        if (!canBreakWith(player, itemInHand)) {
            event.setCancelled(true);
            return;
        }
        double chance = 100.0;
        boolean lost = false;
        if (plugin.settings().breakChanceEnabled) {
            chance = BreakChance.resolve(player, plugin.settings().breakChanceDefault);
            lost = !BreakChance.roll(chance);
        }

        if (stack > 1) {
            breakStacked(event, player, block, spawnerState, itemType, brokenType, stack, chance, itemInHand);
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        pendingBreaks.put(event, new PendingBreak(spawnerState, itemType, brokenType, lost, chance, itemInHand.clone()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnerBreakCommit(BlockBreakEvent event) {
        PendingBreak pending = pendingBreaks.remove(event);
        if (pending == null || event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        CreatureSpawner spawnerState = pending.state();
        String itemType = pending.itemType();
        EntityType brokenType = pending.brokenType();
        boolean lost = pending.lost();
        double chance = pending.chance();

        if (isAdvancedMode()) {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (plugin.items().isSpawnerPickaxe(current)) {
                consumePickaxeUse(player, current, 1);
            }
        }

        plugin.holograms().removeForSpawner(block.getLocation());
        plugin.boosterSelect().closeViewers(block.getLocation());
        plugin.storageMenu().closeViewers(block.getLocation());
        if (plugin.settings().boosterEnabled && plugin.settings().boosterReturnOnBreak) {
            giveTickets(player, plugin.boosters().drainAll(spawnerState));
        }
        if (plugin.settings().dropStoredOnBreak) {
            plugin.storage().dropContents(spawnerState, block.getLocation());
        }

        plugin.jsonLogger().log(player, block, lost ? "LOST" : "BROKE");

        if (lost) {
            String key = chance <= 0.0 ? "spawner-break-no-chance" : "spawner-break-lost";
            player.sendMessage(plugin.language().message(key).replace("%chance%", BreakChance.format(chance)));
        } else {
            ItemStack spawnerItem = spawnerItemFor(itemType, brokenType);
            if (plugin.items().giveOrDrop(player, spawnerItem)) {
                player.sendMessage(plugin.language().message("spawner-collected"));
            }
        }

        String typeName = itemType != null
                ? SpawnerItems.ITEM_PREFIX + itemType
                : (brokenType != null ? brokenType.name() : "UNKNOWN");
        plugin.webhooks().sendBreakWebhook(player, block.getLocation(), typeName, pending.tool());
    }

    private ItemStack spawnerItemFor(String itemType, EntityType brokenType) {
        if (itemType != null) {
            return plugin.items().createItemSpawner(itemType);
        }
        if (brokenType != null) {
            return plugin.items().createSpawnerItem(brokenType);
        }
        return plugin.items().createEmptySpawner();
    }

    private void giveSpawners(Player player, ItemStack template, int amount) {
        int maxSize = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            ItemStack part = template.clone();
            int portion = Math.min(maxSize, remaining);
            part.setAmount(portion);
            plugin.items().giveOrDrop(player, part);
            remaining -= portion;
        }
    }

    private void giveTickets(Player player, List<BoosterManager.Ticket> tickets) {
        for (BoosterManager.Ticket ticket : tickets) {
            plugin.items().giveOrDrop(player, plugin.boosters().createTicket(ticket, 1));
        }
    }

    private void breakStacked(BlockBreakEvent event, Player player, Block block, CreatureSpawner state,
                              String itemType, EntityType brokenType, int stack, double chance, ItemStack itemInHand) {
        event.setCancelled(true);

        int remove = player.isSneaking() ? stack - 1 : 1;
        if (isAdvancedMode()) {
            remove = Math.max(1, Math.min(remove, usesAvailable(itemInHand)));
            consumePickaxeUse(player, itemInHand, remove);
        }
        int collected = 0;
        for (int i = 0; i < remove; i++) {
            if (!plugin.settings().breakChanceEnabled || BreakChance.roll(chance)) {
                collected++;
            }
        }

        int left = stack - remove;
        plugin.stacks().setSize(state, left);
        List<BoosterManager.Ticket> tickets = plugin.boosters().drainAbove(state, left);
        state.update();
        plugin.spawnerSettings().apply(state);

        Hologram hologram = plugin.holograms().get(block.getLocation());
        if (hologram != null) {
            hologram.updateSpawnerState(state);
        }
        plugin.boosterSelect().refreshViewers(block.getLocation());

        if (collected > 0) {
            giveSpawners(player, spawnerItemFor(itemType, brokenType), collected);
            player.sendMessage(plugin.language().message("stack-collected")
                    .replace("%amount%", String.valueOf(collected))
                    .replace("%left%", String.valueOf(left)));
        } else {
            String key = chance <= 0.0 ? "spawner-break-no-chance" : "spawner-break-lost";
            player.sendMessage(plugin.language().message(key).replace("%chance%", BreakChance.format(chance)));
        }
        if (plugin.settings().boosterEnabled && plugin.settings().boosterReturnOnBreak) {
            giveTickets(player, tickets);
        }

        plugin.jsonLogger().log(player, block, collected > 0 ? "BROKE" : "LOST");
        String typeName = itemType != null
                ? SpawnerItems.ITEM_PREFIX + itemType
                : (brokenType != null ? brokenType.name() : "UNKNOWN");
        plugin.webhooks().sendBreakWebhook(player, block.getLocation(), typeName, itemInHand);
    }

    private boolean isAdvancedMode() {
        return "advanced".equals(plugin.settings().systemMode);
    }

    private int usesAvailable(ItemStack pickaxe) {
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) return 1;
        return Math.max(1, meta.getPersistentDataContainer().getOrDefault(plugin.keys().usesLeft, PersistentDataType.INTEGER, 1));
    }

    private boolean canBreakWith(Player player, ItemStack itemInHand) {
        String mode = plugin.settings().systemMode;

        if ("advanced".equals(mode)) {
            if (!plugin.items().isSpawnerPickaxe(itemInHand)) {
                player.sendMessage(plugin.language().message("advanced-pickaxe-required"));
                return false;
            }
            return true;
        }

        if ("classic".equals(mode)) {
            if (plugin.items().hasSilkTouch(itemInHand)) {
                return true;
            }
            player.sendMessage(plugin.language().message("classic-silk-required"));
        }
        return false;
    }

    private void consumePickaxeUse(Player player, ItemStack pickaxe, int amount) {
        ItemMeta meta = pickaxe.getItemMeta();
        int usesLeft = meta.getPersistentDataContainer()
                .getOrDefault(plugin.keys().usesLeft, PersistentDataType.INTEGER, 1) - amount;

        if (usesLeft > 0) {
            meta.getPersistentDataContainer().set(plugin.keys().usesLeft, PersistentDataType.INTEGER, usesLeft);
            List<String> newLore = plugin.language().list("spawner-pickaxe-item.lore").stream()
                    .map(line -> line.replace("%uses%", String.valueOf(usesLeft)))
                    .collect(Collectors.toList());
            meta.setLore(newLore);
            pickaxe.setItemMeta(meta);
        } else {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(plugin.language().message("pickaxe-broken"));
        }
    }
}

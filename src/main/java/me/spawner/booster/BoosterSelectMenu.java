package me.spawner.booster;

import me.spawner.Spawner;
import me.spawner.hologram.Hologram;
import me.spawner.menu.MenuLocks;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoosterSelectMenu implements Listener {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_NEXT = 53;

    public record Snapshot(int stack, Map<Integer, List<BoosterManager.EntryInfo>> slots) {
    }

    public static final class SelectHolder implements InventoryHolder {
        private Inventory inventory;
        private final Player viewer;
        private final Location location;
        private final EquipmentSlot hand;
        private final String key;
        private volatile int page = 1;
        private volatile int totalPages = 1;

        SelectHolder(Player viewer, Location location, EquipmentSlot hand) {
            this.viewer = viewer;
            this.location = location;
            this.hand = hand;
            this.key = Hologram.locationKey(location);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Spawner plugin;
    private final Map<String, Set<SelectHolder>> viewers = new ConcurrentHashMap<>();

    public BoosterSelectMenu(Spawner plugin) {
        this.plugin = plugin;
    }

    private Snapshot snapshot(CreatureSpawner spawner) {
        int stack = plugin.stacks().size(spawner);
        Map<Integer, List<BoosterManager.EntryInfo>> slots = new HashMap<>();
        for (int slot : plugin.boosters().slots(spawner)) {
            if (slot <= stack) {
                slots.put(slot, plugin.boosters().describeSlot(spawner, slot));
            }
        }
        return new Snapshot(stack, slots);
    }

    private String text(String path, String fallback) {
        return ColorUtils.color(plugin.getLanguageConfig().getString(path, fallback));
    }

    public void open(Player player, Location location, EquipmentSlot hand) {
        if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) return;

        MenuLocks locks = plugin.menuLocks();
        if (!locks.tryAcquire(location, player)) {
            player.sendMessage(plugin.language().withPrefix(text("menu.messages.in-use", "menu.messages.in-use")));
            return;
        }

        SelectHolder holder = new SelectHolder(player, location, hand == null ? EquipmentSlot.HAND : hand);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("booster.select.title", "&#6B7280Choose Spawner"));
        holder.inventory = inventory;
        render(holder, snapshot(spawner));

        UUID id = player.getUniqueId();
        locks.beginTransfer(id);
        InventoryView view;
        try {
            view = player.openInventory(inventory);
        } catch (RuntimeException e) {
            locks.release(location, player);
            throw e;
        } finally {
            locks.endTransfer(id);
        }
        if (view == null) {
            if (!MenuLocks.isPluginMenu(player.getOpenInventory().getTopInventory())) {
                locks.release(location, player);
            }
            return;
        }
        viewers.computeIfAbsent(holder.key, k -> ConcurrentHashMap.newKeySet()).add(holder);
    }

    public void refreshViewers(Location location) {
        Set<SelectHolder> set = viewers.get(Hologram.locationKey(location));
        if (set == null || set.isEmpty()) return;
        Scheduler.runLocationTask(plugin, location, () -> {
            if (!(location.getBlock().getState() instanceof CreatureSpawner spawner)) {
                closeViewers(location);
                return;
            }
            Snapshot snapshot = snapshot(spawner);
            for (SelectHolder holder : new ArrayList<>(set)) {
                Scheduler.runEntityTask(plugin, holder.viewer, () -> render(holder, snapshot));
            }
        });
    }

    public void closeViewers(Location location) {
        Set<SelectHolder> set = viewers.get(Hologram.locationKey(location));
        if (set == null || set.isEmpty()) return;
        for (SelectHolder holder : new ArrayList<>(set)) {
            Scheduler.runEntityTask(plugin, holder.viewer, holder.viewer::closeInventory);
        }
    }

    private ItemStack filler() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack pageButton(String path, int page) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        FileConfiguration lang = plugin.getLanguageConfig();
        meta.setDisplayName(ColorUtils.color(lang.getString(path + ".name", path)));
        List<String> lore = new ArrayList<>();
        for (String line : lang.getStringList(path + ".lore")) {
            lore.add(ColorUtils.color(line.replace("%page%", String.valueOf(page))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack entryItem(int index, List<BoosterManager.EntryInfo> infos) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        FileConfiguration lang = plugin.getLanguageConfig();
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.setDisplayName(ColorUtils.color(lang.getString("booster.select.entry", "&e&lSpawner #%index%")
                .replace("%index%", String.valueOf(index))));

        List<String> lore = new ArrayList<>();
        if (infos == null || infos.isEmpty()) {
            lore.add(ColorUtils.color(lang.getString("booster.info.empty", "&7No active booster.")));
        } else {
            List<String> template = lang.getStringList("booster.item.lore");
            String title = lang.getString("booster.info.entry-title", "&e&lBooster #%index%");
            int number = 1;
            for (BoosterManager.EntryInfo info : infos) {
                if (number > 1) lore.add("");
                lore.add(ColorUtils.color(title.replace("%index%", String.valueOf(number))));
                String time = info.remainingMillis() < 0
                        ? plugin.boosters().unlimitedText()
                        : plugin.boosters().formatDuration(info.remainingMillis());
                lore.addAll(plugin.boosters().valueLines(template, 1, info.range(), info.delaySeconds(),
                        info.xpPercent(), time));
                number++;
            }
        }
        lore.add("");
        lore.add(ColorUtils.color(lang.getString("booster.select.click", "&fClick: &eApply Booster")));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void render(SelectHolder holder, Snapshot snapshot) {
        int totalPages = Math.max(1, (snapshot.stack() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(holder.page, totalPages));
        holder.page = page;
        holder.totalPages = totalPages;

        Inventory inventory = holder.inventory;
        int offset = (page - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = offset + i + 1;
            inventory.setItem(i, index <= snapshot.stack() ? entryItem(index, snapshot.slots().get(index)) : null);
        }

        ItemStack filler = filler();
        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 1) {
            inventory.setItem(SLOT_PREVIOUS, pageButton("menu.storage.previous", page - 1));
        }
        if (page < totalPages) {
            inventory.setItem(SLOT_NEXT, pageButton("menu.storage.next", page + 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SelectHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SelectHolder holder)) return;
        Set<SelectHolder> opened = viewers.get(holder.key);
        if (opened != null) {
            opened.remove(holder);
            if (opened.isEmpty()) {
                viewers.remove(holder.key, opened);
            }
        }
        if (event.getPlayer() instanceof Player player && !plugin.menuLocks().isTransferring(player.getUniqueId())) {
            plugin.menuLocks().release(holder.location, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SelectHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        if (event.getClick().isCreativeAction() || event.getClick() == ClickType.DOUBLE_CLICK) return;

        if (slot < PER_PAGE) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.SPAWNER) return;
            int index = (holder.page - 1) * PER_PAGE + slot + 1;
            apply(player, holder, index);
        } else if (slot == SLOT_PREVIOUS && holder.page > 1) {
            holder.page = holder.page - 1;
            refresh(player, holder);
        } else if (slot == SLOT_NEXT && holder.page < holder.totalPages) {
            holder.page = holder.page + 1;
            refresh(player, holder);
        }
    }

    private void refresh(Player player, SelectHolder holder) {
        Scheduler.runLocationTask(plugin, holder.location, () -> {
            if (!(holder.location.getBlock().getState() instanceof CreatureSpawner spawner)) return;
            Snapshot snapshot = snapshot(spawner);
            Scheduler.runEntityTask(plugin, player, () -> render(holder, snapshot));
        });
    }

    private ItemStack handItem(Player player, SelectHolder holder) {
        return holder.hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private void apply(Player player, SelectHolder holder, int sub) {
        ItemStack hand = handItem(player, holder);
        BoosterManager.Ticket ticket = plugin.boosters().readTicket(hand);
        if (ticket == null) {
            player.closeInventory();
            return;
        }

        ItemStack refund = hand.clone();
        refund.setAmount(1);
        if (hand.getAmount() <= 1) {
            if (holder.hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }

        Scheduler.runLocationTask(plugin, holder.location, () -> {
            if (!(holder.location.getBlock().getState() instanceof CreatureSpawner spawner)) {
                Scheduler.runEntityTask(plugin, player, () -> plugin.items().giveOrDrop(player, refund));
                return;
            }

            if (sub > plugin.stacks().size(spawner)) {
                Snapshot stale = snapshot(spawner);
                Scheduler.runEntityTask(plugin, player, () -> {
                    plugin.items().giveOrDrop(player, refund);
                    player.sendMessage(plugin.language().message("booster-slot-invalid"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
                    render(holder, stale);
                });
                return;
            }

            BoosterManager.ApplyResult result = plugin.boosters().applyTicket(spawner, ticket, sub);
            Snapshot snapshot = snapshot(spawner);
            if (result.applied()) {
                plugin.jsonLogger().log(player, holder.location.getBlock(), "BOOSTED");
            }

            Scheduler.runEntityTask(plugin, player, () -> {
                if (!result.applied()) {
                    plugin.items().giveOrDrop(player, refund);
                }
                for (String message : result.messages()) {
                    player.sendMessage(message);
                }
                player.playSound(player.getLocation(),
                        result.applied() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
                render(holder, snapshot);
                if (plugin.boosters().readTicket(handItem(player, holder)) == null) {
                    player.closeInventory();
                }
            });
        });
    }
}

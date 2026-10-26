package me.spawner.storage;

import me.spawner.Spawner;
import me.spawner.menu.MenuLocks;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageMenu implements Listener {

    private static final int SIZE = 54;
    private static final int PER_PAGE = SpawnerStorage.SLOTS_PER_PAGE;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_TAKE_PAGE = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 51;
    private static final int SLOT_NEXT = 53;

    public static final class StorageHolder implements InventoryHolder {
        private Inventory inventory;
        private final Player viewer;
        private final Location location;
        private final String key;
        private volatile int page;
        private volatile int totalPages = 1;

        StorageHolder(Player viewer, Location location, String key) {
            this.viewer = viewer;
            this.location = location;
            this.key = key;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Spawner plugin;
    private final Map<String, Set<StorageHolder>> viewers = new ConcurrentHashMap<>();

    public StorageMenu(Spawner plugin) {
        this.plugin = plugin;
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private String text(String path, String fallback) {
        return ColorUtils.color(plugin.getLanguageConfig().getString(path, fallback));
    }

    private List<String> lines(String path) {
        List<String> raw = plugin.getLanguageConfig().getStringList(path);
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(ColorUtils.color(line));
        }
        return result;
    }

    private String message(String path) {
        return plugin.language().withPrefix(ColorUtils.color(plugin.getLanguageConfig().getString(path, path)));
    }

    private ItemStack button(Material material, String path, Map<String, String> values) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.setDisplayName(replace(text(path + ".name", path), values));
        List<String> lore = new ArrayList<>();
        for (String line : lines(path + ".lore")) {
            lore.add(replace(line, values));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String replace(String input, Map<String, String> values) {
        String result = input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
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

    public void open(Player player, Location location, SpawnerStorage.Snapshot snapshot) {
        MenuLocks locks = plugin.menuLocks();
        if (!locks.tryAcquire(location, player)) {
            player.sendMessage(message("menu.messages.in-use"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        StorageHolder holder = new StorageHolder(player, location, locationKey(location));
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("menu.storage.title", "&#6B7280Spawner Storage"));
        holder.inventory = inventory;
        holder.page = 1;
        render(holder, snapshot);

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
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private void render(StorageHolder holder, SpawnerStorage.Snapshot snapshot) {
        List<ItemStack> stacks = snapshot.stacks();
        int totalPages = Math.max(1, (stacks.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(1, Math.min(holder.page, totalPages));
        holder.page = page;
        holder.totalPages = totalPages;

        Inventory inventory = holder.inventory;
        int offset = (page - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = offset + i;
            inventory.setItem(i, index < stacks.size() ? stacks.get(index).clone() : null);
        }

        ItemStack filler = filler();
        for (int slot = PER_PAGE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        Map<String, String> values = Map.of(
                "%page%", String.valueOf(page),
                "%pages%", String.valueOf(totalPages),
                "%used%", String.valueOf(snapshot.usedSlots()),
                "%max%", String.valueOf(snapshot.maxSlots()));

        if (page > 1) {
            inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, "menu.storage.previous",
                    Map.of("%page%", String.valueOf(page - 1))));
        }
        if (page < totalPages) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, "menu.storage.next",
                    Map.of("%page%", String.valueOf(page + 1))));
        }
        inventory.setItem(SLOT_TAKE_PAGE, button(Material.HOPPER, "menu.storage.take-page", values));
        inventory.setItem(SLOT_BACK, button(Material.BARRIER, "menu.storage.back", values));
        inventory.setItem(SLOT_INFO, button(Material.PAPER, "menu.storage.info", values));
    }

    public void notifyViewers(Location location, SpawnerStorage.Snapshot snapshot) {
        Set<StorageHolder> set = viewers.get(locationKey(location));
        if (set == null || set.isEmpty()) return;

        for (StorageHolder holder : new ArrayList<>(set)) {
            Scheduler.runEntityTask(plugin, holder.viewer, () -> {
                if (set.contains(holder)) {
                    render(holder, snapshot);
                }
            });
        }
    }

    public void closeViewers(Location location) {
        Set<StorageHolder> set = viewers.get(locationKey(location));
        if (set == null || set.isEmpty()) return;
        for (StorageHolder holder : new ArrayList<>(set)) {
            Scheduler.runEntityTask(plugin, holder.viewer, holder.viewer::closeInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StorageHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;

        Set<StorageHolder> set = viewers.get(holder.key);
        if (set != null) {
            set.remove(holder);
            if (set.isEmpty()) {
                viewers.remove(holder.key, set);
            }
        }
        if (event.getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1f, 1f);
            if (!plugin.menuLocks().isTransferring(player.getUniqueId())) {
                plugin.menuLocks().release(holder.location, player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.storage().enabled()) {
            Scheduler.runEntityTaskLater(plugin, player, player::closeInventory, 1L);
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        if (event.getClick().isCreativeAction() || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) return;

        if (slot < PER_PAGE) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            take(player, holder, List.of(item.clone()));
        } else if (slot == SLOT_PREVIOUS) {
            if (holder.page > 1) {
                changePage(player, holder, holder.page - 1);
            }
        } else if (slot == SLOT_NEXT) {
            if (holder.page < holder.totalPages) {
                changePage(player, holder, holder.page + 1);
            }
        } else if (slot == SLOT_TAKE_PAGE) {
            List<ItemStack> requests = new ArrayList<>();
            for (int i = 0; i < PER_PAGE; i++) {
                ItemStack item = holder.inventory.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    requests.add(item.clone());
                }
            }
            if (!requests.isEmpty()) {
                take(player, holder, requests);
            }
        } else if (slot == SLOT_BACK) {
            plugin.menu().reopenMenu(player, holder.location);
        }
    }

    private void changePage(Player player, StorageHolder holder, int newPage) {
        holder.page = newPage;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        Scheduler.runLocationTask(plugin, holder.location, () -> {
            if (!(holder.location.getBlock().getState() instanceof CreatureSpawner spawner)) return;
            SpawnerStorage.Snapshot snapshot = plugin.storage().snapshot(spawner);
            Scheduler.runEntityTask(plugin, player, () -> render(holder, snapshot));
        });
    }

    private void take(Player player, StorageHolder holder, List<ItemStack> requests) {
        Inventory scratch = Bukkit.createInventory(null, 36);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < 36; i++) {
            scratch.setItem(i, contents[i] == null ? null : contents[i].clone());
        }

        List<ItemStack> plans = new ArrayList<>();
        for (ItemStack request : requests) {
            Map<Integer, ItemStack> left = scratch.addItem(request.clone());
            int leftover = 0;
            for (ItemStack rest : left.values()) {
                leftover += rest.getAmount();
            }
            int fit = request.getAmount() - leftover;
            if (fit > 0) {
                ItemStack plan = request.clone();
                plan.setAmount(fit);
                plans.add(plan);
            }
        }

        if (plans.isEmpty()) {
            player.sendMessage(message("menu.messages.storage-inventory-full"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Scheduler.runLocationTask(plugin, holder.location, () -> {
            if (!(holder.location.getBlock().getState() instanceof CreatureSpawner spawner)) return;

            SpawnerStorage storage = plugin.storage();
            List<SpawnerStorage.Entry> entries = storage.read(spawner);
            List<ItemStack> granted = new ArrayList<>();
            int total = 0;
            for (ItemStack plan : plans) {
                long removed = storage.remove(entries, plan, plan.getAmount());
                if (removed > 0) {
                    ItemStack give = plan.clone();
                    give.setAmount((int) removed);
                    granted.add(give);
                    total += (int) removed;
                }
            }
            if (!granted.isEmpty()) {
                storage.write(spawner, entries);
                spawner.update();
            }
            SpawnerStorage.Snapshot snapshot = storage.snapshotEntries(entries, storage.maxSlots(spawner));
            int taken = total;

            Scheduler.runEntityTask(plugin, player, () -> {
                for (ItemStack give : granted) {
                    plugin.items().giveOrDrop(player, give);
                }
                if (taken > 0) {
                    player.sendMessage(message("menu.messages.storage-taken").replace("%amount%", String.valueOf(taken)));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                }
                render(holder, snapshot);
            });
            notifyViewers(holder.location, snapshot);
        });
    }
}

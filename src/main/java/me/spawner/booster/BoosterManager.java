package me.spawner.booster;

import me.spawner.Spawner;
import me.spawner.hologram.Hologram;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class BoosterManager {

    public record Booster(int range, int delaySeconds, int xpPercent, long expiresAt) {
        public boolean unlimited() {
            return expiresAt < 0;
        }

        public boolean expired(long now) {
            return expiresAt >= 0 && now >= expiresAt;
        }

        public long remaining(long now) {
            return unlimited() ? -1L : Math.max(0L, expiresAt - now);
        }
    }

    public record Ticket(int range, int delaySeconds, int xpPercent, int minutes) {
        public boolean unlimited() {
            return minutes < 0;
        }
    }

    private final Spawner plugin;
    private final Map<String, Long> scheduled = new ConcurrentHashMap<>();

    public BoosterManager(Spawner plugin) {
        this.plugin = plugin;
    }

    public void stop() {
        scheduled.clear();
    }

    public static int boostXp(int base, int percent) {
        if (percent <= 0 || base <= 0) {
            return base;
        }
        return (int) Math.round(base * (100.0 + percent) / 100.0);
    }

    public record EntryInfo(int range, int delaySeconds, int xpPercent, long remainingMillis, int slot) {
    }

    public record Group(Booster boost, int count) {
    }

    public record ApplyResult(boolean applied, List<String> messages) {
    }

    List<String> valueLines(List<String> template, int startIndex, int range, int delay, int xp, String duration) {
        List<String> lines = new ArrayList<>();
        for (int i = startIndex; i < template.size(); i++) {
            if (i == 1 && range <= 0) continue;
            if (i == 2 && delay <= 0) continue;
            if (i == 3 && xp <= 0) continue;
            lines.add(ColorUtils.color(template.get(i)
                    .replace("%range%", String.valueOf(range))
                    .replace("%delay%", String.valueOf(delay))
                    .replace("%xp%", String.valueOf(xp))
                    .replace("%duration%", duration)));
        }
        return lines;
    }

    public ItemStack createTicket(Ticket ticket, int amount) {
        FileConfiguration lang = plugin.language().config();
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();

        String duration = ticket.unlimited() ? unlimitedText() : formatDuration(ticket.minutes() * 60_000L);
        meta.setDisplayName(ColorUtils.color(lang.getString("booster.item.name", "&6&lSpawner Booster")));
        meta.setLore(valueLines(lang.getStringList("booster.item.lore"), 0,
                ticket.range(), ticket.delaySeconds(), ticket.xpPercent(), duration));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.keys().boostRange, PersistentDataType.INTEGER, ticket.range());
        pdc.set(plugin.keys().boostDelay, PersistentDataType.INTEGER, ticket.delaySeconds());
        pdc.set(plugin.keys().boostXp, PersistentDataType.INTEGER, ticket.xpPercent());
        pdc.set(plugin.keys().boostMinutes, PersistentDataType.INTEGER, ticket.minutes());

        item.setItemMeta(meta);
        return item;
    }

    public List<EntryInfo> describe(CreatureSpawner spawner) {
        List<EntryInfo> infos = new ArrayList<>(describeSlot(spawner, 0));
        for (int slot : slots(spawner)) {
            infos.addAll(describeSlot(spawner, slot));
        }
        return infos;
    }

    public List<EntryInfo> describeSlot(CreatureSpawner spawner, int slot) {
        long now = System.currentTimeMillis();
        List<Entry> active = activeEntries(spawner, slot);
        active.sort((x, y) -> {
            if (x.unlimited() != y.unlimited()) return x.unlimited() ? 1 : -1;
            return Long.compare(x.expiresAt(), y.expiresAt());
        });

        List<EntryInfo> infos = new ArrayList<>();
        for (Entry entry : active) {
            infos.add(new EntryInfo(entry.range(), entry.delaySeconds(), entry.xpPercent(),
                    entry.unlimited() ? -1L : Math.max(0L, entry.expiresAt() - now), slot));
        }
        return infos;
    }

    public ItemStack createInfoItem(List<EntryInfo> infos) {
        FileConfiguration lang = plugin.language().config();
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtils.color(lang.getString("booster.info.name", "&6&lActive Boosters")));

        List<String> lore = new ArrayList<>();
        if (infos.isEmpty()) {
            lore.add(ColorUtils.color(lang.getString("booster.info.empty", "&7No active booster.")));
        } else {
            List<String> template = lang.getStringList("booster.item.lore");
            String title = lang.getString("booster.info.entry-title", "&e&lBooster #%index%");
            int index = 1;
            for (EntryInfo info : infos) {
                if (index > 1) lore.add("");
                String slotSuffix = info.slot() > 0 ? " &7(#" + info.slot() + ")" : "";
                lore.add(ColorUtils.color(title.replace("%index%", String.valueOf(index)) + slotSuffix));
                String time = info.remainingMillis() < 0 ? unlimitedText() : formatDuration(info.remainingMillis());
                lore.addAll(valueLines(template, 1, info.range(), info.delaySeconds(), info.xpPercent(), time));
                index++;
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public Ticket readTicket(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer minutes = pdc.get(plugin.keys().boostMinutes, PersistentDataType.INTEGER);
        if (minutes == null) return null;

        return new Ticket(
                pdc.getOrDefault(plugin.keys().boostRange, PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(plugin.keys().boostDelay, PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(plugin.keys().boostXp, PersistentDataType.INTEGER, 0),
                minutes);
    }

    private record Entry(int range, int delaySeconds, int xpPercent, long expiresAt, int originalDelay) {
        Entry(int range, int delaySeconds, int xpPercent, long expiresAt) {
            this(range, delaySeconds, xpPercent, expiresAt, delaySeconds);
        }

        boolean unlimited() {
            return expiresAt < 0;
        }

        boolean expired(long now) {
            return expiresAt >= 0 && now >= expiresAt;
        }
    }

    private final Map<Integer, NamespacedKey> slotKeys = new ConcurrentHashMap<>();
    private NamespacedKey slotsKey;

    private NamespacedKey listKey(int slot) {
        if (slot <= 0) return plugin.keys().boostList;
        return slotKeys.computeIfAbsent(slot, i -> new NamespacedKey(plugin, "boost_list_" + i));
    }

    private NamespacedKey slotsKey() {
        if (slotsKey == null) {
            slotsKey = new NamespacedKey(plugin, "boost_slots");
        }
        return slotsKey;
    }

    public java.util.Set<Integer> slots(CreatureSpawner spawner) {
        java.util.Set<Integer> result = new java.util.TreeSet<>();
        String raw = spawner.getPersistentDataContainer().get(slotsKey(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (slot > 0) result.add(slot);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private void writeSlots(CreatureSpawner spawner, java.util.Set<Integer> slots) {
        if (slots.isEmpty()) {
            spawner.getPersistentDataContainer().remove(slotsKey());
            return;
        }
        spawner.getPersistentDataContainer().set(slotsKey(), PersistentDataType.STRING,
                slots.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private List<Entry> entries(CreatureSpawner spawner, int slot) {
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        List<Entry> list = new ArrayList<>();

        String raw = pdc.get(listKey(slot), PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(";")) {
                String[] fields = part.split(",");
                if (fields.length != 4 && fields.length != 5) continue;
                try {
                    int appliedDelay = Integer.parseInt(fields[1]);
                    int originalDelay = fields.length == 5 ? Integer.parseInt(fields[4]) : appliedDelay;
                    list.add(new Entry(Integer.parseInt(fields[0]), appliedDelay,
                            Integer.parseInt(fields[2]), Long.parseLong(fields[3]), originalDelay));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (slot <= 0) {
            Long legacyExpires = pdc.get(plugin.keys().boostExpires, PersistentDataType.LONG);
            if (legacyExpires != null) {
                list.add(new Entry(
                        pdc.getOrDefault(plugin.keys().boostRange, PersistentDataType.INTEGER, 0),
                        pdc.getOrDefault(plugin.keys().boostDelay, PersistentDataType.INTEGER, 0),
                        pdc.getOrDefault(plugin.keys().boostXp, PersistentDataType.INTEGER, 0),
                        legacyExpires));
            }
        }
        return list;
    }

    private void store(CreatureSpawner spawner, int slot, List<Entry> list) {
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        if (slot <= 0) {
            pdc.remove(plugin.keys().boostRange);
            pdc.remove(plugin.keys().boostDelay);
            pdc.remove(plugin.keys().boostXp);
            pdc.remove(plugin.keys().boostExpires);
        }

        if (list.isEmpty()) {
            pdc.remove(listKey(slot));
        } else {
            String serialized = list.stream()
                    .map(e -> e.range() + "," + e.delaySeconds() + "," + e.xpPercent() + "," + e.expiresAt() + ","
                            + e.originalDelay())
                    .collect(Collectors.joining(";"));
            pdc.set(listKey(slot), PersistentDataType.STRING, serialized);
        }

        if (slot > 0) {
            java.util.Set<Integer> known = slots(spawner);
            boolean changed = list.isEmpty() ? known.remove(slot) : known.add(slot);
            if (changed) writeSlots(spawner, known);
        }
    }

    private List<Entry> activeEntries(CreatureSpawner spawner, int slot) {
        long now = System.currentTimeMillis();
        List<Entry> active = new ArrayList<>();
        for (Entry entry : entries(spawner, slot)) {
            if (!entry.expired(now)) {
                active.add(entry);
            }
        }
        return active;
    }

    private Booster sum(List<Entry> active) {
        if (active.isEmpty()) return null;

        long range = 0;
        long delay = 0;
        long xp = 0;
        boolean unlimited = false;
        long latest = -1L;
        for (Entry entry : active) {
            range += Math.max(0, entry.range());
            delay += Math.max(0, entry.delaySeconds());
            xp += Math.max(0, entry.xpPercent());
            if (entry.unlimited()) {
                unlimited = true;
            } else {
                latest = Math.max(latest, entry.expiresAt());
            }
        }
        return new Booster(clamp(range), clamp(delay), clamp(xp), unlimited ? -1L : latest);
    }

    public Booster active(CreatureSpawner spawner) {
        return sum(activeEntries(spawner, 0));
    }

    public Booster activeFor(CreatureSpawner spawner, int slot) {
        List<Entry> combined = activeEntries(spawner, 0);
        if (slot > 0) {
            combined.addAll(activeEntries(spawner, slot));
        }
        return sum(combined);
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE / 2, value);
    }

    public Booster activeIfEnabled(CreatureSpawner spawner) {
        return plugin.settings().boosterEnabled ? active(spawner) : null;
    }

    public List<Group> groups(CreatureSpawner spawner, int stackSize) {
        int size = Math.max(1, stackSize);
        if (!plugin.settings().boosterEnabled) {
            return List.of(new Group(null, size));
        }

        List<Entry> base = activeEntries(spawner, 0);
        List<Group> result = new ArrayList<>();
        int plain = size;
        for (int slot : slots(spawner)) {
            if (slot > size) continue;
            List<Entry> own = activeEntries(spawner, slot);
            if (own.isEmpty()) continue;
            List<Entry> combined = new ArrayList<>(base);
            combined.addAll(own);
            result.add(new Group(sum(combined), 1));
            plain--;
        }
        if (plain > 0) {
            result.add(new Group(sum(base), plain));
        }
        return result;
    }

    public int totalDelaySeconds(CreatureSpawner spawner, int slot) {
        long total = 0;
        for (Entry entry : activeEntries(spawner, 0)) {
            total += Math.max(0, entry.delaySeconds());
        }
        if (slot > 0) {
            for (Entry entry : activeEntries(spawner, slot)) {
                total += Math.max(0, entry.delaySeconds());
            }
        }
        return clamp(total);
    }

    public int count(CreatureSpawner spawner, int slot) {
        return activeEntries(spawner, slot).size();
    }

    public boolean clearIfExpired(CreatureSpawner spawner) {
        boolean changed = false;
        PersistentDataContainer pdc = spawner.getPersistentDataContainer();
        boolean hasLegacy = pdc.get(plugin.keys().boostExpires, PersistentDataType.LONG) != null;

        List<Integer> all = new ArrayList<>();
        all.add(0);
        all.addAll(slots(spawner));
        for (int slot : all) {
            List<Entry> entries = entries(spawner, slot);
            if (entries.isEmpty()) {
                if (slot > 0) {
                    java.util.Set<Integer> known = slots(spawner);
                    if (known.remove(slot)) {
                        writeSlots(spawner, known);
                        changed = true;
                    }
                }
                continue;
            }
            List<Entry> active = activeEntries(spawner, slot);
            if (!(slot == 0 && hasLegacy) && active.size() == entries.size()) continue;
            store(spawner, slot, active);
            changed = true;
        }
        return changed;
    }

    public void write(CreatureSpawner spawner, Ticket ticket, int slot) {
        write(spawner, ticket, slot, ticket.delaySeconds());
    }

    private void write(CreatureSpawner spawner, Ticket ticket, int slot, int originalDelay) {
        long expires = ticket.unlimited() ? -1L : System.currentTimeMillis() + ticket.minutes() * 60_000L;
        List<Entry> list = activeEntries(spawner, slot);
        list.add(new Entry(ticket.range(), ticket.delaySeconds(), ticket.xpPercent(), expires, originalDelay));
        store(spawner, slot, list);
    }

    private List<Ticket> drainSlot(CreatureSpawner spawner, int slot) {
        long now = System.currentTimeMillis();
        List<Ticket> tickets = new ArrayList<>();
        for (Entry entry : activeEntries(spawner, slot)) {
            int minutes = entry.unlimited() ? -1
                    : (int) Math.max(1L, (entry.expiresAt() - now + 59_999L) / 60_000L);
            tickets.add(new Ticket(entry.range(), entry.originalDelay(), entry.xpPercent(), minutes));
        }
        store(spawner, slot, new ArrayList<>());
        return tickets;
    }

    public List<Ticket> drainAll(CreatureSpawner spawner) {
        List<Ticket> tickets = new ArrayList<>(drainSlot(spawner, 0));
        for (int slot : slots(spawner)) {
            tickets.addAll(drainSlot(spawner, slot));
        }
        return tickets;
    }

    public List<Ticket> drainAbove(CreatureSpawner spawner, int stackSize) {
        List<Ticket> tickets = new ArrayList<>();
        for (int slot : slots(spawner)) {
            if (slot > stackSize) {
                tickets.addAll(drainSlot(spawner, slot));
            }
        }
        return tickets;
    }

    public ApplyResult applyTicket(CreatureSpawner spawner, Ticket ticket, int slot) {
        if (!plugin.settings().boosterEnabled) {
            return new ApplyResult(false, List.of(plugin.language().message("booster-disabled")));
        }

        int activeCount = count(spawner, slot);
        if (activeCount > 0) {
            if (!plugin.settings().boosterAllowStacking) {
                return new ApplyResult(false, List.of(plugin.language().message("booster-already-active")
                        .replace("%duration%", formatRemaining(activeFor(spawner, slot)))));
            }
            if (activeCount >= plugin.settings().boosterMaxStacked) {
                return new ApplyResult(false, List.of(plugin.language().message("booster-stack-limit")
                        .replace("%max%", String.valueOf(plugin.settings().boosterMaxStacked))));
            }
        }

        int appliedDelay = ticket.delaySeconds();
        boolean delayReduced = false;
        if (appliedDelay > 0) {
            boolean itemSpawner = plugin.items().itemSpawnerType(spawner) != null;
            int room = Math.max(0, plugin.spawnerSettings().maxDelayReductionSeconds(itemSpawner)
                    - totalDelaySeconds(spawner, slot));
            if (appliedDelay > room) {
                appliedDelay = room;
                delayReduced = true;
            }
        }
        if (appliedDelay <= 0 && ticket.range() <= 0 && ticket.xpPercent() <= 0) {
            return new ApplyResult(false, List.of(plugin.language().message("booster-delay-maxed-no-effect")));
        }
        Ticket effective = new Ticket(ticket.range(), appliedDelay, ticket.xpPercent(), ticket.minutes());

        write(spawner, effective, slot, ticket.delaySeconds());
        spawner.update();
        plugin.spawnerSettings().apply(spawner);

        int blockDelay = plugin.spawnerSettings().effectiveDelay(spawner);
        if (spawner.getDelay() > blockDelay) {
            spawner.setDelay(blockDelay);
        }
        spawner.update();

        scheduleExpiry(spawner);
        Hologram hologram = plugin.holograms().get(spawner.getLocation());
        if (hologram != null) {
            hologram.updateSpawnerState(spawner);
        }

        String duration = ticket.unlimited() ? unlimitedText() : formatDuration(ticket.minutes() * 60_000L);
        List<String> messages = new ArrayList<>();
        messages.add(plugin.language().message("booster-applied")
                .replace("%range%", String.valueOf(ticket.range()))
                .replace("%delay%", String.valueOf(effective.delaySeconds()))
                .replace("%xp%", String.valueOf(effective.xpPercent()))
                .replace("%duration%", duration));
        if (delayReduced) {
            messages.add(plugin.language().message(appliedDelay > 0 ? "booster-delay-partial" : "booster-delay-maxed")
                    .replace("%delay%", String.valueOf(appliedDelay)));
        }
        return new ApplyResult(true, messages);
    }

    private long nextExpiry(CreatureSpawner spawner) {
        long next = -1L;
        List<Integer> all = new ArrayList<>();
        all.add(0);
        all.addAll(slots(spawner));
        for (int slot : all) {
            for (Entry entry : activeEntries(spawner, slot)) {
                if (entry.unlimited()) continue;
                if (next < 0 || entry.expiresAt() < next) {
                    next = entry.expiresAt();
                }
            }
        }
        return next;
    }

    public void scheduleExpiry(CreatureSpawner spawner) {
        long next = nextExpiry(spawner);
        if (next < 0) return;

        Location loc = spawner.getLocation();
        String key = Hologram.locationKey(loc);
        Long already = scheduled.get(key);
        if (already != null && already == next) return;

        scheduled.put(key, next);
        long ticks = Math.max(1L, (next - System.currentTimeMillis()) / 50L + 1L);
        Scheduler.runLocationTaskLater(plugin, loc, () -> expire(loc, next), ticks);
    }

    private void expire(Location loc, long expiresAt) {
        scheduled.remove(Hologram.locationKey(loc), expiresAt);
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        if (loc.getBlock().getType() != Material.SPAWNER) return;
        if (!(loc.getBlock().getState() instanceof CreatureSpawner state)) return;

        plugin.spawnerSettings().apply(state);
        Hologram hologram = plugin.holograms().get(loc);
        if (hologram != null) {
            hologram.updateSpawnerState(state);
        }
        plugin.boosterSelect().refreshViewers(loc);
        scheduleExpiry(state);
    }

    public String unlimitedText() {
        return plugin.language().config().getString("booster.time.unlimited", "Unlimited");
    }

    public String formatDuration(long millis) {
        FileConfiguration lang = plugin.language().config();
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        String hourUnit = lang.getString("booster.time.hour", "h");
        String minuteUnit = lang.getString("booster.time.minute", "m");
        String secondUnit = lang.getString("booster.time.second", "s");

        StringBuilder builder = new StringBuilder();
        if (hours > 0) builder.append(hours).append(hourUnit);
        if (minutes > 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(minutes).append(minuteUnit);
        }
        if (seconds > 0 && hours == 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(seconds).append(secondUnit);
        }
        if (builder.length() == 0) {
            builder.append(0).append(secondUnit);
        }
        return builder.toString();
    }

    public String formatRemaining(Booster booster) {
        if (booster.unlimited()) {
            return unlimitedText();
        }
        return formatDuration(booster.remaining(System.currentTimeMillis()));
    }
}

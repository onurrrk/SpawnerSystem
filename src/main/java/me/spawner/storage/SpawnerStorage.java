package me.spawner.storage;

import me.spawner.Spawner;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerStorage {

    public static final class Entry {
        private final ItemStack template;
        private long amount;

        Entry(ItemStack template, long amount) {
            ItemStack copy = template.clone();
            copy.setAmount(1);
            this.template = copy;
            this.amount = amount;
        }

        public ItemStack template() {
            return template;
        }

        public long amount() {
            return amount;
        }

        int stackSize() {
            return Math.max(1, template.getMaxStackSize());
        }

        long slots() {
            long size = stackSize();
            return (amount + size - 1) / size;
        }
    }

    public static final class StoredEntries extends ArrayList<Entry> {
        private final List<String> unreadable = new ArrayList<>();
        private boolean locked;
    }

    public record Snapshot(List<ItemStack> stacks, int usedSlots, int maxSlots) {
    }

    private static final String FORMAT_PREFIX = "v2:";

    public static final int SLOTS_PER_PAGE = 45;

    private final Spawner plugin;
    private final NamespacedKey itemsKey;
    private final NamespacedKey xpKey;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private record CachedRead(String raw, List<Entry> entries) {
    }

    private static final int READ_CACHE_LIMIT = 1024;
    private final Map<String, CachedRead> readCache = new ConcurrentHashMap<>();

    private static final String[] DENIED_PREFIXES = {
            "java.lang.reflect.", "java.lang.invoke.", "java.lang.Runtime", "java.lang.Process",
            "java.util.PriorityQueue", "java.rmi.", "java.net.", "java.beans.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.apache.", "org.codehaus.", "org.springframework.", "org.hibernate.", "org.mozilla.", "org.python.",
            "bsh.", "clojure.", "groovy.", "com.mchange.", "com.fasterxml.", "org.jboss.", "org.omg.", "org.w3c.",
            "org.xml."
    };

    private static final ObjectInputFilter STORAGE_FILTER = info -> {
        if (info.depth() > 128 || info.arrayLength() > 16_000_000L || info.references() > 4_000_000L) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        String name = type.getName();
        for (String prefix : DENIED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return ObjectInputFilter.Status.REJECTED;
            }
        }
        return ObjectInputFilter.Status.UNDECIDED;
    };

    public SpawnerStorage(Spawner plugin) {
        this.plugin = plugin;
        this.itemsKey = new NamespacedKey(plugin, "storage_items");
        this.xpKey = new NamespacedKey(plugin, "stored_xp");
    }

    public boolean enabled() {
        return plugin.settings().storageEnabled;
    }

    public int maxPages(CreatureSpawner spawner) {
        long pages = Math.max(1, plugin.settings().storageMaxPages);
        if (plugin.stacks().enabled()) {
            pages += plugin.stacks().size(spawner) - 1L;
        }
        return (int) Math.min(pages, Integer.MAX_VALUE / SLOTS_PER_PAGE);
    }

    public int maxSlots(CreatureSpawner spawner) {
        return maxPages(spawner) * SLOTS_PER_PAGE;
    }

    public boolean isLocked(List<Entry> entries) {
        return entries instanceof StoredEntries stored && stored.locked;
    }

    private void warnUnreadable(CreatureSpawner spawner, String detail) {
        String key = spawner.getWorld().getName() + "," + spawner.getX() + "," + spawner.getY() + "," + spawner.getZ();
        if (warned.add(key)) {
            plugin.getLogger().warning("Spawner storage at " + key + " has unreadable data (" + detail
                    + "). It was kept untouched and will not be overwritten.");
        }
    }

    private Entry decode(String part) throws Exception {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(part)))) {
            in.setObjectInputFilter(STORAGE_FILTER);
            Object object = in.readObject();
            long amount = in.readLong();
            if (object instanceof ItemStack stack && amount > 0) {
                return new Entry(stack, amount);
            }
            return null;
        }
    }

    private String encode(Entry entry) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(entry.template());
            out.writeLong(entry.amount());
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public List<Entry> read(CreatureSpawner spawner) {
        StoredEntries list = new StoredEntries();
        String raw = spawner.getPersistentDataContainer().get(itemsKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return list;

        if (raw.startsWith(FORMAT_PREFIX)) {
            String cacheKey = cacheKey(spawner);
            CachedRead cached = readCache.get(cacheKey);
            if (cached != null && cached.raw().equals(raw)) {
                for (Entry entry : cached.entries()) {
                    list.add(new Entry(entry.template, entry.amount));
                }
                return list;
            }

            String body = raw.substring(FORMAT_PREFIX.length());
            for (String part : body.split(";")) {
                if (part.isEmpty()) continue;
                try {
                    Entry entry = decode(part);
                    if (entry != null) {
                        list.add(entry);
                    }
                } catch (Exception e) {
                    list.unreadable.add(part);
                }
            }
            if (!list.unreadable.isEmpty()) {
                warnUnreadable(spawner, list.unreadable.size() + " unreadable stack(s)");
            } else {
                remember(cacheKey, raw, list);
            }
            return list;
        }

        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(raw)))) {
            in.setObjectInputFilter(STORAGE_FILTER);
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                Object object = in.readObject();
                long amount = in.readLong();
                if (object instanceof ItemStack stack && amount > 0) {
                    list.add(new Entry(stack, amount));
                }
            }
        } catch (Exception e) {
            list.clear();
            list.locked = true;
            warnUnreadable(spawner, String.valueOf(e.getMessage()));
        }
        return list;
    }

    public void write(CreatureSpawner spawner, List<Entry> entries) {
        List<String> unreadable = List.of();
        if (entries instanceof StoredEntries stored) {
            if (stored.locked) return;
            unreadable = stored.unreadable;
        }
        if (entries.isEmpty() && unreadable.isEmpty()) {
            spawner.getPersistentDataContainer().remove(itemsKey);
            readCache.remove(cacheKey(spawner));
            return;
        }
        try {
            List<String> parts = new ArrayList<>();
            for (Entry entry : entries) {
                parts.add(encode(entry));
            }
            parts.addAll(unreadable);
            String raw = FORMAT_PREFIX + String.join(";", parts);
            spawner.getPersistentDataContainer().set(itemsKey, PersistentDataType.STRING, raw);
            if (unreadable.isEmpty()) {
                remember(cacheKey(spawner), raw, entries);
            } else {
                readCache.remove(cacheKey(spawner));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save spawner storage data: " + e.getMessage());
        }
    }

    private String cacheKey(CreatureSpawner spawner) {
        return spawner.getWorld().getName() + "," + spawner.getX() + "," + spawner.getY() + "," + spawner.getZ();
    }

    private void remember(String key, String raw, List<Entry> entries) {
        if (readCache.size() >= READ_CACHE_LIMIT) {
            readCache.clear();
        }
        List<Entry> copy = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            copy.add(new Entry(entry.template, entry.amount));
        }
        readCache.put(key, new CachedRead(raw, copy));
    }

    public int usedSlots(List<Entry> entries) {
        long used = 0;
        for (Entry entry : entries) {
            used += entry.slots();
        }
        return (int) Math.min(Integer.MAX_VALUE, used);
    }

    public boolean isFull(List<Entry> entries, int max) {
        int used = usedSlots(entries);
        if (used > max) return true;
        if (used < max) return false;
        for (Entry entry : entries) {
            if (entry.slots() * entry.stackSize() - entry.amount() > 0) {
                return false;
            }
        }
        return true;
    }

    public int add(List<Entry> entries, ItemStack drop, int maxSlots) {
        if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) return 0;

        int incoming = drop.getAmount();
        Entry target = null;
        for (Entry entry : entries) {
            if (entry.template().isSimilar(drop)) {
                target = entry;
                break;
            }
        }

        int stackSize = Math.max(1, drop.getMaxStackSize());
        long freeSlots = Math.max(0, (long) maxSlots - usedSlots(entries));
        long room = freeSlots * stackSize;
        if (target != null) {
            room += target.slots() * target.stackSize() - target.amount();
        }

        long stored = Math.min(incoming, room);
        if (stored > 0) {
            if (target == null) {
                entries.add(new Entry(drop, stored));
            } else {
                target.amount += stored;
            }
        }
        return (int) (incoming - stored);
    }

    public long remove(List<Entry> entries, ItemStack template, long amount) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.template().isSimilar(template)) continue;

            long removed = Math.min(amount, entry.amount);
            entry.amount -= removed;
            if (entry.amount <= 0) {
                entries.remove(i);
            }
            return removed;
        }
        return 0L;
    }

    public List<ItemStack> expand(List<Entry> entries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Entry entry : entries) {
            long remaining = entry.amount();
            int size = entry.stackSize();
            while (remaining > 0) {
                int part = (int) Math.min(remaining, size);
                ItemStack stack = entry.template().clone();
                stack.setAmount(part);
                stacks.add(stack);
                remaining -= part;
            }
        }
        return stacks;
    }

    public Snapshot snapshotEntries(List<Entry> entries, int maxSlots) {
        return new Snapshot(expand(entries), usedSlots(entries), maxSlots);
    }

    public Snapshot snapshot(CreatureSpawner spawner) {
        return snapshotEntries(read(spawner), maxSlots(spawner));
    }

    public int storedXp(CreatureSpawner spawner) {
        return spawner.getPersistentDataContainer().getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
    }

    public void dropContents(CreatureSpawner spawner, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location dropLocation = location.clone().add(0.5, 0.5, 0.5);
        if (enabled()) {
            for (ItemStack stack : expand(read(spawner))) {
                world.dropItemNaturally(dropLocation, stack);
            }
        }

        int xp = plugin.settings().xpVacuumEnabled ? storedXp(spawner) : 0;
        if (xp > 0) {
            world.spawn(dropLocation, ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
    }

    public boolean wipeDisabled(CreatureSpawner spawner) {
        boolean changed = false;
        if (plugin.settings().wipeStoredItems && spawner.getPersistentDataContainer().has(itemsKey, PersistentDataType.STRING)) {
            spawner.getPersistentDataContainer().remove(itemsKey);
            changed = true;
        }
        if (plugin.settings().wipeStoredXp
                && spawner.getPersistentDataContainer().has(xpKey, PersistentDataType.INTEGER)) {
            spawner.getPersistentDataContainer().remove(xpKey);
            changed = true;
        }
        return changed;
    }
}

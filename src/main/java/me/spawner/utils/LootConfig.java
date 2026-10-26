package me.spawner.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public class LootConfig {
    public record LootItem(Material material, int minAmount, int maxAmount, double chance,
                            Integer minDurability, Integer maxDurability, PotionType potionType) {
        public ItemStack createItemStack(Random random) {
            ItemStack item = new ItemStack(material, 1);

            if (minDurability != null && maxDurability != null) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable dmg) {
                    int durability = random.nextInt(maxDurability - minDurability + 1) + minDurability;
                    dmg.setDamage(durability);
                    item.setItemMeta(meta);
                }
            }

            if (material == Material.TIPPED_ARROW && potionType != null) {
                ItemMeta rawMeta = item.getItemMeta();
                if (rawMeta instanceof PotionMeta meta) {
                    Compat.applyBasePotion(meta, potionType);
                    item.setItemMeta(meta);
                }
            }

            return item;
        }

        public int generateAmount(Random random) {
            if (maxAmount <= minAmount) return minAmount;
            return random.nextInt(maxAmount - minAmount + 1) + minAmount;
        }
    }

    public record EntityLoot(int experience, List<LootItem> items) {}

    private final JavaPlugin plugin;
    private final String fileName;
    private final Function<String, String> keyResolver;
    private volatile Map<String, EntityLoot> lootMap = Map.of();
    private final Random random = new Random();

    public LootConfig(JavaPlugin plugin, String fileName, Function<String, String> keyResolver) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.keyResolver = keyResolver;
        load();
    }

    public static LootConfig forMobs(JavaPlugin plugin) {
        return new LootConfig(plugin, "loot.yml", key -> {
            EntityType type = Compat.entityType(key);
            return type != null ? type.name() : null;
        });
    }

    public static LootConfig forItems(JavaPlugin plugin) {
        return new LootConfig(plugin, "spawner_items.yml", key -> {
            Material material = Material.getMaterial(key.toUpperCase(Locale.ROOT));
            return material != null ? material.name() : null;
        });
    }

    public void load() {
        Map<String, EntityLoot> built = new HashMap<>();

        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            String resolvedKey = keyResolver.apply(key);
            if (resolvedKey == null) continue;

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            int exp = section.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = section.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    Material material = Material.getMaterial(itemKey.toUpperCase(Locale.ROOT));
                    if (material == null) continue;

                    int[] amountRange = parseRange(itemSection.getString("amount", "1-1"));
                    double chance = itemSection.getDouble("chance", 100.0);

                    Integer minDur = null, maxDur = null;
                    if (itemSection.contains("durability")) {
                        int[] durRange = parseRange(itemSection.getString("durability"));
                        minDur = durRange[0];
                        maxDur = durRange[1];
                    }

                    PotionType potionType = null;
                    if (itemSection.contains("potion_type")) {
                        potionType = Compat.potionType(itemSection.getString("potion_type"));
                    }

                    items.add(new LootItem(material, amountRange[0], amountRange[1], chance, minDur, maxDur, potionType));
                }
            }

            built.put(resolvedKey, new EntityLoot(exp, items));
        }
        lootMap = Map.copyOf(built);
    }

    private int[] parseRange(String raw) {
        if (raw == null) return new int[]{1, 1};
        try {
            String[] parts = raw.split("-");
            int a = Integer.parseInt(parts[0].trim());
            int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return new int[]{1, 1};
        }
    }

    public boolean hasConfig(String key) {
        return key != null && lootMap.get(key) != null;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(lootMap.keySet());
    }

    public int getExperience(String key) {
        EntityLoot loot = lootMap.get(key);
        return loot != null ? loot.experience() : 0;
    }

    public List<ItemStack> rollDrops(String key) {
        EntityLoot loot = lootMap.get(key);
        if (loot == null || loot.items().isEmpty()) return Collections.emptyList();

        List<ItemStack> drops = new ArrayList<>();
        for (LootItem lootItem : loot.items()) {
            if (random.nextDouble() * 100.0 >= lootItem.chance()) continue;

            int amount = lootItem.generateAmount(random);
            if (amount <= 0) continue;

            ItemStack stack = lootItem.createItemStack(random);
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            drops.add(stack);

            int remaining = amount - stack.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extra = stack.clone();
                extra.setAmount(Math.min(remaining, stack.getMaxStackSize()));
                drops.add(extra);
                remaining -= extra.getAmount();
            }
        }
        return drops;
    }

    public void reload() {
        load();
    }
}

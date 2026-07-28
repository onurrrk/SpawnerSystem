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
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;

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
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    meta.setBasePotionData(new PotionData(potionType, false, false));
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
    private final Map<EntityType, EntityLoot> lootMap = new EnumMap<>(EntityType.class);
    private final Random random = new Random();

    public LootConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        lootMap.clear();

        File file = new File(plugin.getDataFolder(), "loot.yml");
        if (!file.exists()) {
            plugin.saveResource("loot.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[LootConfig] Unknown entity type in loot.yml: " + key);
                continue;
            }

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            int exp = section.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = section.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    Material material;
                    try {
                        material = Material.valueOf(itemKey.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[LootConfig] Unknown material in loot.yml: " + itemKey + " (" + key + ")");
                        continue;
                    }

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
                        try {
                            potionType = PotionType.valueOf(itemSection.getString("potion_type").toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    items.add(new LootItem(material, amountRange[0], amountRange[1], chance, minDur, maxDur, potionType));
                }
            }

            lootMap.put(type, new EntityLoot(exp, items));
        }
    }

    private int[] parseRange(String raw) {
        if (raw == null) return new int[]{1, 1};
        String[] parts = raw.split("-");
        if (parts.length == 1) {
            int v = Integer.parseInt(parts[0].trim());
            return new int[]{v, v};
        }
        int a = Integer.parseInt(parts[0].trim());
        int b = Integer.parseInt(parts[1].trim());
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    public boolean hasConfig(EntityType type) {
        return lootMap.get(type) != null;
    }

    public int getExperience(EntityType type) {
        EntityLoot loot = lootMap.get(type);
        return loot != null ? loot.experience() : 0;
    }

    public List<ItemStack> rollDrops(EntityType type) {
        EntityLoot loot = lootMap.get(type);
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

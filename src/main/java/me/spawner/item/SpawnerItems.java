package me.spawner.item;

import com.google.common.collect.ArrayListMultimap;
import me.spawner.Spawner;
import me.spawner.utils.ColorUtils;
import me.spawner.utils.Compat;
import me.spawner.utils.PDCUtils;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SpawnerItems {
    public static final String ITEM_PREFIX = "ITEM:";
    private static final List<String> CARRIERS = List.of("MARKER", "ARMOR_STAND");
    private static final List<String> ITEM_DISPLAY_TYPES = List.of("ITEM", "DROPPED_ITEM");
    private static final String[] ADMIN_LOG_RULES = {"logAdminCommands", "log_admin_commands", "sendCommandFeedback", "send_command_feedback"};
    private static volatile boolean commandFallbackBroken = false;

    private final Spawner plugin;

    public SpawnerItems(Spawner plugin) {
        this.plugin = plugin;
    }

    public ItemStack createSpawnerItem(EntityType type) {
        return buildSpawner(plugin.language().entityName(type), type.name(), type, null);
    }

    public ItemStack createItemSpawner(String material) {
        return buildSpawner(plugin.language().itemName(material), ITEM_PREFIX + material, null, material);
    }

    public String itemSpawnerType(CreatureSpawner spawner) {
        return spawner.getPersistentDataContainer().get(plugin.keys().itemSpawner, PersistentDataType.STRING);
    }

    public boolean ensureItemSpawnerType(CreatureSpawner spawner) {
        String material = itemSpawnerType(spawner);
        if (material == null) {
            return false;
        }

        EntityType current = spawner.getSpawnedType();
        if (current != null && ITEM_DISPLAY_TYPES.contains(current.name())) {
            return false;
        }

        if (setSpawnedItemViaApi(spawner, material)) {
            return true;
        }

        boolean changed = false;
        if (current == null || !CARRIERS.contains(current.name())) {
            changed = setCarrierType(spawner);
        }
        scheduleDataMerge(spawner.getLocation(), material);
        return changed;
    }

    public boolean setCarrierType(CreatureSpawner spawner) {
        for (String name : CARRIERS) {
            EntityType carrier = Compat.entityType(name);
            if (carrier == null) continue;
            try {
                spawner.setSpawnedType(carrier);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private String itemEntitySnbt(String material) {
        String id = material.toLowerCase(Locale.ROOT);
        return "{id:\"minecraft:item\",Item:{id:\"minecraft:" + id + "\",Count:1b,count:1}}";
    }

    private boolean setSpawnedItemViaApi(CreatureSpawner spawner, String material) {
        try {
            Object factory = Bukkit.class.getMethod("getEntityFactory").invoke(null);

            Method create = null;
            for (Method method : factory.getClass().getMethods()) {
                if (method.getName().equals("createEntitySnapshot")
                        && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                    create = method;
                    break;
                }
            }
            if (create == null) return false;
            Object snapshot = create.invoke(factory, itemEntitySnbt(material));

            for (Method method : CreatureSpawner.class.getMethods()) {
                if (method.getName().equals("setSpawnedEntity") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].getSimpleName().equals("EntitySnapshot")) {
                    method.invoke(spawner, snapshot);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void scheduleDataMerge(Location location, String material) {
        if (Scheduler.isFolia() || commandFallbackBroken || location.getWorld() == null) return;

        String dimension;
        switch (location.getWorld().getEnvironment()) {
            case NORMAL -> dimension = "minecraft:overworld";
            case NETHER -> dimension = "minecraft:the_nether";
            case THE_END -> dimension = "minecraft:the_end";
            default -> {
                return;
            }
        }

        Scheduler.runLocationTaskLater(plugin, location, () -> {
            if (location.getBlock().getType() != Material.SPAWNER) return;
            if (location.getBlock().getState() instanceof CreatureSpawner current) {
                EntityType type = current.getSpawnedType();
                if (type != null && ITEM_DISPLAY_TYPES.contains(type.name())) return;
            }

            String command = "execute in " + dimension + " run data merge block "
                    + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ()
                    + " {SpawnPotentials:[],SpawnData:{entity:" + itemEntitySnbt(material) + "}}";
            Map<World, Map<String, String>> saved = silenceAdminLog();
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    commandFallbackBroken = true;
                }
            } catch (Throwable t) {
                commandFallbackBroken = true;
            } finally {
                restoreAdminLog(saved);
            }
        }, 2L);
    }

    @SuppressWarnings("deprecation")
    private Map<World, Map<String, String>> silenceAdminLog() {
        Map<World, Map<String, String>> saved = new java.util.HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (String rule : ADMIN_LOG_RULES) {
                try {
                    String value = world.getGameRuleValue(rule);
                    if (value != null) {
                        saved.computeIfAbsent(world, w -> new java.util.HashMap<>()).put(rule, value);
                        world.setGameRuleValue(rule, "false");
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return saved;
    }

    @SuppressWarnings("deprecation")
    private void restoreAdminLog(Map<World, Map<String, String>> saved) {
        for (Map.Entry<World, Map<String, String>> worldEntry : saved.entrySet()) {
            for (Map.Entry<String, String> rule : worldEntry.getValue().entrySet()) {
                try {
                    worldEntry.getKey().setGameRuleValue(rule.getKey(), rule.getValue());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public ItemStack createEmptySpawner() {
        return buildSpawner(plugin.language().entityName(null), "EMPTY", null, null);
    }

    private void applySpawnData(ItemMeta meta, EntityType type, String itemMaterial) {
        if (type == null && itemMaterial == null) return;
        if (!(meta instanceof BlockStateMeta stateMeta)) return;
        try {
            if (!(stateMeta.getBlockState() instanceof CreatureSpawner state)) return;
            if (itemMaterial != null) {
                if (!setSpawnedItemViaApi(state, itemMaterial) && !setCarrierType(state)) return;
            } else {
                state.setSpawnedType(type);
            }
            stateMeta.setBlockState(state);
        } catch (Throwable ignored) {
        }
    }

    private ItemStack buildSpawner(String translatedTypeName, String typeId, EntityType type, String itemMaterial) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        String name = plugin.language().config().getString("spawner-item.name", "&a%type% Spawner")
                .replace("%type%", translatedTypeName);
        meta.setDisplayName(ColorUtils.color(name));

        String modeTag = plugin.language().config().getString("mode-tags." + plugin.settings().systemMode, "");
        List<String> lore = plugin.language().list("spawner-item.lore").stream()
                .map(line -> line.replace("%type%", translatedTypeName).replace("%mode%", modeTag))
                .collect(Collectors.toList());
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(plugin.keys().spawnerType, PersistentDataType.STRING, typeId);
        applySpawnData(meta, type, itemMaterial);
        addHideAdditionalTooltip(meta);

        spawner.setItemMeta(meta);
        return hideBlockEntityTooltip(spawner);
    }

    private static final boolean TOOLTIP_DISPLAY_SUPPORTED = detectTooltipDisplay();

    private volatile Object cachedComponent;
    private volatile java.util.Set<Object> cachedHidden;

    private static boolean detectTooltipDisplay() {
        String[] parts = Bukkit.getBukkitVersion().split("[.-]");
        try {
            int first = Integer.parseInt(parts[0]);
            if (first > 1) return true;
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 && parts[2].matches("\\d+") ? Integer.parseInt(parts[2]) : 0;
            return minor > 21 || (minor == 21 && patch >= 5);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ItemStack nativeCopy(ItemStack item) {
        try {
            Object bytes = ItemStack.class.getMethod("serializeAsBytes").invoke(item);
            Object copy = ItemStack.class.getMethod("deserializeBytes", byte[].class).invoke(null, bytes);
            if (copy instanceof ItemStack result && result.getType() == item.getType()) {
                return result;
            }
        } catch (Throwable ignored) {
        }
        return item;
    }

    public ItemStack hideBlockEntityTooltip(ItemStack item) {
        if (!TOOLTIP_DISPLAY_SUPPORTED) return item;
        if (hideViaPaperApi(item)) return item;
        return hideViaUnsafe(item);
    }

    private Object findBlockEntityDataComponent() throws Exception {
        Object cached = cachedComponent;
        if (cached != null) return cached;

        Class<?> accessClass = Class.forName("io.papermc.paper.registry.RegistryAccess");
        Class<?> keyClass = Class.forName("io.papermc.paper.registry.RegistryKey");
        Object access = accessClass.getMethod("registryAccess").invoke(null);
        Object registryKey = keyClass.getField("DATA_COMPONENT_TYPE").get(null);
        Object registry = accessClass.getMethod("getRegistry", keyClass).invoke(access, registryKey);
        Class<?> registryClass = Class.forName("org.bukkit.Registry");

        List<Object> candidates = new ArrayList<>();
        try {
            Class<?> keys = Class.forName("io.papermc.paper.registry.keys.DataComponentTypeKeys");
            candidates.add(keys.getField("BLOCK_ENTITY_DATA").get(null));
        } catch (Throwable ignored) {
        }
        try {
            candidates.add(Class.forName("net.kyori.adventure.key.Key")
                    .getMethod("key", String.class, String.class).invoke(null, "minecraft", "block_entity_data"));
        } catch (Throwable ignored) {
        }
        candidates.add(org.bukkit.NamespacedKey.minecraft("block_entity_data"));

        for (Object candidate : candidates) {
            for (Method method : registryClass.getMethods()) {
                if (!method.getName().equals("get") || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isInstance(candidate)) continue;
                try {
                    Object found = method.invoke(registry, candidate);
                    if (found != null) {
                        cachedComponent = found;
                        return found;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private java.util.Set<Object> hiddenComponentSet() throws Exception {
        java.util.Set<Object> cached = cachedHidden;
        if (cached != null) return cached;

        Class<?> accessClass = Class.forName("io.papermc.paper.registry.RegistryAccess");
        Class<?> keyClass = Class.forName("io.papermc.paper.registry.RegistryKey");
        Object access = accessClass.getMethod("registryAccess").invoke(null);
        Object registryKey = keyClass.getField("DATA_COMPONENT_TYPE").get(null);
        Object registry = accessClass.getMethod("getRegistry", keyClass).invoke(access, registryKey);

        java.util.Set<Object> hidden = new java.util.LinkedHashSet<>();
        if (registry instanceof Iterable<?> iterable) {
            java.util.Map<String, Object> sorted = new java.util.TreeMap<>();
            for (Object type : iterable) {
                if (!(type instanceof org.bukkit.Keyed keyed)) continue;
                String name = keyed.getKey().getKey();
                if (name.equals("lore") || name.equals("custom_name") || name.equals("item_name")) continue;
                sorted.put(name, type);
            }
            hidden.addAll(sorted.values());
        }
        if (hidden.isEmpty()) {
            Object single = findBlockEntityDataComponent();
            if (single != null) {
                hidden.add(single);
            }
        }
        if (hidden.isEmpty()) return null;
        cachedHidden = hidden;
        return hidden;
    }

    private boolean hideViaPaperApi(ItemStack item) {
        try {
            java.util.Set<Object> hiddenSet = hiddenComponentSet();
            if (hiddenSet == null) return false;

            Class<?> typesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object tooltipDisplayType = typesClass.getField("TOOLTIP_DISPLAY").get(null);

            Class<?> displayClass = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay");
            Class<?> builderClass = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay$Builder");
            Object builder = displayClass.getMethod("tooltipDisplay").invoke(null);
            builderClass.getMethod("hiddenComponents", java.util.Set.class).invoke(builder, hiddenSet);
            Object display = builderClass.getMethod("build").invoke(builder);

            for (Method method : ItemStack.class.getMethods()) {
                if (method.getName().equals("setData") && method.getParameterCount() == 2
                        && method.getParameterTypes()[0].getSimpleName().equals("Valued")
                        && method.getParameterTypes()[1].isInstance(display)) {
                    method.invoke(item, tooltipDisplayType, display);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static final String[] COMPONENT_IDS = {
            "custom_data", "max_stack_size", "max_damage", "damage", "unbreakable", "item_model", "rarity",
            "enchantments", "can_place_on", "can_break", "attribute_modifiers", "custom_model_data", "repair_cost",
            "creative_slot_lock", "enchantment_glint_override", "intangible_projectile", "food", "consumable",
            "use_remainder", "use_cooldown", "damage_resistant", "tool", "weapon", "enchantable", "equippable",
            "repairable", "glider", "tooltip_style", "death_protection", "blocks_attacks", "stored_enchantments",
            "dyed_color", "map_color", "map_id", "map_decorations", "map_post_processing", "charged_projectiles",
            "bundle_contents", "potion_contents", "potion_duration_scale", "suspicious_stew_effects",
            "writable_book_content", "written_book_content", "trim", "debug_stick_state", "entity_data",
            "bucket_entity_data", "block_entity_data", "instrument", "provides_trim_material",
            "ominous_bottle_amplifier", "jukebox_playable", "provides_banner_patterns", "recipes",
            "lodestone_tracker", "firework_explosion", "fireworks", "profile", "note_block_sound", "banner_patterns",
            "base_color", "pot_decorations", "container", "block_state", "bees", "lock", "container_loot",
            "break_sound", "villager_variant", "wolf_variant", "wolf_sound_variant", "wolf_collar", "fox_variant",
            "salmon_size", "parrot_variant", "tropical_fish_pattern", "tropical_fish_base_color",
            "tropical_fish_pattern_color", "mooshroom_variant", "rabbit_variant", "pig_variant", "cow_variant",
            "chicken_variant", "frog_variant", "horse_variant", "painting_variant", "llama_variant",
            "axolotl_variant", "cat_variant", "cat_collar", "sheep_color", "shulker_color", "kinetic_weapon",
            "piercing_weapon", "attack_range", "swing_animation", "use_effects", "dye", "additional_trade_cost",
            "zombie_nautilus_variant"
    };

    private volatile List<String> unsafeHiddenIds;

    @SuppressWarnings("deprecation")
    private ItemStack modifyComponents(ItemStack item, String component) {
        for (String arguments : new String[]{"[" + component + "]", component}) {
            try {
                ItemStack result = Bukkit.getUnsafe().modifyItemStack(item, arguments);
                if (result != null && result.getType() == Material.SPAWNER) {
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String hiddenComponentsArgument(List<String> ids) {
        return "tooltip_display={hidden_components:["
                + ids.stream().map(id -> "\"minecraft:" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }

    private List<String> unsafeHiddenIds() {
        List<String> cached = unsafeHiddenIds;
        if (cached != null) return cached;
        List<String> valid = new ArrayList<>();
        ItemStack probe = new ItemStack(Material.SPAWNER);
        for (String id : COMPONENT_IDS) {
            if (modifyComponents(probe, hiddenComponentsArgument(List.of(id))) != null) {
                valid.add(id);
            }
        }
        unsafeHiddenIds = valid;
        return valid;
    }

    private ItemStack hideViaUnsafe(ItemStack item) {
        List<String> ids = unsafeHiddenIds();
        if (ids.isEmpty()) return item;
        ItemStack result = modifyComponents(item, hiddenComponentsArgument(ids));
        return result != null ? result : item;
    }

    private String typeIdOf(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.keys().spawnerType, PersistentDataType.STRING);
    }

    private boolean sameSpawner(ItemStack item, ItemStack other) {
        if (item == null || other == null) return false;
        if (item.getType() != Material.SPAWNER || other.getType() != Material.SPAWNER) return false;
        String first = typeIdOf(item);
        String second = typeIdOf(other);
        if (first != null && second != null) return first.equals(second);
        if (first != null || second != null) return false;
        if (item.isSimilar(other)) return true;
        ItemMeta firstMeta = item.getItemMeta();
        ItemMeta secondMeta = other.getItemMeta();
        return firstMeta != null && secondMeta != null
                && Objects.equals(firstMeta.getDisplayName(), secondMeta.getDisplayName())
                && Objects.equals(firstMeta.getLore(), secondMeta.getLore());
    }

    public boolean isPluginSpawner(ItemStack item) {
        return typeIdOf(item) != null;
    }

    private ItemStack canonicalTemplate(String typeId) {
        if (typeId.equals("EMPTY")) {
            return createEmptySpawner();
        }
        if (typeId.startsWith(ITEM_PREFIX)) {
            return createItemSpawner(typeId.substring(ITEM_PREFIX.length()));
        }
        EntityType type = Compat.entityType(typeId);
        return type == null ? null : createSpawnerItem(type);
    }

    private boolean sameComponents(ItemStack first, ItemStack second) {
        try {
            byte[] firstBytes = (byte[]) ItemStack.class.getMethod("serializeAsBytes").invoke(first);
            byte[] secondBytes = (byte[]) ItemStack.class.getMethod("serializeAsBytes").invoke(second);
            return Arrays.equals(firstBytes, secondBytes);
        } catch (Throwable t) {
            return first.isSimilar(second) && first.getAmount() == second.getAmount();
        }
    }

    public void normalize(Inventory inventory) {
        Map<String, ItemStack> templates = new HashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != Material.SPAWNER) continue;
            String typeId = typeIdOf(stack);
            if (typeId == null) continue;

            ItemStack template = templates.get(typeId);
            if (template == null && !templates.containsKey(typeId)) {
                ItemStack built = canonicalTemplate(typeId);
                template = built == null ? null : nativeCopy(built);
                templates.put(typeId, template);
            }
            if (template == null) continue;

            ItemStack canonical = template.clone();
            canonical.setAmount(stack.getAmount());
            if (!sameComponents(stack, canonical)) {
                inventory.setItem(slot, canonical);
            }
        }
    }

    private Map<Integer, ItemStack> addSpawner(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        int amount = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (int slot = 0; slot < 36 && amount > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!sameSpawner(item, existing)) continue;
            int room = max - existing.getAmount();
            if (room <= 0) continue;
            int moved = Math.min(room, amount);
            existing.setAmount(existing.getAmount() + moved);
            inventory.setItem(slot, existing);
            amount -= moved;
        }
        if (amount <= 0) return new HashMap<>();
        ItemStack rest = item.clone();
        rest.setAmount(amount);
        return inventory.addItem(rest);
    }

    public ItemStack createSpawnerPickaxe(int uses) {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        meta.setDisplayName(ColorUtils.color(plugin.language().config().getString("spawner-pickaxe-item.name")));
        meta.setLore(plugin.language().list("spawner-pickaxe-item.lore").stream()
                .map(line -> line.replace("%uses%", String.valueOf(uses)))
                .collect(Collectors.toList()));

        PDCUtils.setBoolean(meta.getPersistentDataContainer(), plugin.keys().spawnerPickaxe, true);
        meta.getPersistentDataContainer().set(plugin.keys().usesLeft, PersistentDataType.INTEGER, uses);
        meta.setAttributeModifiers(ArrayListMultimap.create());
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }

        Enchantment efficiency = Compat.enchantment("efficiency");
        if (efficiency != null) {
            meta.addEnchant(efficiency, 5, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        addHideAdditionalTooltip(meta);

        pickaxe.setItemMeta(meta);
        return pickaxe;
    }

    private void addHideAdditionalTooltip(ItemMeta meta) {
        for (String flagName : new String[]{"HIDE_ADDITIONAL_TOOLTIP", "HIDE_POTION_EFFECTS"}) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName));
            } catch (Exception ignored) {
            }
        }
    }

    public boolean hasSilkTouch(ItemStack item) {
        Enchantment silk = Compat.enchantment("silk_touch");
        return silk != null && item != null && item.containsEnchantment(silk);
    }

    public boolean isSpawnerPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return PDCUtils.hasBoolean(item.getItemMeta().getPersistentDataContainer(), plugin.keys().spawnerPickaxe);
    }

    public boolean giveOrDrop(Player player, ItemStack item) {
        ItemStack toGive = item.getType() == Material.SPAWNER ? nativeCopy(hideBlockEntityTooltip(item)) : item;
        Map<Integer, ItemStack> leftovers = toGive.getType() == Material.SPAWNER
                ? addSpawner(player, toGive)
                : player.getInventory().addItem(toGive);
        if (leftovers.isEmpty()) {
            return true;
        }
        Scheduler.runEntityTask(plugin, player, () -> {
            leftovers.values().forEach(leftover -> {
                Item dropped = player.getWorld().dropItem(player.getEyeLocation(), leftover);
                dropped.setVelocity(player.getLocation().getDirection().multiply(0.3));
                dropped.setPickupDelay(40);
                dropped.setThrower(player.getUniqueId());
            });
            player.sendMessage(plugin.language().message("inventory-full"));
        });
        return false;
    }
}

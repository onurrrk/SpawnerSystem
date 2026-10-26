package me.spawner.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class SpawnerKeys {
    public final NamespacedKey spawnerPickaxe;
    public final NamespacedKey usesLeft;
    public final NamespacedKey playerPlaced;
    public final NamespacedKey spawnerType;
    public final NamespacedKey nerfedMob;
    public final NamespacedKey itemSpawner;
    public final NamespacedKey boostList;
    public final NamespacedKey boostRange;
    public final NamespacedKey boostDelay;
    public final NamespacedKey boostXp;
    public final NamespacedKey boostMinutes;
    public final NamespacedKey boostExpires;
    public final NamespacedKey mobXpBoost;
    public final NamespacedKey spawnerOwner;
    public final NamespacedKey spawnerBorn;
    public final NamespacedKey autoKill;

    public SpawnerKeys(Plugin plugin) {
        this.spawnerPickaxe = new NamespacedKey(plugin, "spawner_pickaxe");
        this.usesLeft = new NamespacedKey(plugin, "uses_left");
        this.playerPlaced = new NamespacedKey(plugin, "player_placed_spawner");
        this.spawnerType = new NamespacedKey(plugin, "spawner_type");
        this.nerfedMob = new NamespacedKey(plugin, "nerfed_mob");
        this.itemSpawner = new NamespacedKey(plugin, "item_spawner");
        this.boostList = new NamespacedKey(plugin, "boost_list");
        this.boostRange = new NamespacedKey(plugin, "boost_range");
        this.boostDelay = new NamespacedKey(plugin, "boost_delay");
        this.boostXp = new NamespacedKey(plugin, "boost_xp");
        this.boostMinutes = new NamespacedKey(plugin, "boost_minutes");
        this.boostExpires = new NamespacedKey(plugin, "boost_expires");
        this.mobXpBoost = new NamespacedKey(plugin, "mob_xp_boost");
        this.spawnerOwner = new NamespacedKey(plugin, "spawner_owner");
        this.spawnerBorn = new NamespacedKey(plugin, "spawner_born");
        this.autoKill = new NamespacedKey(plugin, "auto_kill");
    }
}

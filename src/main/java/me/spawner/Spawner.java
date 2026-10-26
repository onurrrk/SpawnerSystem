package me.spawner;

import me.spawner.booster.BoosterManager;
import me.spawner.command.SpawnerCommand;
import me.spawner.config.LanguageManager;
import me.spawner.config.PluginSettings;
import me.spawner.culling.CullingManager;
import me.spawner.discord.WebhookManager;
import me.spawner.hologram.HologramManager;
import me.spawner.item.SpawnerItems;
import me.spawner.item.SpawnerKeys;
import me.spawner.listener.BoosterListener;
import me.spawner.listener.ChunkListener;
import me.spawner.listener.ExplosionListener;
import me.spawner.listener.MobLimitListener;
import me.spawner.listener.MobNerfListener;
import me.spawner.listener.SpawnerBreakListener;
import me.spawner.listener.SpawnerItemListener;
import me.spawner.listener.SpawnerPlaceListener;
import me.spawner.listener.SpawnerSpawnListener;
import me.spawner.booster.BoosterSelectMenu;
import me.spawner.menu.MenuLocks;
import me.spawner.menu.SpawnerMenu;
import me.spawner.spawner.ChunkMobCounter;
import me.spawner.spawner.SpawnerSettingsApplier;
import me.spawner.spawner.SpawnerTicker;
import me.spawner.stack.SpawnerStacks;
import me.spawner.storage.SpawnerStorage;
import me.spawner.storage.StorageMenu;
import me.spawner.utils.ConfigUpdater;
import me.spawner.utils.JsonLogger;
import me.spawner.utils.Scheduler;
import me.spawner.utils.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Spawner extends JavaPlugin {
    private static final int BSTATS_ID = 26914;

    private PluginSettings settings;
    private LanguageManager language;
    private SpawnerKeys keys;
    private SpawnerItems items;
    private ChunkMobCounter mobCounter;
    private HologramManager holograms;
    private BoosterManager boosters;
    private SpawnerSettingsApplier spawnerSettings;
    private SpawnerTicker ticker;
    private CullingManager culling;
    private SpawnerMenu spawnerMenu;
    private MenuLocks menuLocks;
    private SpawnerStacks stacks;
    private BoosterSelectMenu boosterSelect;
    private SpawnerStorage storage;
    private StorageMenu storageMenu;
    private JsonLogger jsonLogger;
    private WebhookManager webhookManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        saveDefaultConfig();
        ConfigUpdater.markCurrentVersion(this);
        if (getConfig().getBoolean("bstats-enabled", true)) {
            new Metrics(this, BSTATS_ID);
        }
        if (getConfig().getBoolean("update-checker", true)) {
            updateChecker = new UpdateChecker(this);
        }

        settings = new PluginSettings();
        language = new LanguageManager(this);
        language.reload();
        settings.load(this);

        keys = new SpawnerKeys(this);
        items = new SpawnerItems(this);
        mobCounter = new ChunkMobCounter();
        holograms = new HologramManager(this);
        boosters = new BoosterManager(this);
        spawnerSettings = new SpawnerSettingsApplier(this);
        ticker = new SpawnerTicker(this);
        culling = new CullingManager(this);
        menuLocks = new MenuLocks();
        stacks = new SpawnerStacks(this);
        boosterSelect = new BoosterSelectMenu(this);
        storage = new SpawnerStorage(this);
        storageMenu = new StorageMenu(this);
        spawnerMenu = new SpawnerMenu(this);
        jsonLogger = new JsonLogger(this);
        webhookManager = new WebhookManager(this);

        registerListeners();
        registerCommand();

        holograms.start();
        ticker.start();
        spawnerSettings.loadAllLoadedChunks();
        culling.start();
    }

    @Override
    public void onDisable() {
        try {
            stopTasks();
        } catch (Throwable ignored) {
        }
        try {
            if (updateChecker != null) updateChecker.shutdown();
        } catch (Throwable ignored) {
        }
        try {
            if (jsonLogger != null) jsonLogger.close();
        } catch (Throwable ignored) {
        }
        try {
            if (webhookManager != null) webhookManager.shutdown();
        } catch (Throwable ignored) {
        }
    }

    public boolean reloadPlugin() {
        try {
            stopTasks();
            saveDefaultConfig();
            ConfigUpdater.update(this);
            ConfigUpdater.markCurrentVersion(this);
            reloadConfig();
            language.reload();
            settings.load(this);

            holograms.start();
            ticker.start();
            webhookManager.loadConfig();

            jsonLogger.close();
            jsonLogger = new JsonLogger(this);
            spawnerMenu.reload();

            spawnerSettings.loadAllLoadedChunks();
            culling.start();
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Reload failed", t);
            recoverTasks();
            return false;
        }
    }

    private void recoverTasks() {
        try {
            stopTasks();
            holograms.start();
            ticker.start();
            spawnerSettings.loadAllLoadedChunks();
            culling.start();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Could not restart tasks after the failed reload", t);
        }
    }

    private void stopTasks() {
        Scheduler.cancelAll(this);
        if (culling != null) culling.stop();
        if (holograms != null) holograms.stop();
        if (ticker != null) ticker.stop();
        if (mobCounter != null) mobCounter.clear();
        if (boosters != null) boosters.stop();
    }

    private void registerListeners() {
        Listener[] listeners = {
                spawnerMenu,
                storageMenu,
                boosterSelect,
                new ChunkListener(this),
                new SpawnerPlaceListener(this),
                new SpawnerBreakListener(this),
                new SpawnerSpawnListener(this),
                new MobNerfListener(this),
                new MobLimitListener(this),
                new BoosterListener(this),
                new SpawnerItemListener(this),
                new ExplosionListener()
        };
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("spsystem");
        if (command == null) return;
        SpawnerCommand executor = new SpawnerCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public FileConfiguration getLanguageConfig() {
        return language != null ? language.config() : null;
    }

    public PluginSettings settings() {
        return settings;
    }

    public LanguageManager language() {
        return language;
    }

    public SpawnerKeys keys() {
        return keys;
    }

    public SpawnerItems items() {
        return items;
    }

    public ChunkMobCounter mobCounter() {
        return mobCounter;
    }

    public HologramManager holograms() {
        return holograms;
    }

    public SpawnerSettingsApplier spawnerSettings() {
        return spawnerSettings;
    }

    public SpawnerTicker ticker() {
        return ticker;
    }

    public CullingManager culling() {
        return culling;
    }

    public BoosterManager boosters() {
        return boosters;
    }

    public SpawnerMenu menu() {
        return spawnerMenu;
    }

    public SpawnerStacks stacks() {
        return stacks;
    }

    public BoosterSelectMenu boosterSelect() {
        return boosterSelect;
    }

    public MenuLocks menuLocks() {
        return menuLocks;
    }

    public SpawnerStorage storage() {
        return storage;
    }

    public StorageMenu storageMenu() {
        return storageMenu;
    }

    public JsonLogger jsonLogger() {
        return jsonLogger;
    }

    public WebhookManager webhooks() {
        return webhookManager;
    }
}

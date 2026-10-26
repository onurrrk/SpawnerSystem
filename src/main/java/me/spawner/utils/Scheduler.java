package me.spawner.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class Scheduler {
    private static final boolean IS_FOLIA;
    private static volatile int generation;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (Throwable e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private Scheduler() {
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void runLocationTask(Plugin plugin, Location loc, Runnable task) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            if (loc == null || loc.getWorld() == null) return;
            FoliaImpl.runLocation(plugin, loc, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            if (entity == null) return;
            FoliaImpl.runEntity(plugin, entity, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runLocationTaskLater(Plugin plugin, Location loc, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) return;
        if (IS_FOLIA) {
            if (loc == null || loc.getWorld() == null) return;
            FoliaImpl.runLocationLater(plugin, loc, task, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
        }
    }

    public static void runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) return;
        if (IS_FOLIA) {
            if (entity == null) return;
            FoliaImpl.runEntityLater(plugin, entity, task, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
        }
    }

    public static Object runLocationTaskTimer(Plugin plugin, Location loc, Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return null;
        if (IS_FOLIA) {
            if (loc == null || loc.getWorld() == null) return null;
            return FoliaImpl.runLocationTimer(plugin, loc, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static Object runEntityTaskTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return runEntityTaskTimer(plugin, entity, task, null, delayTicks, periodTicks);
    }

    public static Object runEntityTaskTimer(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return null;
        if (IS_FOLIA) {
            if (entity == null) return null;
            return FoliaImpl.runEntityTimer(plugin, entity, task, retired, Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static Object runAsyncTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return null;
        if (IS_FOLIA) {
            return FoliaImpl.runAsyncTimer(plugin, task, Math.max(1, delayTicks) * 50L, Math.max(1, periodTicks) * 50L);
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static void cancelTask(Object task) {
        if (task == null) return;
        if (IS_FOLIA) {
            FoliaImpl.cancel(task);
        } else if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        }
    }

    public static void cancelAll(Plugin plugin) {
        generation++;
        if (IS_FOLIA) {
            FoliaImpl.cancelAll(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private static final class FoliaImpl {
        static void runLocation(Plugin plugin, Location loc, Runnable task) {
            Bukkit.getRegionScheduler().execute(plugin, loc, task);
        }

        static void runEntity(Plugin plugin, Entity entity, Runnable task) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        }

        static void runLocationLater(Plugin plugin, Location loc, Runnable task, long delay) {
            final int stamp = generation;
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> {
                if (stamp == generation) task.run();
            }, delay);
        }

        static void runEntityLater(Plugin plugin, Entity entity, Runnable task, long delay) {
            final int stamp = generation;
            entity.getScheduler().runDelayed(plugin, t -> {
                if (stamp == generation) task.run();
            }, null, delay);
        }

        static Object runLocationTimer(Plugin plugin, Location loc, Runnable task, long delay, long period) {
            final int stamp = generation;
            return Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> {
                if (stamp != generation) {
                    t.cancel();
                    return;
                }
                task.run();
            }, delay, period);
        }

        static Object runEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delay, long period) {
            final int stamp = generation;
            return entity.getScheduler().runAtFixedRate(plugin, t -> {
                if (stamp != generation) {
                    t.cancel();
                    return;
                }
                task.run();
            }, retired, delay, period);
        }

        static Object runAsyncTimer(Plugin plugin, Runnable task, long delayMs, long periodMs) {
            return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delayMs, periodMs,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        static void cancel(Object task) {
            if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask) {
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
            }
        }

        static void cancelAll(Plugin plugin) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        }
    }
}

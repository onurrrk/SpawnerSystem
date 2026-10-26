package me.spawner.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class LuckPermsHook {
    private LuckPermsHook() {
    }

    public static void register(Plugin plugin, Consumer<UUID> onRecalculate) {
        try {
            LuckPermsProvider.get().getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                    event -> onRecalculate.accept(event.getUser().getUniqueId()));
        } catch (Throwable ignored) {
        }
    }

    public static boolean check(UUID uuid, String permission, Map<UUID, Boolean> cache) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(uuid);
            if (user != null) {
                boolean has = eval(user, permission);
                cache.put(uuid, has);
                return has;
            }
            api.getUserManager().loadUser(uuid).thenAccept(loaded -> {
                if (loaded != null) cache.put(uuid, eval(loaded, permission));
            });
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean eval(User user, String permission) {
        var data = user.getCachedData().getPermissionData(QueryOptions.nonContextual());
        return data.checkPermission(permission).asBoolean() || data.checkPermission("spawner.admin").asBoolean();
    }
}

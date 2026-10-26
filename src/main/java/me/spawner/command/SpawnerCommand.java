package me.spawner.command;

import me.spawner.Spawner;
import me.spawner.booster.BoosterManager;
import me.spawner.item.SpawnerItems;
import me.spawner.utils.Compat;
import me.spawner.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SpawnerCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "spawner.admin";
    private static final int MAX_GIVE_AMOUNT = 2304;
    private static final int MAX_BOOST_RANGE = 1000;
    private static final int MAX_BOOST_DELAY_SECONDS = 100000;
    private static final int MAX_BOOST_XP_PERCENT = 10000;
    private static final int MAX_BOOST_MINUTES = 10000000;

    private final Spawner plugin;

    public SpawnerCommand(Spawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(plugin.language().message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.language().message("wrong-subcommand"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                boolean reloaded = plugin.reloadPlugin();
                sender.sendMessage(plugin.language().message(reloaded ? "reload-success" : "reload-failed"));
                return true;
            }
            case "pickaxegive":
                return givePickaxe(sender, args);
            case "givespawner":
                return giveSpawner(sender, args);
            case "givebooster":
                return giveBooster(sender, args);
            default:
                sender.sendMessage(plugin.language().message("wrong-subcommand"));
                return true;
        }
    }

    private boolean givePickaxe(CommandSender sender, String[] args) {
        if (!"advanced".equals(plugin.settings().systemMode)) {
            sender.sendMessage(plugin.language().message("command-disabled"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().message("wrong-pickaxe-usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.language().message("player-not-found").replace("%player%", args[1]));
            return true;
        }

        int uses;
        try {
            uses = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.language().message("not-a-number"));
            return true;
        }
        if (uses <= 0 || uses > 1000) {
            sender.sendMessage(plugin.language().message("max-uses-limit"));
            return true;
        }

        ItemStack pickaxe = plugin.items().createSpawnerPickaxe(uses);
        Scheduler.runEntityTask(plugin, target, () -> plugin.items().giveOrDrop(target, pickaxe));
        sender.sendMessage(plugin.language().message("pickaxe-given-sender")
                .replace("%player%", target.getName())
                .replace("%uses%", String.valueOf(uses)));
        target.sendMessage(plugin.language().message("pickaxe-given-recipient"));
        return true;
    }

    private boolean giveSpawner(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendGiveUsage(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.language().message("player-not-found").replace("%player%", args[1]));
            return true;
        }

        String kind = args[2].toLowerCase(Locale.ROOT);
        if (args.length < 4 || !(kind.equals("mob") || kind.equals("item"))) {
            sendGiveUsage(sender);
            return true;
        }
        String value = args[3];
        int amountIndex = 4;

        String itemMaterial = null;
        EntityType type = null;
        if (kind.equals("item")) {
            itemMaterial = value.toUpperCase(Locale.ROOT);
            if (!plugin.menu().hasItemSpawner(itemMaterial)) {
                sender.sendMessage(plugin.language().message("invalid-item-spawner").replace("%type%", value));
                return true;
            }
        } else {
            type = Compat.entityType(value);
            if (type == null || !type.isAlive() || !type.isSpawnable()) {
                sender.sendMessage(plugin.language().message("invalid-mob-type").replace("%type%", value));
                return true;
            }
        }

        int amount = 1;
        if (args.length > amountIndex) {
            try {
                amount = Math.min(MAX_GIVE_AMOUNT, Math.max(1, Integer.parseInt(args[amountIndex])));
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.language().message("not-a-number"));
                return true;
            }
        }

        String translatedTypeName = itemMaterial != null
                ? plugin.language().itemName(itemMaterial)
                : plugin.language().entityName(type);
        ItemStack spawnerItem = itemMaterial != null
                ? plugin.items().createItemSpawner(itemMaterial)
                : plugin.items().createSpawnerItem(type);
        spawnerItem.setAmount(amount);
        Scheduler.runEntityTask(plugin, target, () -> plugin.items().giveOrDrop(target, spawnerItem));

        sender.sendMessage(plugin.language().message("spawner-given-sender")
                .replace("%player%", target.getName())
                .replace("%type%", translatedTypeName) + " x" + amount);
        target.sendMessage(plugin.language().message("spawner-given-recipient")
                .replace("%type%", translatedTypeName) + " x" + amount);
        return true;
    }

    private boolean giveBooster(CommandSender sender, String[] args) {
        if (!plugin.settings().boosterEnabled) {
            sender.sendMessage(plugin.language().message("booster-disabled"));
            return true;
        }
        if (args.length < 6) {
            plugin.language().messageLines("usage-givebooster").forEach(sender::sendMessage);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.language().message("player-not-found").replace("%player%", args[1]));
            return true;
        }

        int range;
        int delaySeconds;
        int xpPercent;
        int minutes;
        int amount = 1;
        try {
            range = parseBounded(args[2], 0, MAX_BOOST_RANGE);
            delaySeconds = parseBounded(args[3], 0, MAX_BOOST_DELAY_SECONDS);
            xpPercent = parseBounded(args[4], 0, MAX_BOOST_XP_PERCENT);

            String duration = args[5].toLowerCase(Locale.ROOT);
            if (duration.equals("unlimited") || duration.equals("infinite") || duration.equals("-1")) {
                minutes = -1;
            } else {
                minutes = parseBounded(duration, 1, MAX_BOOST_MINUTES);
            }
            if (args.length > 6) {
                amount = parseBounded(args[6], 1, MAX_GIVE_AMOUNT);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.language().message("not-a-number"));
            return true;
        }

        if (range == 0 && delaySeconds == 0 && xpPercent == 0) {
            sender.sendMessage(plugin.language().message("booster-no-effect"));
            return true;
        }

        if (delaySeconds > 0) {
            int maxDelay = plugin.spawnerSettings().maxTicketDelaySeconds();
            if (maxDelay <= 0) {
                sender.sendMessage(plugin.language().message("booster-delay-not-reducible")
                        .replace("%min%", String.valueOf(plugin.settings().boosterMinDelaySeconds)));
                return true;
            }
            if (delaySeconds > maxDelay) {
                sender.sendMessage(plugin.language().message("booster-delay-too-high")
                        .replace("%max%", String.valueOf(maxDelay))
                        .replace("%min%", String.valueOf(plugin.settings().boosterMinDelaySeconds)));
                return true;
            }
        }

        BoosterManager.Ticket ticket = new BoosterManager.Ticket(range, delaySeconds, xpPercent, minutes);
        ItemStack tickets = plugin.boosters().createTicket(ticket, amount);
        Scheduler.runEntityTask(plugin, target, () -> plugin.items().giveOrDrop(target, tickets));

        sender.sendMessage(plugin.language().message("booster-given-sender")
                .replace("%player%", target.getName())
                .replace("%amount%", String.valueOf(amount)));
        target.sendMessage(plugin.language().message("booster-given-recipient")
                .replace("%amount%", String.valueOf(amount)));
        return true;
    }

    private int parseBounded(String raw, int min, int max) {
        int value = Integer.parseInt(raw);
        if (value < min || value > max) {
            throw new NumberFormatException("out of range");
        }
        return value;
    }

    private void sendGiveUsage(CommandSender sender) {
        sender.sendMessage(plugin.language().message("usage-givespawner"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("reload", "pickaxegive", "givespawner", "givebooster"), completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("pickaxegive") || args[0].equalsIgnoreCase("givespawner") || args[0].equalsIgnoreCase("givebooster"))) {
            StringUtil.copyPartialMatches(args[1],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pickaxegive")) {
            StringUtil.copyPartialMatches(args[2],
                    IntStream.rangeClosed(1, 1000).mapToObj(String::valueOf).collect(Collectors.toList()), completions);
        } else if (args[0].equalsIgnoreCase("givebooster") && args.length >= 3 && args.length <= 7) {
            List<String> options;
            switch (args.length) {
                case 3 -> options = Arrays.asList("5", "10", "20");
                case 4 -> {
                    int maxDelay = plugin.spawnerSettings().maxTicketDelaySeconds();
                    options = new ArrayList<>();
                    options.add("0");
                    for (int candidate : new int[]{5, 10, 15}) {
                        if (candidate < maxDelay) options.add(String.valueOf(candidate));
                    }
                    if (maxDelay > 0) options.add(String.valueOf(maxDelay));
                }
                case 5 -> options = Arrays.asList("0", "25", "50", "100");
                case 6 -> options = Arrays.asList("30", "60", "unlimited");
                default -> options = Arrays.asList("1", "16", "64");
            }
            StringUtil.copyPartialMatches(args[args.length - 1], options, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("givespawner")) {
            StringUtil.copyPartialMatches(args[2], Arrays.asList("mob", "item"), completions);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("givespawner")) {
            if (args[2].equalsIgnoreCase("mob")) {
                List<String> mobs = Arrays.stream(EntityType.values())
                        .filter(t -> t.isAlive() && t.isSpawnable())
                        .map(Enum::name)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[3], mobs, completions);
            } else if (args[2].equalsIgnoreCase("item")) {
                StringUtil.copyPartialMatches(args[3], new ArrayList<>(plugin.menu().itemSpawnerKeys()), completions);
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("givespawner")) {
            StringUtil.copyPartialMatches(args[4], Arrays.asList("1", "16", "64"), completions);
        }
        return completions;
    }
}

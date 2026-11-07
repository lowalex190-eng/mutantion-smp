package com.mutationsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MutationCommand implements TabExecutor {

    private static final String ADMIN = "mutationsmp.admin";

    private final MutationSMP plugin;
    private final MutationManager mm;

    public MutationCommand(MutationSMP plugin, MutationManager mm) {
        this.plugin = plugin;
        this.mm = mm;
    }

    private void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                showMutations(sender, p);
            } else {
                usage(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> listAll(sender);
            case "info" -> info(sender, args);
            case "check" -> check(sender, args);
            case "give", "remove", "reroll", "clear", "reload" -> {
                if (!sender.hasPermission(ADMIN)) {
                    msg(sender, "You don't have permission to do that.", NamedTextColor.RED);
                    return true;
                }
                switch (sub) {
                    case "give" -> give(sender, args);
                    case "remove" -> remove(sender, args);
                    case "reroll" -> reroll(sender, args);
                    case "clear" -> clear(sender, args);
                    default -> {
                        plugin.reloadConfig();
                        msg(sender, "MutationSMP config reloaded.", NamedTextColor.GREEN);
                    }
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        msg(s, "/mutation  - your mutations", NamedTextColor.GRAY);
        msg(s, "/mutation list  - every mutation", NamedTextColor.GRAY);
        msg(s, "/mutation info <mutation>", NamedTextColor.GRAY);
        msg(s, "/mutation check <player>", NamedTextColor.GRAY);
        if (s.hasPermission(ADMIN)) {
            msg(s, "/mutation give <player> <mutation|random>", NamedTextColor.GOLD);
            msg(s, "/mutation remove <player> <mutation|newest|all>", NamedTextColor.GOLD);
            msg(s, "/mutation reroll <player>", NamedTextColor.GOLD);
            msg(s, "/mutation clear <player>", NamedTextColor.GOLD);
            msg(s, "/mutation reload", NamedTextColor.GOLD);
        }
    }

    // ---------------- public subcommands ----------------

    private void showMutations(CommandSender to, Player target) {
        List<MutationType> list = mm.get(target);
        to.sendMessage(Component.text(
                target.getName() + "'s mutations (" + list.size() + "/" + mm.maxMutations() + ")",
                NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        if (list.isEmpty()) {
            msg(to, "None yet. Go get some kills.", NamedTextColor.GRAY);
            return;
        }
        for (MutationType t : list) {
            to.sendMessage(mm.describe(t));
        }
    }

    private void listAll(CommandSender to) {
        to.sendMessage(Component.text("All mutations", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        for (MutationType t : MutationType.values()) {
            if (!mm.isEnabled(t)) continue;
            to.sendMessage(mm.describe(t));
        }
    }

    private void info(CommandSender to, String[] args) {
        if (args.length < 2) {
            msg(to, "Usage: /mutation info <mutation>", NamedTextColor.RED);
            return;
        }
        MutationType t = MutationType.fromId(args[1]);
        if (t == null) {
            msg(to, "Unknown mutation: " + args[1], NamedTextColor.RED);
            return;
        }
        to.sendMessage(mm.describe(t));
    }

    private void check(CommandSender to, String[] args) {
        if (args.length < 2) {
            msg(to, "Usage: /mutation check <player>", NamedTextColor.RED);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(to, "That player isn't online.", NamedTextColor.RED);
            return;
        }
        showMutations(to, target);
    }

    // ---------------- admin subcommands ----------------

    private Player target(CommandSender s, String[] args) {
        if (args.length < 2) {
            usage(s);
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) msg(s, "That player isn't online.", NamedTextColor.RED);
        return target;
    }

    private void give(CommandSender s, String[] args) {
        Player target = target(s, args);
        if (target == null) return;
        if (args.length < 3) {
            msg(s, "Usage: /mutation give <player> <mutation|random>", NamedTextColor.RED);
            return;
        }
        if (args[2].equalsIgnoreCase("random")) {
            MutationType got = mm.giveRandom(target, true, true);
            if (got == null) {
                msg(s, target.getName() + " has every mutation already.", NamedTextColor.RED);
            } else {
                msg(s, "Gave " + target.getName() + " " + got.displayName() + ".", NamedTextColor.GREEN);
            }
            return;
        }
        MutationType t = MutationType.fromId(args[2]);
        if (t == null) {
            msg(s, "Unknown mutation: " + args[2], NamedTextColor.RED);
            return;
        }
        if (mm.add(target, t, true, true)) {
            msg(s, "Gave " + target.getName() + " " + t.displayName() + ".", NamedTextColor.GREEN);
        } else {
            msg(s, target.getName() + " already has " + t.displayName() + ".", NamedTextColor.RED);
        }
    }

    private void remove(CommandSender s, String[] args) {
        Player target = target(s, args);
        if (target == null) return;
        if (args.length < 3) {
            msg(s, "Usage: /mutation remove <player> <mutation|newest|all>", NamedTextColor.RED);
            return;
        }
        String what = args[2].toLowerCase(Locale.ROOT);
        if (what.equals("all")) {
            mm.clear(target);
            msg(target, "All your mutations were removed.", NamedTextColor.RED);
            msg(s, "Cleared " + target.getName() + ".", NamedTextColor.GREEN);
        } else if (what.equals("newest")) {
            MutationType t = mm.removeNewest(target, true);
            if (t == null) msg(s, target.getName() + " has no mutations.", NamedTextColor.RED);
            else msg(s, "Removed " + t.displayName() + " from " + target.getName() + ".", NamedTextColor.GREEN);
        } else {
            MutationType t = MutationType.fromId(what);
            if (t == null) {
                msg(s, "Unknown mutation: " + args[2], NamedTextColor.RED);
            } else if (mm.remove(target, t, true)) {
                msg(s, "Removed " + t.displayName() + " from " + target.getName() + ".", NamedTextColor.GREEN);
            } else {
                msg(s, target.getName() + " doesn't have " + t.displayName() + ".", NamedTextColor.RED);
            }
        }
    }

    private void reroll(CommandSender s, String[] args) {
        Player target = target(s, args);
        if (target == null) return;
        int count = Math.max(1, mm.get(target).size());
        mm.clear(target);
        for (int i = 0; i < count; i++) {
            mm.giveRandom(target, i == count - 1, false);
        }
        msg(target, "Your mutations were rerolled!", NamedTextColor.LIGHT_PURPLE);
        showMutations(s, target);
    }

    private void clear(CommandSender s, String[] args) {
        Player target = target(s, args);
        if (target == null) return;
        mm.clear(target);
        msg(target, "All your mutations were removed.", NamedTextColor.RED);
        msg(s, "Cleared " + target.getName() + ".", NamedTextColor.GREEN);
    }

    // ---------------- tab completion ----------------

    private List<String> ids() {
        List<String> out = new ArrayList<>();
        for (MutationType t : MutationType.values()) out.add(t.id());
        return out;
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            out.addAll(List.of("list", "info", "check"));
            if (admin) out.addAll(List.of("give", "remove", "reroll", "clear", "reload"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info")) {
                out.addAll(ids());
            } else if (sub.equals("check")
                    || (admin && List.of("give", "remove", "reroll", "clear").contains(sub))) {
                out.addAll(onlineNames());
            }
        } else if (args.length == 3 && admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give")) {
                out.add("random");
                out.addAll(ids());
            } else if (sub.equals("remove")) {
                out.addAll(List.of("newest", "all"));
                out.addAll(ids());
            }
        }

        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}

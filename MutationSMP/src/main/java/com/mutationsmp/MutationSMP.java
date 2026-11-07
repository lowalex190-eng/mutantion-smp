package com.mutationsmp;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MutationSMP extends JavaPlugin {

    private MutationManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new MutationManager(this);

        getServer().getPluginManager().registerEvents(new MutationListener(this, manager), this);

        MutationCommand handler = new MutationCommand(this, manager);
        PluginCommand cmd = getCommand("mutation");
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // Runs every 5 ticks (4x per second) for tick-based mutations.
        Bukkit.getScheduler().runTaskTimer(this, new MutationTicker(manager), 20L, 5L);

        // Armor overlays (needs the MutationSMP resource pack on the client).
        Bukkit.getScheduler().runTaskTimer(this, new ArmorOverlayTask(this, manager), 40L, 20L);

        // /reload safety: re-sync anyone already online.
        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.sync(p);
        }
        getLogger().info("MutationSMP enabled with " + MutationType.values().length + " mutations.");
    }

    public MutationManager getManager() {
        return manager;
    }
}

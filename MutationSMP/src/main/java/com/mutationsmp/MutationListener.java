package com.mutationsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MutationListener implements Listener {

    private final MutationSMP plugin;
    private final MutationManager mm;
    private final Random random = new Random();

    /** killer -> (victim -> last reward time in ms) */
    private final Map<UUID, Map<UUID, Long>> killCooldowns = new HashMap<>();

    public MutationListener(MutationSMP plugin, MutationManager mm) {
        this.plugin = plugin;
        this.mm = mm;
    }

    public static boolean isSword(Material m) {
        return m.name().endsWith("_SWORD");
    }

    // ------------------------------------------------------------------
    //  Join / quit / respawn
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        mm.unload(p);
        mm.sync(p);
        mm.giveStartingIfNeeded(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mm.unload(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        // Re-apply attribute modifiers on the fresh body, then heal to the new max.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            mm.sync(p);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) p.setHealth(max.getValue());
        }, 2L);
    }

    // ------------------------------------------------------------------
    //  Deaths: lose newest mutation, killer gains one
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim);

        String mode = cfg.getString("lose-on-death", "ALL").toUpperCase();
        MutationType lost = null;
        if (mode.equals("ALL") || (mode.equals("PVP") && pvp)) {
            lost = mm.removeNewest(victim, true);
        }

        if (pvp && cfg.getBoolean("pvp-kill-gives-mutation", true)) {
            rewardKill(killer, victim, lost);
        }
    }

    private void rewardKill(Player killer, Player victim, MutationType stolen) {
        FileConfiguration cfg = plugin.getConfig();
        long cooldownMs = cfg.getLong("same-victim-cooldown-seconds", 600) * 1000L;
        long now = System.currentTimeMillis();

        Map<UUID, Long> perVictim = killCooldowns.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
        Long last = perVictim.get(victim.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            killer.sendActionBar(Component.text(
                    "No mutation: you killed this player too recently.", NamedTextColor.GRAY));
            return;
        }
        perVictim.put(victim.getUniqueId(), now);

        boolean steal = "STEAL".equalsIgnoreCase(cfg.getString("kill-reward-mode", "RANDOM"));
        if (steal && stolen != null && !mm.has(killer, stolen)) {
            mm.add(killer, stolen, true, true);
            return;
        }
        if (mm.giveRandom(killer, true, true) == null) {
            killer.sendActionBar(Component.text(
                    "You already have every mutation!", NamedTextColor.GOLD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent e) {
        if (e instanceof PlayerDeathEvent) return;
        if (!plugin.getConfig().getBoolean("boss-kills-give-mutation", true)) return;
        EntityType type = e.getEntityType();
        if (type != EntityType.ENDER_DRAGON && type != EntityType.WITHER && type != EntityType.WARDEN) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        mm.giveRandom(killer, true, true);
    }

    // ------------------------------------------------------------------
    //  Damage taken
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        List<MutationType> owned = mm.get(p);
        if (owned.isEmpty()) return;
        DamageCause cause = e.getCause();

        if (owned.contains(MutationType.FEATHER_BONES)) {
            if (cause == DamageCause.FALL) {
                e.setCancelled(true);
                return;
            }
            if (cause == DamageCause.PROJECTILE) {
                e.setDamage(e.getDamage() * 1.5);
            }
        }

        if (owned.contains(MutationType.LAVA_BLOOD)
                && (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK
                || cause == DamageCause.LAVA || cause == DamageCause.HOT_FLOOR
                || cause == DamageCause.CAMPFIRE)) {
            e.setCancelled(true);
            p.setFireTicks(0);
            return;
        }

        if (owned.contains(MutationType.MAGNETIC_PALMS) && random.nextDouble() < 0.04) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!hand.getType().isAir()) {
                p.dropItem(true);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Damage dealt
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        List<MutationType> owned = mm.get(p);
        if (owned.isEmpty()) return;

        double dmg = e.getDamage();

        if (owned.contains(MutationType.JITTERY_HANDS)
                && isSword(p.getInventory().getItemInMainHand().getType())) {
            dmg *= 2.0;
        }

        if (owned.contains(MutationType.BERSERKER)) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = max != null ? max.getValue() : 20.0;
            double missing = Math.max(0.0, 1.0 - (p.getHealth() / maxHp));
            dmg *= 1.0 + 0.8 * missing;
        }

        if (owned.contains(MutationType.STATIC_CHARGE) && random.nextDouble() < 0.20) {
            dmg += 4.0;
            e.getEntity().getWorld().strikeLightningEffect(e.getEntity().getLocation());
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, false));
        }

        e.setDamage(dmg);
    }

    /** Runs after everything else so we heal from the damage that was actually dealt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity) || e.getEntity() instanceof ArmorStand) return;
        if (p.isDead() || !mm.has(p, MutationType.VAMPIRIC)) return;

        double heal = e.getFinalDamage() * 0.25;
        if (heal <= 0) return;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.min(maxHp, p.getHealth() + heal));
    }

    // ------------------------------------------------------------------
    //  Misc drawbacks
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p
                && e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                && mm.has(p, MutationType.VAMPIRIC)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        Player p = e.getPlayer();
        if (e.isSprinting() && p.isInWater() && mm.has(p, MutationType.BRUTE_HEART)) {
            e.setCancelled(true);
        }
    }
}

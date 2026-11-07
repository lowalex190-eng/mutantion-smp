package com.mutationsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores each player's mutations (in their persistent data, so it survives
 * restarts) and applies / removes attribute modifiers.
 * The list is ordered oldest -> newest.
 */
public class MutationManager {

    private final MutationSMP plugin;
    private final NamespacedKey dataKey;
    private final NamespacedKey startedKey;
    private final Map<UUID, List<MutationType>> cache = new HashMap<>();
    private final Random random = new Random();

    public MutationManager(MutationSMP plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "mutations");
        this.startedKey = new NamespacedKey(plugin, "got_starting_mutation");
    }

    // ------------------------------------------------------------------
    //  Storage
    // ------------------------------------------------------------------

    private List<MutationType> raw(Player p) {
        return cache.computeIfAbsent(p.getUniqueId(), id -> load(p));
    }

    private List<MutationType> load(Player p) {
        List<MutationType> list = new ArrayList<>();
        String stored = p.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (stored != null && !stored.isEmpty()) {
            for (String s : stored.split(",")) {
                MutationType t = MutationType.fromId(s);
                if (t != null && !list.contains(t)) list.add(t);
            }
        }
        return list;
    }

    private void save(Player p, List<MutationType> list) {
        cache.put(p.getUniqueId(), list);
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (list.isEmpty()) {
            pdc.remove(dataKey);
        } else {
            pdc.set(dataKey, PersistentDataType.STRING,
                    list.stream().map(MutationType::id).collect(Collectors.joining(",")));
        }
    }

    public void unload(Player p) {
        cache.remove(p.getUniqueId());
    }

    /** Read-only view, oldest first. */
    public List<MutationType> get(Player p) {
        return Collections.unmodifiableList(raw(p));
    }

    public boolean has(Player p, MutationType t) {
        return raw(p).contains(t);
    }

    // ------------------------------------------------------------------
    //  Config helpers
    // ------------------------------------------------------------------

    public boolean isEnabled(MutationType t) {
        return plugin.getConfig().getBoolean("mutations." + t.id() + ".enabled", true);
    }

    public int weight(MutationType t) {
        return Math.max(0, plugin.getConfig().getInt("mutations." + t.id() + ".weight", 10));
    }

    public int maxMutations() {
        return Math.max(1, plugin.getConfig().getInt("max-mutations", 5));
    }

    // ------------------------------------------------------------------
    //  Adding / removing
    // ------------------------------------------------------------------

    /** Picks a random enabled mutation the player doesn't own (weighted) and gives it. */
    public MutationType giveRandom(Player p, boolean announce, boolean broadcast) {
        List<MutationType> owned = raw(p);
        List<MutationType> pool = new ArrayList<>();
        int total = 0;
        for (MutationType t : MutationType.values()) {
            if (owned.contains(t) || !isEnabled(t)) continue;
            int w = weight(t);
            if (w <= 0) continue;
            pool.add(t);
            total += w;
        }
        if (pool.isEmpty() || total <= 0) return null;

        int roll = random.nextInt(total);
        for (MutationType t : pool) {
            roll -= weight(t);
            if (roll < 0) {
                add(p, t, announce, broadcast);
                return t;
            }
        }
        return null;
    }

    /** Gives a specific mutation. Returns false if the player already has it. */
    public boolean add(Player p, MutationType t, boolean announce, boolean broadcast) {
        List<MutationType> list = new ArrayList<>(raw(p));
        if (list.contains(t)) return false;
        list.add(t);

        List<MutationType> faded = new ArrayList<>();
        while (list.size() > maxMutations()) {
            faded.add(list.remove(0)); // oldest fades
        }
        save(p, list);

        for (MutationType f : faded) {
            removeAttributes(p, f);
            cleanup(p, f);
            if (announce) {
                p.sendMessage(Component.text("Your oldest mutation faded away: ", NamedTextColor.GRAY)
                        .append(Component.text(f.displayName(), f.color())));
            }
        }

        applyAttributes(p, t);
        clampHealth(p);

        if (announce) {
            p.showTitle(Title.title(
                    Component.text("MUTATED", t.color(), TextDecoration.BOLD),
                    Component.text(t.displayName(), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
            p.sendMessage(describe(t));
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.8f);
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        }

        if (broadcast && plugin.getConfig().getBoolean("broadcast-gains", true)) {
            Bukkit.broadcast(Component.text(p.getName(), NamedTextColor.WHITE)
                    .append(Component.text(" mutated: ", NamedTextColor.GRAY))
                    .append(Component.text(t.displayName(), t.color(), TextDecoration.BOLD)));
        }
        return true;
    }

    public boolean remove(Player p, MutationType t, boolean announce) {
        List<MutationType> list = new ArrayList<>(raw(p));
        if (!list.remove(t)) return false;
        save(p, list);
        removeAttributes(p, t);
        cleanup(p, t);
        clampHealth(p);
        if (announce) {
            p.sendMessage(Component.text("You lost a mutation: ", NamedTextColor.RED)
                    .append(Component.text(t.displayName(), t.color(), TextDecoration.BOLD)));
        }
        return true;
    }

    /** Removes the most recently gained mutation. Returns it, or null if none. */
    public MutationType removeNewest(Player p, boolean announce) {
        List<MutationType> list = raw(p);
        if (list.isEmpty()) return null;
        MutationType t = list.get(list.size() - 1);
        remove(p, t, announce);
        return t;
    }

    public void clear(Player p) {
        for (MutationType t : new ArrayList<>(raw(p))) {
            remove(p, t, false);
        }
    }

    /** First-join bonus mutation. */
    public void giveStartingIfNeeded(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(startedKey, PersistentDataType.BYTE)) return;
        pdc.set(startedKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.getConfig().getBoolean("starting-mutation", true)) {
            giveRandom(p, true, true);
        }
    }

    // ------------------------------------------------------------------
    //  Attributes
    // ------------------------------------------------------------------

    private NamespacedKey modKey(MutationType t, int index) {
        return new NamespacedKey(plugin, "mut_" + t.id() + "_" + index);
    }

    private void applyAttributes(Player p, MutationType t) {
        Mod[] mods = t.mods();
        for (int i = 0; i < mods.length; i++) {
            AttributeInstance inst = p.getAttribute(mods[i].attribute());
            if (inst == null) continue;
            NamespacedKey key = modKey(t, i);
            removeKey(inst, key);
            inst.addModifier(new AttributeModifier(key, mods[i].amount(), mods[i].operation()));
        }
    }

    private void removeAttributes(Player p, MutationType t) {
        Mod[] mods = t.mods();
        for (int i = 0; i < mods.length; i++) {
            AttributeInstance inst = p.getAttribute(mods[i].attribute());
            if (inst == null) continue;
            removeKey(inst, modKey(t, i));
        }
    }

    private void removeKey(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
    }

    /** Rebuilds every modifier from stored data. Use on join / respawn. */
    public void sync(Player p) {
        for (MutationType t : MutationType.values()) removeAttributes(p, t);
        for (MutationType t : raw(p)) applyAttributes(p, t);
        clampHealth(p);
    }

    public void clampHealth(Player p) {
        if (p.isDead()) return;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && p.getHealth() > max.getValue()) {
            p.setHealth(max.getValue());
        }
    }

    /** Removes potion effects that a mutation was maintaining. */
    private void cleanup(Player p, MutationType t) {
        switch (t) {
            case NIGHT_STALKER -> p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            case GILLBORN -> {
                p.removePotionEffect(PotionEffectType.WATER_BREATHING);
                p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    //  Display
    // ------------------------------------------------------------------

    public Component describe(MutationType t) {
        return Component.text()
                .append(Component.text("\u2605 " + t.displayName(), t.color(), TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("  + ", NamedTextColor.GREEN))
                .append(Component.text(t.buff(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("  - ", NamedTextColor.RED))
                .append(Component.text(t.drawback(), NamedTextColor.GRAY))
                .build();
    }
}

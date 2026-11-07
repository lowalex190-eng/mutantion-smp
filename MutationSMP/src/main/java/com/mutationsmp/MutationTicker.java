package com.mutationsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;

/** Handles every mutation that needs to be checked on a timer (runs every 5 ticks). */
public class MutationTicker implements Runnable {

    private final MutationManager mm;
    private final Random random = new Random();
    private long tick = 0;

    public MutationTicker(MutationManager mm) {
        this.mm = mm;
    }

    @Override
    public void run() {
        tick += 5;
        boolean second = tick % 20 == 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            for (MutationType t : mm.get(p)) {
                handle(p, t, second);
            }
        }
    }

    private void handle(Player p, MutationType t, boolean second) {
        switch (t) {
            case BRUTE_HEART -> {
                if (p.isSprinting() && p.isInWater()) p.setSprinting(false);
            }

            case JITTERY_HANDS -> {
                if (MutationListener.isSword(p.getInventory().getItemInMainHand().getType())
                        && random.nextInt(50) == 0) { // roughly every ~12 seconds
                    Location l = p.getLocation();
                    float yaw = l.getYaw() + (random.nextFloat() * 60f - 30f);
                    float pitch = l.getPitch() + (random.nextFloat() * 24f - 12f);
                    pitch = Math.max(-90f, Math.min(90f, pitch));
                    p.setRotation(yaw, pitch);
                }
            }

            case LAVA_BLOOD -> {
                if (p.getFireTicks() > 0) p.setFireTicks(0);
                if (second && (p.isInWater() || p.isInRain())) {
                    p.damage(1.0);
                }
            }

            case NIGHT_STALKER -> {
                if (second) {
                    effect(p, PotionEffectType.NIGHT_VISION, 400, 0, false);
                    World w = p.getWorld();
                    if (w.getEnvironment() == World.Environment.NORMAL
                            && w.isDayTime()
                            && !p.isInWater()
                            && p.getLocation().getBlock().getLightFromSky() >= 15) {
                        effect(p, PotionEffectType.WEAKNESS, 60, 0, true);
                    }
                }
            }

            case SPEEDSTER -> {
                if (tick % 600 == 0) { // every 30 seconds
                    if (p.getSaturation() > 0f) {
                        p.setSaturation(Math.max(0f, p.getSaturation() - 1f));
                    } else if (p.getFoodLevel() > 0) {
                        p.setFoodLevel(p.getFoodLevel() - 1);
                    }
                }
            }

            case BERSERKER -> {
                if (p.isBlocking()) p.clearActiveItem();
            }

            case MAGNETIC_PALMS -> {
                Location target = p.getLocation().add(0, 0.6, 0);
                for (Entity e : p.getNearbyEntities(6, 6, 6)) {
                    if (!(e instanceof Item item)) continue;
                    if (item.getPickupDelay() > 0) continue;
                    Vector dir = target.toVector().subtract(item.getLocation().toVector());
                    if (dir.lengthSquared() < 0.25) continue;
                    item.setVelocity(dir.normalize().multiply(0.4));
                }
            }

            case GILLBORN -> {
                if (p.isInWater()) {
                    effect(p, PotionEffectType.WATER_BREATHING, 100, 0, true);
                    effect(p, PotionEffectType.DOLPHINS_GRACE, 100, 0, true);
                } else {
                    effect(p, PotionEffectType.SLOWNESS, 30, 0, false);
                }
            }

            default -> { }
        }
    }

    /** Applies an effect without downgrading a stronger one the player already has. */
    private void effect(Player p, PotionEffectType type, int duration, int amplifier, boolean visible) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && (cur.getAmplifier() > amplifier
                || (cur.getAmplifier() == amplifier && cur.getDuration() > duration))) {
            return;
        }
        p.addPotionEffect(new PotionEffect(type, duration, amplifier, false, visible, visible));
    }
}

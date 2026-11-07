package com.mutationsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;

import java.util.Locale;

import static org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER;
import static org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR;

/**
 * Every mutation: a buff AND a drawback.
 * Attribute-based numbers live here. Event/tick-based behaviour is in
 * MutationListener and MutationTicker.
 */
public enum MutationType {

    BRUTE_HEART("Brute Heart", NamedTextColor.RED,
            "+3 hearts",
            "Can't sprint while in water",
            new Mod(Attribute.MAX_HEALTH, 6.0, ADD_NUMBER)),

    GLASS_CANNON("Glass Cannon", NamedTextColor.GOLD,
            "+60% melee damage",
            "-3 hearts",
            new Mod(Attribute.ATTACK_DAMAGE, 0.6, ADD_SCALAR),
            new Mod(Attribute.MAX_HEALTH, -6.0, ADD_NUMBER)),

    JITTERY_HANDS("Jittery Hands", NamedTextColor.YELLOW,
            "Double damage with swords",
            "Your camera randomly flicks while holding a sword"),

    FEATHER_BONES("Feather Bones", NamedTextColor.WHITE,
            "No fall damage and higher jumps",
            "Take +50% damage from projectiles",
            new Mod(Attribute.JUMP_STRENGTH, 0.25, ADD_SCALAR)),

    LAVA_BLOOD("Lava Blood", NamedTextColor.RED,
            "Immune to fire and lava",
            "Water and rain hurt you"),

    NIGHT_STALKER("Night Stalker", NamedTextColor.DARK_PURPLE,
            "Permanent night vision",
            "Weakened in direct sunlight"),

    SPEEDSTER("Speedster", NamedTextColor.AQUA,
            "+25% movement speed",
            "Hunger drains faster",
            new Mod(Attribute.MOVEMENT_SPEED, 0.25, ADD_SCALAR)),

    STONE_SKIN("Stone Skin", NamedTextColor.GRAY,
            "+5 armor and 60% knockback resistance",
            "-15% movement speed",
            new Mod(Attribute.ARMOR, 5.0, ADD_NUMBER),
            new Mod(Attribute.KNOCKBACK_RESISTANCE, 0.6, ADD_NUMBER),
            new Mod(Attribute.MOVEMENT_SPEED, -0.15, ADD_SCALAR)),

    VAMPIRIC("Vampiric", NamedTextColor.DARK_RED,
            "Heal 25% of the melee damage you deal",
            "No natural regeneration from food"),

    TINY_TERROR("Tiny Terror", NamedTextColor.GREEN,
            "Much smaller hitbox and +10% speed",
            "-1 block reach and -25% melee damage",
            new Mod(Attribute.SCALE, -0.35, ADD_SCALAR),
            new Mod(Attribute.MOVEMENT_SPEED, 0.10, ADD_SCALAR),
            new Mod(Attribute.ENTITY_INTERACTION_RANGE, -1.0, ADD_NUMBER),
            new Mod(Attribute.ATTACK_DAMAGE, -0.25, ADD_SCALAR)),

    TITAN_FRAME("Titan Frame", NamedTextColor.DARK_AQUA,
            "+2 hearts and +1 block attack reach",
            "-10% speed and slower attacks",
            new Mod(Attribute.MAX_HEALTH, 4.0, ADD_NUMBER),
            new Mod(Attribute.ENTITY_INTERACTION_RANGE, 1.0, ADD_NUMBER),
            new Mod(Attribute.MOVEMENT_SPEED, -0.10, ADD_SCALAR),
            new Mod(Attribute.ATTACK_SPEED, -0.15, ADD_SCALAR)),

    MAGNETIC_PALMS("Magnetic Palms", NamedTextColor.BLUE,
            "Nearby dropped items fly to you",
            "4% chance to drop your held item when hurt"),

    STATIC_CHARGE("Static Charge", NamedTextColor.YELLOW,
            "20% chance on hit for a lightning strike (+4 damage)",
            "Every strike makes you glow for 5 seconds"),

    BERSERKER("Berserker", NamedTextColor.DARK_RED,
            "Up to +80% melee damage as your health drops",
            "Can't block with shields"),

    GILLBORN("Gillborn", NamedTextColor.DARK_AQUA,
            "Water breathing and dolphin's grace while in water",
            "Slowed while on land");

    private final String displayName;
    private final NamedTextColor color;
    private final String buff;
    private final String drawback;
    private final Mod[] mods;

    MutationType(String displayName, NamedTextColor color, String buff, String drawback, Mod... mods) {
        this.displayName = displayName;
        this.color = color;
        this.buff = buff;
        this.drawback = drawback;
        this.mods = mods;
    }

    public String displayName() { return displayName; }
    public NamedTextColor color() { return color; }
    public String buff() { return buff; }
    public String drawback() { return drawback; }
    public Mod[] mods() { return mods; }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MutationType fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

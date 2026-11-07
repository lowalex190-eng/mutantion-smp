package com.mutationsmp;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Locale;

/**
 * Makes mutations visible on armor.
 *
 * Once per second, every piece of vanilla armor a player is wearing gets its
 * "equippable asset id" switched to e.g. mutationsmp:iron_vampiric, which the
 * MutationSMP resource pack renders as the normal armor PLUS a mutation overlay.
 * The overlay shows the player's NEWEST mutation. When they lose it (or the
 * setting is disabled) the armor is reset to vanilla.
 */
public class ArmorOverlayTask implements Runnable {

    public static final String NAMESPACE = "mutationsmp";

    private final MutationSMP plugin;
    private final MutationManager mm;

    public ArmorOverlayTask(MutationSMP plugin, MutationManager mm) {
        this.plugin = plugin;
        this.mm = mm;
    }

    @Override
    public void run() {
        boolean enabled = plugin.getConfig().getBoolean("armor-overlay.enabled", true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            MutationType shown = null;
            if (enabled) {
                List<MutationType> list = mm.get(p);
                if (!list.isEmpty()) shown = list.get(list.size() - 1);
            }

            PlayerInventory inv = p.getInventory();

            ItemStack helmet = inv.getHelmet();
            if (update(helmet, shown, EquipmentSlot.HEAD)) inv.setHelmet(helmet);

            ItemStack chest = inv.getChestplate();
            if (update(chest, shown, EquipmentSlot.CHEST)) inv.setChestplate(chest);

            ItemStack legs = inv.getLeggings();
            if (update(legs, shown, EquipmentSlot.LEGS)) inv.setLeggings(legs);

            ItemStack boots = inv.getBoots();
            if (update(boots, shown, EquipmentSlot.FEET)) inv.setBoots(boots);
        }
    }

    /** Returns true if the item was changed. */
    private boolean update(ItemStack item, MutationType shown, EquipmentSlot slot) {
        if (item == null || item.getType().isAir()) return false;

        Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
        if (current == null) return false;

        Key currentId = current.assetId();
        boolean ours = currentId != null && currentId.namespace().equals(NAMESPACE);
        String material = materialKey(item.getType());

        // Nothing to show (or armor type we have no overlay for): restore vanilla if we changed it.
        if (shown == null || material == null) {
            if (ours) {
                item.resetData(DataComponentTypes.EQUIPPABLE);
                return true;
            }
            return false;
        }

        // Don't touch armor that another plugin gave a custom model.
        if (currentId != null && !ours && !currentId.namespace().equals("minecraft")) return false;

        Key wanted = Key.key(NAMESPACE, material + "_" + shown.id());
        if (wanted.equals(currentId)) return false;

        Equippable updated = Equippable.equippable(slot)
                .assetId(wanted)
                .equipSound(current.equipSound())
                .damageOnHurt(current.damageOnHurt())
                .build();
        item.setData(DataComponentTypes.EQUIPPABLE, updated);
        return true;
    }

    /** Maps an armor Material to its vanilla equipment name, or null if unsupported. */
    private String materialKey(Material m) {
        String n = m.name().toUpperCase(Locale.ROOT);
        if (!(n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"))) {
            return null;
        }
        if (n.startsWith("IRON_")) return "iron";
        if (n.startsWith("GOLDEN_")) return "gold";
        if (n.startsWith("DIAMOND_")) return "diamond";
        if (n.startsWith("NETHERITE_")) return "netherite";
        if (n.startsWith("CHAINMAIL_")) return "chainmail";
        if (n.startsWith("COPPER_")) return "copper";
        return null; // leather, turtle shell, modded armor...
    }
}

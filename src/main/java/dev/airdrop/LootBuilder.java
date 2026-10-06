package dev.airdrop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.logging.Logger;

public final class LootBuilder {
    private LootBuilder() {}

    /** Читает секцию loot из config.yml: ключ — номер слота 0..53. Возвращает массив из 54 предметов. */
    public static ItemStack[] build(ConfigurationSection loot, Logger log) {
        ItemStack[] out = new ItemStack[54];
        if (loot == null) return out;
        for (String key : loot.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                log.warning("loot: слот '" + key + "' не число — пропущен");
                continue;
            }
            if (slot < 0 || slot > 53) {
                log.warning("loot: слот " + slot + " вне диапазона 0..53 — пропущен");
                continue;
            }
            ConfigurationSection s = loot.getConfigurationSection(key);
            if (s == null) continue;
            ItemStack item = item(s, log);
            if (item != null) out[slot] = item;
        }
        return out;
    }

    private static ItemStack item(ConfigurationSection s, Logger log) {
        String name = s.getString("item", "");
        Material m = Material.matchMaterial(name);
        if (m == null || m.isAir()) {
            log.warning("loot: неизвестный предмет '" + name + "' — пропущен");
            return null;
        }
        ItemStack item = new ItemStack(m, Math.max(1, s.getInt("amount", 1)));

        String potion = s.getString("potion");
        if (potion != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta pm) {
                try {
                    pm.setBasePotionType(PotionType.valueOf(potion.toUpperCase(Locale.ROOT)));
                    item.setItemMeta(pm);
                } catch (IllegalArgumentException e) {
                    log.warning("loot: неизвестное зелье '" + potion + "'");
                }
            }
        }

        ConfigurationSection ench = s.getConfigurationSection("enchants");
        if (ench != null) {
            ItemMeta meta = item.getItemMeta();
            for (String id : ench.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
                if (e == null) {
                    log.warning("loot: неизвестное зачарование '" + id + "'");
                    continue;
                }
                int level = ench.getInt(id, 1);
                if (meta instanceof EnchantmentStorageMeta esm) {
                    esm.addStoredEnchant(e, level, true);
                } else {
                    meta.addEnchant(e, level, true);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}

package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Enchantments;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KitManager {

    private final BFFA01 plugin;

    public KitManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void giveKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);

        UUID uuid = player.getUniqueId();

        ConfigurationSection kitConfig = plugin.getConfig().getConfigurationSection("kit");
        if (kitConfig == null) {
            return;
        }

        List<ItemStack> itemsToGive = new ArrayList<>();
        ConfigurationSection invConfig = kitConfig.getConfigurationSection("inventory");

        if (invConfig != null) {
            for (String key : invConfig.getKeys(false)) {
                try {
                    String matName = invConfig.getString(key + ".material", "AIR");
                    int amount = invConfig.getInt(key + ".amount", 1);
                    Material material = Material.matchMaterial(matName);

                    if (material != null) {
                        ItemStack item = new ItemStack(material, amount);
                        ItemMeta meta = item.getItemMeta();

                        if (meta != null) {
                            if (material == Material.IRON_SWORD && plugin.getDataManager().hasUpgrade(uuid, "sharpness")) {
                                enchant(meta, "sharpness", 1, true);
                            }
                            if (material == Material.BOW && plugin.getDataManager().hasUpgrade(uuid, "powerbow")) {
                                enchant(meta, "power", 1, true);
                            }
                            if (material == Material.BOW && plugin.getDataManager().hasUpgrade(uuid, "punchbow")) {
                                enchant(meta, "punch", 1, true);
                            }
                            if (material == Material.STICK && plugin.getDataManager().hasUpgrade(uuid, "knockback2")) {
                                enchant(meta, "knockback", 2, true);
                            }
                            item.setItemMeta(meta);
                        }

                        ConfigurationSection enchants = invConfig.getConfigurationSection(key + ".enchantments");
                        if (enchants != null) {
                            meta = item.getItemMeta();
                            if (meta != null) {
                                for (String enchantName : enchants.getKeys(false)) {
                                    Enchantment enchantment = Enchantments.resolve(enchantName);
                                    if (enchantment == null) {
                                        continue;
                                    }
                                    int level = enchants.getInt(enchantName);
                                    if (level < 1) {
                                        continue;
                                    }
                                    level = Math.min(level, enchantment.getMaxLevel());
                                    if (!meta.hasEnchant(enchantment) || meta.getEnchantLevel(enchantment) < level) {
                                        meta.addEnchant(enchantment, level, false);
                                    }
                                }
                                item.setItemMeta(meta);
                            }
                        }
                        itemsToGive.add(item);
                    }
                } catch (Exception ignored) {
                    // A broken kit slot is skipped so the rest of the kit still arrives.
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            String savedMat = plugin.getDataManager().getKitLayoutItem(uuid, i);
            if (savedMat != null && !savedMat.equals("AIR")) {
                Material mat = Material.matchMaterial(savedMat);
                if (mat != null) {
                    for (int j = 0; j < itemsToGive.size(); j++) {
                        if (itemsToGive.get(j).getType() == mat) {
                            inventory.setItem(i, itemsToGive.get(j));
                            itemsToGive.remove(j);
                            break;
                        }
                    }
                }
            }
        }

        for (ItemStack item : itemsToGive) {
            inventory.addItem(item);
        }

        ConfigurationSection armorConfig = kitConfig.getConfigurationSection("armor");
        if (armorConfig != null) {
            Material helmetMat = Material.matchMaterial(armorConfig.getString("helmet.material", "AIR"));
            Material chestplateMat = Material.matchMaterial(armorConfig.getString("chestplate.material", "AIR"));
            Material leggingsMat = Material.matchMaterial(armorConfig.getString("leggings.material", "AIR"));
            Material bootsMat = Material.matchMaterial(armorConfig.getString("boots.material", "AIR"));

            ItemStack helmet = helmetMat != null && helmetMat != Material.AIR ? new ItemStack(helmetMat) : null;
            ItemStack chestplate = chestplateMat != null && chestplateMat != Material.AIR ? new ItemStack(chestplateMat) : null;
            ItemStack leggings = leggingsMat != null && leggingsMat != Material.AIR ? new ItemStack(leggingsMat) : null;
            ItemStack boots = bootsMat != null && bootsMat != Material.AIR ? new ItemStack(bootsMat) : null;

            if (chestplate != null && plugin.getDataManager().hasUpgrade(uuid, "protection")) {
                ItemMeta meta = chestplate.getItemMeta();
                if (meta != null) {
                    enchant(meta, "protection", 1, true);
                    chestplate.setItemMeta(meta);
                }
            }

            if (boots != null && plugin.getDataManager().hasUpgrade(uuid, "featherfalling")) {
                ItemMeta meta = boots.getItemMeta();
                if (meta != null) {
                    enchant(meta, "feather_falling", 2, true);
                    boots.setItemMeta(meta);
                }
            }

            inventory.setHelmet(helmet);
            inventory.setChestplate(chestplate);
            inventory.setLeggings(leggings);
            inventory.setBoots(boots);
        }

        player.updateInventory();
    }

    private static void enchant(ItemMeta meta, String name, int level, boolean ignoreLevel) {
        Enchantment enchantment = Enchantments.resolve(name);
        if (enchantment == null || level < 1) {
            return;
        }
        int applied = ignoreLevel ? level : Math.min(level, enchantment.getMaxLevel());
        if (!meta.hasEnchant(enchantment) || meta.getEnchantLevel(enchantment) < applied) {
            meta.addEnchant(enchantment, applied, ignoreLevel);
        }
    }
}

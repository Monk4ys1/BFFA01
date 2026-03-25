package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
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
        if (kitConfig == null) return;

        // Load configured items
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
                            // Check for permanent Sharpness upgrade
                            if (material == Material.IRON_SWORD && plugin.getDataManager().hasUpgrade(uuid, "sharpness")) {
                                meta.addEnchant(Enchantment.SHARPNESS, 1, true);
                            }

                            // Check for permanent Power upgrade
                            if (material == Material.BOW && plugin.getDataManager().hasUpgrade(uuid, "powerbow")) {
                                meta.addEnchant(Enchantment.POWER, 1, true);
                            }

                            // Check for permanent Punch upgrade
                            if (material == Material.BOW && plugin.getDataManager().hasUpgrade(uuid, "punchbow")) {
                                meta.addEnchant(Enchantment.PUNCH, 1, true);
                            }

                            // Check for permanent Knockback 2 Stick
                            if (material == Material.STICK && plugin.getDataManager().hasUpgrade(uuid, "knockback2")) {
                                meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
                            }

                            item.setItemMeta(meta);
                        }

                        ConfigurationSection enchants = invConfig.getConfigurationSection(key + ".enchantments");
                        if (enchants != null) {
                            meta = item.getItemMeta();
                            if (meta != null) {
                                for (String enchantName : enchants.getKeys(false)) {
                                    Enchantment enchantment = Enchantment.getByName(enchantName.toUpperCase());
                                    if (enchantment != null) {
                                        // Don't overwrite if we already applied a stronger upgrade
                                        if (!meta.hasEnchant(enchantment) || meta.getEnchantLevel(enchantment) < enchants.getInt(enchantName)) {
                                            meta.addEnchant(enchantment, enchants.getInt(enchantName), true);
                                        }
                                    }
                                }
                                item.setItemMeta(meta);
                            }
                        }
                        itemsToGive.add(item);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Apply Custom Kit Layout if exists
        boolean layoutUsed = false;
        for (int i = 0; i < 9; i++) {
            String savedMat = plugin.getDataManager().getKitLayoutItem(uuid, i);
            if (savedMat != null && !savedMat.equals("AIR")) {
                Material mat = Material.matchMaterial(savedMat);
                if (mat != null) {
                    // Find the item in our generated items that matches the saved material
                    for (int j = 0; j < itemsToGive.size(); j++) {
                        if (itemsToGive.get(j).getType() == mat) {
                            inventory.setItem(i, itemsToGive.get(j));
                            itemsToGive.remove(j);
                            layoutUsed = true;
                            break;
                        }
                    }
                }
            }
        }

        // If they didn't have a layout (or for remaining unmapped items), just dump them in
        for (ItemStack item : itemsToGive) {
            inventory.addItem(item);
        }

        // Give armor
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

            // Check for Protection 1 Chestplate
            if (chestplate != null && plugin.getDataManager().hasUpgrade(uuid, "protection")) {
                ItemMeta meta = chestplate.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(Enchantment.PROTECTION, 1, true);
                    chestplate.setItemMeta(meta);
                }
            }

            // Check for Feather Falling 2 Boots
            if (boots != null && plugin.getDataManager().hasUpgrade(uuid, "featherfalling")) {
                ItemMeta meta = boots.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(Enchantment.FEATHER_FALLING, 2, true);
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
}

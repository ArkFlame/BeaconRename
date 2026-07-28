package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierCost;
import com.arkflame.flameforge.model.TierDefinition;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MenuItemFactory {
    private static final MaterialResolver MATERIAL_RESOLVER = MaterialResolver.getInstance();

    private static final Material FALLBACK_GRAY_STAINED_GLASS = Material.valueOf("STAINED_GLASS_PANE");
    private static final short GRAY_STAINED_GLASS_DATA = 7;

    private static final String GRAY_STAINED_GLASS_LEGACY = "STAINED_GLASS";

    private MenuItemFactory() {
    }

    public static ItemStack createFiller() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("STAINED_GLASS_PANE", FALLBACK_GRAY_STAINED_GLASS);
        ItemStack item = new ItemStack(material, 1);
        if (material == FALLBACK_GRAY_STAINED_GLASS) {
            item = new ItemStack(FALLBACK_GRAY_STAINED_GLASS, 1, GRAY_STAINED_GLASS_DATA);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createInputPlaceholder() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("BLACK_STAINED_GLASS_PANE", Material.valueOf("STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&8&lInput Item"));
            meta.setLore(Arrays.asList(
                translate("&7Place your item here"),
                translate("&7(Stack size must be exactly 1)")
            ));
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createCatalystPlaceholder() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("CYAN_STAINED_GLASS_PANE", Material.valueOf("STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&b&lCatalyst"));
            meta.setLore(Arrays.asList(
                translate("&7Optional: Place a catalyst"),
                translate("&7to modify outcomes")
            ));
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createWardPlaceholder() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("MAGENTA_STAINED_GLASS_PANE", Material.valueOf("STAINED_GLASS_PANE"));
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&d&lWard"));
            meta.setLore(Arrays.asList(
                translate("&7Optional: Place a ward"),
                translate("&7to protect your item")
            ));
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createTierSelected(TierDefinition tier) {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("NETHER_STAR", Material.DIAMOND);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&6&l" + tier.getId()));
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            List<String> lore = new ArrayList<>();
            lore.add(translate("&7Tier Level: &e" + tier.getTierLevel()));
            lore.add(translate("&aSelected"));
            meta.setLore(lore);
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createTierAvailable(TierDefinition tier) {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("DIAMOND", Material.IRON_INGOT);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&e&l" + tier.getId()));
            List<String> lore = new ArrayList<>();
            lore.add(translate("&7Tier Level: &e" + tier.getTierLevel()));
            lore.add(translate("&aClick to select"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createTierLocked(TierDefinition tier) {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("IRON_BARS", Material.IRON_DOOR);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&7&l" + tier.getId()));
            List<String> lore = new ArrayList<>();
            lore.add(translate("&7Tier Level: &c" + tier.getTierLevel()));
            lore.add(translate("&cLocked - Upgrade station"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createTierUnavailable(TierDefinition tier) {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("COBBLESTONE", Material.COBBLESTONE);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&8&l" + tier.getId()));
            List<String> lore = new ArrayList<>();
            lore.add(translate("&7Tier Level: &8" + tier.getTierLevel()));
            lore.add(translate("&cUnavailable"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createPreviousButton(boolean enabled) {
        Material material = enabled ?
            MATERIAL_RESOLVER.resolveOrDefault("ARROW", Material.ARROW) :
            MATERIAL_RESOLVER.resolveOrDefault("BARRIER", Material.BARRIER);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(enabled ? "&e&lPrevious Page" : "&7&lPrevious Page"));
            meta.setLore(enabled ?
                Collections.singletonList(translate("&7Click to go to previous page")) :
                Collections.singletonList(translate("&cNo previous page"))
            );
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createNextButton(boolean enabled) {
        Material material = enabled ?
            MATERIAL_RESOLVER.resolveOrDefault("ARROW", Material.ARROW) :
            MATERIAL_RESOLVER.resolveOrDefault("BARRIER", Material.BARRIER);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(enabled ? "&e&lNext Page" : "&7&lNext Page"));
            meta.setLore(enabled ?
                Collections.singletonList(translate("&7Click to go to next page")) :
                Collections.singletonList(translate("&cNo next page"))
            );
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createCloseButton() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("BARRIER", Material.BARRIER);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&c&lClose"));
            meta.setLore(Collections.singletonList(translate("&7Click to close the menu")));
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createPityHistoryItem(PlayerForgeState state, String stationId) {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("BOOK", Material.BOOK);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&5&lPity / History"));
            List<String> lore = new ArrayList<>();
            int pity = state != null ? state.getPityCount(stationId) : 0;
            lore.add(translate("&7Current Pity: &d" + pity));
            lore.add(translate("&7Click to view history"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    public static ItemStack createConfirmItem(TierDefinition tier, ChanceTable chanceTable, CostQuote costQuote, boolean canAfford) {
        Material material = canAfford ?
            MATERIAL_RESOLVER.resolveOrDefault("EMERALD_BLOCK", Material.EMERALD_BLOCK) :
            MATERIAL_RESOLVER.resolveOrDefault("REDSTONE_BLOCK", Material.REDSTONE_BLOCK);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(canAfford ? "&a&lConfirm Forge" : "&c&lCannot Afford"));
            List<String> lore = buildConfirmLore(tier, chanceTable, costQuote);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    private static List<String> buildConfirmLore(TierDefinition tier, ChanceTable chanceTable, CostQuote costQuote) {
        List<String> lore = new ArrayList<>();

        if (costQuote != null) {
            BigDecimal xpCost = costQuote.getXpCost();
            BigDecimal moneyCost = costQuote.getMoneyCost();
            boolean xpAffordable = costQuote.isXpAffordable();
            boolean moneyAffordable = costQuote.isMoneyAffordable();

            if (xpCost != null && xpCost.compareTo(BigDecimal.ZERO) > 0) {
                String xpLine = translate("&dXP Cost: &d" + xpCost.toPlainString() + " XP");
                if (!xpAffordable) {
                    xpLine += translate(" &c(Not affordable)");
                }
                lore.add(xpLine);
            }

            if (moneyCost != null && moneyCost.compareTo(BigDecimal.ZERO) > 0) {
                String moneyLine = translate("&aMoney Cost: &a$" + moneyCost.toPlainString());
                if (!moneyAffordable) {
                    moneyLine += translate(" &c(Not affordable)");
                }
                lore.add(moneyLine);
            }
        }

        lore.add(translate("&7&m----------------------------"));
        lore.add(translate("&e&lPossible Outcomes:"));

        if (chanceTable != null) {
            List<ChanceEntry> entries = chanceTable.getEntries();
            for (ChanceEntry entry : entries) {
                BigDecimal percentage = entry.getDisplayPercentage();
                String pctStr = percentage.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
                String outcomeLine = translate("&7- &f" + entry.getOutcomeId() + ": &e" + pctStr + "%");
                lore.add(outcomeLine);
            }
        }

        return lore;
    }

    public static ItemStack createInfoItem() {
        Material material = MATERIAL_RESOLVER.resolveOrDefault("NETHER_STAR", Material.DIAMOND);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate("&6&lFlameForge"));
            List<String> lore = new ArrayList<>();
            lore.add(translate("&7Welcome to the FlameForge"));
            lore.add(translate("&7Place items in the slots below"));
            lore.add(translate("&7Select a tier and confirm to forge"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            try {
                item.setItemMeta(meta);
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    private static String translate(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}

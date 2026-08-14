package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ForgeVariantEligibility {
    private final ItemIdentityService identityService;
    private final TierRepository tierRepository;

    public ForgeVariantEligibility(ItemIdentityService identityService, TierRepository tierRepository) {
        this.identityService = identityService;
        this.tierRepository = tierRepository;
    }

    public boolean isEligible(ItemStack item, ForgeVariant variant) {
        if (item == null || variant == null) {
            return false;
        }
        List<String> applicableGroups = variant.getApplicableGroups();
        if (applicableGroups.isEmpty()) {
            return true;
        }
        Material material = item.getType();
        Optional<String> equipmentCategory = tierRepository.findEquipmentCategory(material);
        for (String group : applicableGroups) {
            if ("ANY".equalsIgnoreCase(group)) {
                return true;
            }
            if (equipmentCategory.isPresent() && equipmentCategory.get().equalsIgnoreCase(group)) {
                return true;
            }
            if (identityService.matchesMaterialGroup(material, group)) {
                return true;
            }
        }
        return false;
    }

    public List<ForgeVariant> eligibleVariants(ItemStack item, List<ForgeVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return Collections.emptyList();
        }
        List<ForgeVariant> result = new ArrayList<>();
        for (ForgeVariant variant : variants) {
            if (isEligible(item, variant)) {
                result.add(variant);
            }
        }
        return Collections.unmodifiableList(result);
    }
}

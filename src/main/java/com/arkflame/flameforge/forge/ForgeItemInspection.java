package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ForgeItemInspection {
    public enum Status {
        READY,
        EMPTY,
        AIR,
        CUSTOM_NAME,
        CUSTOM_LORE,
        CUSTOM_MODEL_DATA,
        FOREIGN_PERSISTENT_DATA,
        CURSED,
        MAX_TIER,
        NEXT_TIER_MISSING,
        NEXT_TIER_DISABLED,
        STATION_TIER_BLOCKED,
        TIER_PERMISSION_REQUIRED,
        DENIED_MATERIAL,
        DENIED_GROUP,
        NO_ELIGIBLE_VARIANTS,
        INVALID_IDENTITY
    }

    private final ItemIdentityCodec codec;
    private final ItemIdentityService identityService;
    private final AttributeBridge attributeBridge;
    private final TierRepository tierRepository;
    private final ForgeVariantEligibility variantEligibility;

    private Method pdcGetMethod;
    private Method pdcKeysMethod;
    private Class<?> namespacedKeyClass;
    private Class<?> pdcTypeClass;

    public ForgeItemInspection(ItemIdentityCodec codec, ItemIdentityService identityService,
                               AttributeBridge attributeBridge, TierRepository tierRepository,
                               ForgeVariantEligibility variantEligibility) {
        this.codec = codec;
        this.identityService = identityService;
        this.attributeBridge = attributeBridge;
        this.tierRepository = tierRepository;
        this.variantEligibility = variantEligibility;
        initReflection();
    }

    private void initReflection() {
        try {
            namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            pdcTypeClass = Class.forName("org.bukkit.persistence.PersistentDataType");
            Class<?> pdcClass = Class.forName("org.bukkit.persistence.PersistentDataContainer");
            pdcGetMethod = pdcClass.getMethod("get", namespacedKeyClass, pdcTypeClass);
            pdcKeysMethod = pdcClass.getMethod("getKeys");
        } catch (Exception e) {
        }
    }

    public InspectionResult inspect(Player player, PlayerForgeState session, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new InspectionResult(Status.AIR, null);
        }

        if (item.getAmount() == 0) {
            return new InspectionResult(Status.EMPTY, null);
        }

        ItemIdentityService.ForgeIdentityRead identityRead = identityService.readForgeIdentity(item);
        ItemIdentityCodec.Identity identity;

        switch (identityRead.getStatus()) {
            case NONE:
                identity = synthesizeFreshIdentity(item);
                break;
            case INVALID:
                return new InspectionResult(Status.INVALID_IDENTITY, null);
            case VALID:
            default:
                identity = identityRead.getIdentity();
                break;
        }

        final boolean freshUnowned = identityRead.getStatus() == ItemIdentityService.ForgeIdentityStatus.NONE;
        if (identityRead.getStatus() == ItemIdentityService.ForgeIdentityStatus.VALID && identity.isCursed()) {
            return new InspectionResult(Status.CURSED, identity);
        }

        int currentTier = identity.getCurrentTier();
        Material material = resolveForgeMaterial(identity, item);
        TierDefinition currentTierDef = tierRepository.findForMaterialAndLevel(material, currentTier).orElse(null);

        if (currentTierDef != null && !currentTierDef.isEnabled()) {
            return new InspectionResult(Status.NEXT_TIER_DISABLED, identity);
        }

        TierDefinition targetTierDef = tierRepository.findExactNext(material, currentTier).orElse(null);

        if (targetTierDef == null) {
            if (currentTier >= tierRepository.maxLevelFor(material)) {
                return new InspectionResult(Status.MAX_TIER, identity);
            }
            return new InspectionResult(Status.NEXT_TIER_MISSING, identity);
        }

        if (!targetTierDef.isEnabled()) {
            return new InspectionResult(Status.NEXT_TIER_DISABLED, identity);
        }

        if (currentTier == 0 && freshUnowned) {
            Status nameStatus = checkTier0CustomName(item, identity);
            if (nameStatus != null) {
                return new InspectionResult(nameStatus, identity);
            }

            Status loreStatus = checkTier0CustomLore(item, identity);
            if (loreStatus != null) {
                return new InspectionResult(loreStatus, identity);
            }

            Status modelStatus = checkTier0CustomModelData(item, identity);
            if (modelStatus != null) {
                return new InspectionResult(modelStatus, identity);
            }

            Status pdcStatus = checkTier0ForeignPdc(item, identity);
            if (pdcStatus != null) {
                return new InspectionResult(pdcStatus, identity);
            }
        }

        if (targetTierDef != null) {
            List<String> deniedMaterials = targetTierDef.getDeniedMaterials();
            if (!deniedMaterials.isEmpty()) {
                Material mat = item.getType();
                String matName = mat.name();
                for (String denied : deniedMaterials) {
                    if (denied.equalsIgnoreCase(matName) || denied.equalsIgnoreCase("*")) {
                        return new InspectionResult(Status.DENIED_MATERIAL, identity);
                    }
                }
            }

            List<String> allowedGroups = targetTierDef.getAllowedGroups();
            if (!allowedGroups.isEmpty()) {
                Optional<String> groupOpt = identityService.getMaterialGroup(item.getType());
                if (groupOpt.isPresent()) {
                    String group = groupOpt.get();
                    boolean allowed = allowedGroups.stream()
                        .anyMatch(g -> g.equalsIgnoreCase("ANY") || identityService.matchesMaterialGroup(item.getType(), g));
                    if (!allowed) {
                        return new InspectionResult(Status.DENIED_GROUP, identity);
                    }
                }
            }

            List<com.arkflame.flameforge.model.ForgeVariant> variants = targetTierDef.getVariants();
            if (variants == null || variants.isEmpty()) {
                return new InspectionResult(Status.NO_ELIGIBLE_VARIANTS, identity);
            }
            List<com.arkflame.flameforge.model.ForgeVariant> eligible = variantEligibility.eligibleVariants(item, variants);
            if (eligible.isEmpty()) {
                return new InspectionResult(Status.NO_ELIGIBLE_VARIANTS, identity);
            }
        }

        if (session == null) {
            return new InspectionResult(Status.READY, identity);
        }

        String stationId = session.getActiveStationId();

        Optional<StationProfile> profileOpt = resolveStationProfile(stationId);
        if (profileOpt.isPresent()) {
            StationProfile profile = profileOpt.get();
            int maxTier = profile.getMaxTierUnlocked();
            if (maxTier >= 0 && targetTierDef.getLevel() > maxTier) {
                return new InspectionResult(Status.STATION_TIER_BLOCKED, identity);
            }

            List<String> requiredPerms = profile.getRequiredPermissions();
            for (String perm : requiredPerms) {
                if (!player.hasPermission(perm)) {
                    return new InspectionResult(Status.TIER_PERMISSION_REQUIRED, identity);
                }
            }
        }

        return new InspectionResult(Status.READY, identity);
    }

    private ItemIdentityCodec.Identity synthesizeFreshIdentity(ItemStack item) {
        return ItemIdentityCodec.Identity.empty()
            .withForgeId(java.util.UUID.randomUUID())
            .withBaseMaterial(item.getType().name())
            .withBaseDisplayName(identityService.defaultBaseDisplayName(item.getType()));
    }

    private Status checkTier0CustomName(ItemStack item, ItemIdentityCodec.Identity identity) {
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        if (identity.getCurrentTier() > 0) {
            return null;
        }
        return Status.CUSTOM_NAME;
    }

    private Status checkTier0CustomLore(ItemStack item, ItemIdentityCodec.Identity identity) {
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return null;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        for (String line : lore) {
            if (line != null && !line.isEmpty()) {
                if (identity.getCurrentTier() > 0) {
                    continue;
                }
                return Status.CUSTOM_LORE;
            }
        }
        return null;
    }

    private Status checkTier0CustomModelData(ItemStack item, ItemIdentityCodec.Identity identity) {
        if (!Boolean.TRUE.equals(attributeBridge.isModernCustomModelDataAvailable())) {
            return null;
        }
        Optional<Integer> cmd = attributeBridge.getCustomModelData(item);
        if (cmd.isPresent() && identity.getCurrentTier() == 0) {
            return Status.CUSTOM_MODEL_DATA;
        }
        return null;
    }

    private Status checkTier0ForeignPdc(ItemStack item, ItemIdentityCodec.Identity identity) {
        if (!Boolean.TRUE.equals(attributeBridge.isModernCustomModelDataAvailable())) {
            return null;
        }
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }
            Method getPdcMethod = ItemMeta.class.getMethod("getPersistentDataContainer");
            Object pdc = getPdcMethod.invoke(meta);
            if (pdc == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            java.util.Set<Object> keys = (java.util.Set<Object>) pdcKeysMethod.invoke(pdc);
            if (keys == null || keys.isEmpty()) {
                return null;
            }
            boolean hasFlameforge = false;
            boolean hasForeign = false;
            for (Object key : keys) {
                String keyStr = key.toString();
                if (keyStr.startsWith("flameforge:")) {
                    hasFlameforge = true;
                } else {
                    hasForeign = true;
                }
            }
            if (hasForeign && identity.getCurrentTier() == 0 && !hasFlameforge) {
                return Status.FOREIGN_PERSISTENT_DATA;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Material resolveForgeMaterial(ItemIdentityCodec.Identity identity, ItemStack item) {
        String baseMaterial = identity != null ? identity.getBaseMaterial() : null;
        if (baseMaterial != null && !baseMaterial.isEmpty()) {
            Material resolved = Material.matchMaterial(baseMaterial);
            if (resolved != null) {
                return resolved;
            }
        }
        return item.getType();
    }

    private Optional<StationProfile> resolveStationProfile(String stationId) {
        if (stationId == null || stationId.isEmpty()) {
            return Optional.empty();
        }
        return tierRepository.findExtra(stationId)
            .map(extra -> StationProfile.of(stationId, stationId, -1, Collections.emptyList()));
    }

    public static final class InspectionResult {
        private final Status status;
        private final ItemIdentityCodec.Identity identity;

        public InspectionResult(Status status, ItemIdentityCodec.Identity identity) {
            this.status = status;
            this.identity = identity;
        }

        public Status getStatus() { return status; }
        public ItemIdentityCodec.Identity getIdentity() { return identity; }
        public boolean isReady() { return status == Status.READY; }
    }
}

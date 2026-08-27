package com.arkflame.flameforge.item;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ForgeExampleItemService {

    private static final String[] WEAPON_MATERIALS = {"NETHERITE_SWORD", "DIAMOND_SWORD", "IRON_SWORD"};
    private static final String[] ARMOR_MATERIALS = {"NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE", "IRON_CHESTPLATE"};
    private static final String[] SHIELD_MATERIALS = {"SHIELD"};
    private static final String[] AMULET_MATERIALS = {"NETHERITE_INGOT", "DIAMOND", "EMERALD", "WOOL"};

    public enum Status {
        SUCCESS,
        MATERIAL_UNAVAILABLE,
        MATERIAL_CATEGORY_MISMATCH,
        VARIANT_INELIGIBLE,
        MUTATION_FAILED
    }

    public static final class ExampleResult {
        private final Status status;
        private final Optional<ItemStack> item;
        private final Optional<Material> material;
        private final Optional<String> actualCategoryId;
        private final Optional<String> requiredCategoryId;

        private ExampleResult(Status status, ItemStack item, Material material,
                              String actualCategoryId, String requiredCategoryId) {
            this.status = status;
            this.item = Optional.ofNullable(item);
            this.material = Optional.ofNullable(material);
            this.actualCategoryId = Optional.ofNullable(actualCategoryId);
            this.requiredCategoryId = Optional.ofNullable(requiredCategoryId);
        }

        public static ExampleResult success(ItemStack item, Material material) {
            return new ExampleResult(Status.SUCCESS, item, material, null, null);
        }

        public static ExampleResult materialUnavailable(Material material) {
            return new ExampleResult(Status.MATERIAL_UNAVAILABLE, null, material, null, null);
        }

        public static ExampleResult categoryMismatch(Material material, String actualCategoryId,
                                                     String requiredCategoryId) {
            return new ExampleResult(Status.MATERIAL_CATEGORY_MISMATCH, null, material,
                actualCategoryId, requiredCategoryId);
        }

        public static ExampleResult variantIneligible(Material material) {
            return new ExampleResult(Status.VARIANT_INELIGIBLE, null, material, null, null);
        }

        public static ExampleResult mutationFailed(Material material) {
            return new ExampleResult(Status.MUTATION_FAILED, null, material, null, null);
        }

        public Status getStatus() {
            return status;
        }

        public Optional<ItemStack> getItem() {
            return item;
        }

        public Optional<Material> getMaterial() {
            return material;
        }

        public Optional<String> getActualCategoryId() {
            return actualCategoryId;
        }

        public Optional<String> getRequiredCategoryId() {
            return requiredCategoryId;
        }

        private static boolean itemsEqual(Optional<ItemStack> left, Optional<ItemStack> right) {
            if (!left.isPresent() || !right.isPresent()) {
                return left.isPresent() == right.isPresent();
            }
            ItemStack leftItem = left.get();
            ItemStack rightItem = right.get();
            if (leftItem.getType() != rightItem.getType()
                    || leftItem.getAmount() != rightItem.getAmount()
                    || leftItem.getDurability() != rightItem.getDurability()) {
                return false;
            }
            return Objects.equals(displayName(leftItem), displayName(rightItem));
        }

        private static String displayName(ItemStack item) {
            if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return "";
        }

        private static int itemHashCode(Optional<ItemStack> value) {
            if (!value.isPresent()) {
                return 0;
            }
            ItemStack item = value.get();
            int result = item.getType().hashCode();
            result = 31 * result + item.getAmount();
            result = 31 * result + (int) item.getDurability();
            result = 31 * result + displayName(item).hashCode();
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ExampleResult)) {
                return false;
            }
            ExampleResult that = (ExampleResult) o;
            return status == that.status
                && itemsEqual(item, that.item)
                && material.equals(that.material)
                && actualCategoryId.equals(that.actualCategoryId)
                && requiredCategoryId.equals(that.requiredCategoryId);
        }

        @Override
        public int hashCode() {
            int result = status.hashCode();
            result = 31 * result + itemHashCode(item);
            result = 31 * result + material.hashCode();
            result = 31 * result + actualCategoryId.hashCode();
            result = 31 * result + requiredCategoryId.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ExampleResult{status=" + status
                + ", item=" + (item.isPresent() ? item.get().getType().name() : "none")
                + ", material=" + material.map(Material::name).orElse("none")
                + ", actualCategoryId=" + actualCategoryId.orElse("none")
                + ", requiredCategoryId=" + requiredCategoryId.orElse("none") + "}";
        }
    }

    private final TierRepository tierRepository;
    private final MaterialResolver materialResolver;
    private final ForgeVariantEligibility variantEligibility;
    private final ItemIdentityService identityService;
    private final ItemMutationService mutationService;

    public ForgeExampleItemService(TierRepository tierRepository, MaterialResolver materialResolver,
                                   ForgeVariantEligibility variantEligibility,
                                   ItemIdentityService identityService,
                                   ItemMutationService mutationService) {
        this.tierRepository = Objects.requireNonNull(tierRepository, "tierRepository");
        this.materialResolver = Objects.requireNonNull(materialResolver, "materialResolver");
        this.variantEligibility = Objects.requireNonNull(variantEligibility, "variantEligibility");
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.mutationService = Objects.requireNonNull(mutationService, "mutationService");
    }

    public Optional<Material> resolveDefaultMaterial(TierDefinition tier) {
        if (tier == null) {
            return Optional.empty();
        }
        String[] candidates;
        Optional<String> categoryId = tierRepository.getEquipmentCatalog().categoryIdForTier(tier.getId());
        if (!categoryId.isPresent() || "weapon".equalsIgnoreCase(categoryId.get())) {
            candidates = WEAPON_MATERIALS;
        } else if ("armor".equalsIgnoreCase(categoryId.get())) {
            candidates = ARMOR_MATERIALS;
        } else if ("shield".equalsIgnoreCase(categoryId.get())) {
            candidates = SHIELD_MATERIALS;
        } else {
            candidates = AMULET_MATERIALS;
        }
        return materialResolver.get(candidates).map(MaterialResolver.ResolvedMaterial::getMaterial);
    }

    public ExampleResult create(TierDefinition tier, ForgeVariant variant, Material material, UUID forgeId) {
        if (tier == null || variant == null) {
            return ExampleResult.mutationFailed(material);
        }
        if (material == null || material == Material.AIR) {
            return ExampleResult.materialUnavailable(material);
        }

        Optional<String> tierCategoryId = tierRepository.getEquipmentCatalog().categoryIdForTier(tier.getId());
        if (tierCategoryId.isPresent()) {
            String materialCategoryId = tierRepository.getEquipmentCatalog()
                .categoryForMaterial(material.name()).getId();
            if (!tierCategoryId.get().equalsIgnoreCase(materialCategoryId)) {
                return ExampleResult.categoryMismatch(material, materialCategoryId, tierCategoryId.get());
            }
        }

        ItemStack item = new ItemStack(material, 1);
        if (!variantEligibility.isEligible(item, variant)) {
            return ExampleResult.variantIneligible(material);
        }

        UUID actualForgeId = forgeId != null ? forgeId : UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(actualForgeId)
            .withBaseMaterial(material.name())
            .withBaseDisplayName(identityService.defaultBaseDisplayName(material));

        ItemMutationService.MutationResult mutation =
            mutationService.mutateSuccess(item, tier, variant, identity, actualForgeId);
        if (mutation.isSuccess() && mutation.getResult() != null) {
            return ExampleResult.success(mutation.getResult(), material);
        }
        return ExampleResult.mutationFailed(material);
    }

    public ExampleResult createDefault(TierDefinition tier, ForgeVariant variant, UUID forgeId) {
        Optional<Material> material = resolveDefaultMaterial(tier);
        if (!material.isPresent() || material.get() == Material.AIR) {
            return ExampleResult.materialUnavailable(null);
        }
        return create(tier, variant, material.get(), forgeId);
    }
}

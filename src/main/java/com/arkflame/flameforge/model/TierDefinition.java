package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TierDefinition {
    private final String id;
    private final int level;
    private final boolean enabled;
    private final String permission;
    private final TierDisplay display;
    private final long cooldownSeconds;
    private final List<String> allowedGroups;
    private final List<String> deniedMaterials;
    private final TierRequirements requirements;
    private final TierChances chances;
    private final BreakPolicy breakPolicy;
    private final CurseDefinition curseDefinition;
    private final ForgeAnimationProfile animationProfile;
    private final List<ForgeVariant> variants;

    public TierDefinition(String id, int level, boolean enabled, String permission,
                         TierDisplay display, long cooldownSeconds,
                         List<String> allowedGroups, List<String> deniedMaterials,
                         TierRequirements requirements, TierChances chances,
                         BreakPolicy breakPolicy, CurseDefinition curseDefinition,
                         ForgeAnimationProfile animationProfile, List<ForgeVariant> variants) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.level = level;
        this.enabled = enabled;
        this.permission = permission;
        this.display = display;
        this.cooldownSeconds = cooldownSeconds;
        this.allowedGroups = allowedGroups != null ? Collections.unmodifiableList(new java.util.ArrayList<>(allowedGroups)) : Collections.emptyList();
        this.deniedMaterials = deniedMaterials != null ? Collections.unmodifiableList(new java.util.ArrayList<>(deniedMaterials)) : Collections.emptyList();
        this.requirements = requirements != null ? requirements : new TierRequirements(TierRequirements.Combine.ALL, null, null, null);
        this.chances = chances != null ? chances : new TierChances(0, 0, 0);
        this.breakPolicy = breakPolicy != null ? breakPolicy : BreakPolicy.none();
        this.curseDefinition = curseDefinition;
        this.animationProfile = animationProfile;
        this.variants = variants != null ? Collections.unmodifiableList(new java.util.ArrayList<>(variants)) : Collections.emptyList();
    }

    public String getId() { return id; }
    public int getLevel() { return level; }
    public int getTierLevel() { return level; }
    public boolean isEnabled() { return enabled; }
    public String getPermission() { return permission; }
    public TierDisplay getDisplay() { return display; }
    public long getCooldownSeconds() { return cooldownSeconds; }
    public List<String> getAllowedGroups() { return allowedGroups; }
    public List<String> getDeniedMaterials() { return deniedMaterials; }
    public TierRequirements getRequirements() { return requirements; }
    public TierRequirements getCost() { return requirements; }
    public TierChances getChances() { return chances; }
    public BreakPolicy getBreakPolicy() { return breakPolicy; }
    public CurseDefinition getCurseDefinition() { return curseDefinition; }
    public ForgeAnimationProfile getAnimationProfile() { return animationProfile; }
    public List<ForgeVariant> getVariants() { return variants; }

    public int getSuccessAnimationDuration() {
        return animationProfile != null ? animationProfile.getDurationTicks() : 0;
    }

    public int getFailAnimationDuration() {
        return animationProfile != null ? animationProfile.getDurationTicks() : 0;
    }

    public List<OutcomeDefinition> getOutcomes() {
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierDefinition)) return false;
        TierDefinition that = (TierDefinition) o;
        return level == that.level && enabled == that.enabled &&
               cooldownSeconds == that.cooldownSeconds &&
               Objects.equals(id, that.id) &&
               Objects.equals(permission, that.permission) &&
               Objects.equals(display, that.display) &&
               Objects.equals(allowedGroups, that.allowedGroups) &&
               Objects.equals(deniedMaterials, that.deniedMaterials) &&
               Objects.equals(requirements, that.requirements) &&
               Objects.equals(chances, that.chances) &&
               Objects.equals(breakPolicy, that.breakPolicy) &&
               Objects.equals(curseDefinition, that.curseDefinition) &&
               Objects.equals(animationProfile, that.animationProfile) &&
               Objects.equals(variants, that.variants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level, enabled, permission, display, cooldownSeconds,
                           allowedGroups, deniedMaterials, requirements, chances,
                           breakPolicy, curseDefinition, animationProfile, variants);
    }

    @Override
    public String toString() {
        return "TierDefinition{id=" + id + ", level=" + level + ", enabled=" + enabled +
               ", permission=" + permission + ", display=" + display +
               ", cooldownSeconds=" + cooldownSeconds + ", allowedGroups=" + allowedGroups +
               ", deniedMaterials=" + deniedMaterials + ", requirements=" + requirements +
               ", chances=" + chances + ", breakPolicy=" + breakPolicy +
               ", curseDefinition=" + curseDefinition + ", animationProfile=" + animationProfile +
               ", variants=" + variants + "}";
    }

    public static final class TierDisplay {
        private final String name;
        private final List<String> lore;
        private final boolean glow;
        private final String icon;

        public TierDisplay(String name, List<String> lore, boolean glow, String icon) {
            this.name = name != null ? name : "";
            this.lore = lore != null ? Collections.unmodifiableList(new java.util.ArrayList<>(lore)) : Collections.emptyList();
            this.glow = glow;
            this.icon = icon;
        }

        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public boolean isGlow() { return glow; }
        public String getIcon() { return icon; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TierDisplay)) return false;
            TierDisplay that = (TierDisplay) o;
            return glow == that.glow &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(lore, that.lore) &&
                   Objects.equals(icon, that.icon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, lore, glow, icon);
        }

        @Override
        public String toString() {
            return "TierDisplay{name=" + name + ", lore=" + lore + ", glow=" + glow + ", icon=" + icon + "}";
        }
    }
}

package com.arkflame.flameforge.command;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.menu.MenuInputReturnService;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextBridge;

import java.util.List;
import java.util.Set;

public final class ReadyServices {

    private final ConfigService configService;
    private final TierRepository tierRepository;
    private final ForgeStationService stationService;
    private final StationRepository stationRepository;
    private final PlayerStateRepository playerStateRepository;
    private final ForgeService forgeService;
    private final ForgeAccessService accessService;
    private final TextBridge textBridge;
    private final MessageService messageService;
    private final MaterialResolver materialResolver;
    private final TeleportBridge teleportBridge;
    private final ItemIdentityCodec itemIdentityCodec;
    private final ItemIdentityService itemIdentityService;
    private final ForgeItemPolicy forgeItemPolicy;
    private final PotionEffectResolver potionEffectResolver;
    private final EquipmentBridge equipmentBridge;
    private final ForgePowerService forgePowerService;
    private final ForgeVariantEligibility forgeVariantEligibility;
    private final MenuInputReturnService menuInputReturnService;
    private final ItemMutationService itemMutationService;

    public ReadyServices(ConfigService configService, TierRepository tierRepository,
                         ForgeStationService stationService, StationRepository stationRepository,
                         PlayerStateRepository playerStateRepository, ForgeService forgeService,
                         ForgeAccessService accessService,
                         TextBridge textBridge, MessageService messageService,
                         MaterialResolver materialResolver, TeleportBridge teleportBridge,
                         ItemIdentityCodec itemIdentityCodec, ItemIdentityService itemIdentityService,
                         ForgeItemPolicy forgeItemPolicy, PotionEffectResolver potionEffectResolver,
                         EquipmentBridge equipmentBridge, ForgePowerService forgePowerService,
                         ForgeVariantEligibility forgeVariantEligibility,
                         MenuInputReturnService menuInputReturnService,
                         ItemMutationService itemMutationService) {
        this.configService = configService;
        this.tierRepository = tierRepository;
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.playerStateRepository = playerStateRepository;
        this.forgeService = forgeService;
        this.accessService = accessService;
        this.textBridge = textBridge;
        this.messageService = messageService;
        this.materialResolver = materialResolver;
        this.teleportBridge = teleportBridge;
        this.itemIdentityCodec = itemIdentityCodec;
        this.itemIdentityService = itemIdentityService;
        this.forgeItemPolicy = forgeItemPolicy;
        this.potionEffectResolver = potionEffectResolver;
        this.equipmentBridge = equipmentBridge;
        this.forgePowerService = forgePowerService;
        this.forgeVariantEligibility = forgeVariantEligibility;
        this.menuInputReturnService = menuInputReturnService;
        this.itemMutationService = itemMutationService;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public TierRepository getTierRepository() {
        return tierRepository;
    }

    public ForgeStationService getStationService() {
        return stationService;
    }

    public StationRepository getStationRepository() {
        return stationRepository;
    }

    public PlayerStateRepository getPlayerStateRepository() {
        return playerStateRepository;
    }

    public ForgeService getForgeService() {
        return forgeService;
    }

    public ForgeAccessService getAccessService() {
        return accessService;
    }

    public TextBridge getTextBridge() {
        return textBridge;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public MaterialResolver getMaterialResolver() {
        return materialResolver;
    }

    public TeleportBridge getTeleportBridge() {
        return teleportBridge;
    }

    public ItemIdentityCodec getItemIdentityCodec() {
        return itemIdentityCodec;
    }

    public ItemIdentityService getItemIdentityService() {
        return itemIdentityService;
    }

    public ForgeItemPolicy getForgeItemPolicy() {
        return forgeItemPolicy;
    }

    public PotionEffectResolver getPotionEffectResolver() {
        return potionEffectResolver;
    }

    public EquipmentBridge getEquipmentBridge() {
        return equipmentBridge;
    }

    public ForgePowerService getForgePowerService() {
        return forgePowerService;
    }

    public ForgeVariantEligibility getForgeVariantEligibility() {
        return forgeVariantEligibility;
    }

    public MenuInputReturnService getMenuInputReturnService() {
        return menuInputReturnService;
    }

    public ItemMutationService getItemMutationService() {
        return itemMutationService;
    }

    public List<String> getTierIds() {
        return configService.getAllTiers().stream()
                .map(t -> t.getId())
                .collect(java.util.stream.Collectors.toList());
    }

    public Set<String> getProfileIds() {
        return configService.getCurrentSnapshot().getStationProfileIds();
    }

    public List<String> getMaterialAliases() {
        return new java.util.ArrayList<>(materialResolver.getAliases().keySet());
    }
}

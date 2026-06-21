package autismclient.modules;

import autismclient.mixin.accessor.AutismItemStackRenderStateAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GoldenLeverModule extends PackModule {
    public static final int GOLD_TINT = 0xFFFFD24A;
    private static volatile boolean active;
    private static volatile boolean femaleBody;
    private static volatile boolean femaleBodySelfOnly;
    private static volatile boolean femaleBodyOnlyOthers;
    private static volatile boolean femaleBodyCustomPlayers;
    private static volatile boolean femaleBodyCustomIncludeSelf;
    private static volatile Set<String> femaleBodyPlayerNames = Set.of();

    public GoldenLeverModule() {
        super("golden-lever", "Golden Lever", PackModuleCategory.MISC, "Renames and recolors vanilla levers.");
        //? if >=1.21.9 {
        this.option(PackModuleOption.bool("female-body", "Female Body", false).group("Appearance").description("Shape player bodies."));
        //?} else {
        /*this.option(PackModuleOption.bool("female-body", "Female Body", false).group("Appearance").description("Shape player bodies.").lockedNote("1.21.9+ only"));*/
        //?}
        this.option(PackModuleOption.bool("female-body-self-only", "Self Only", true).group("Appearance").description("Only affect you.").visible(module -> Boolean.parseBoolean(module.value("female-body"))));
        this.option(PackModuleOption.bool("female-body-only-others", "Only Others", false).group("Appearance").description("Only affect others.").visible(module -> Boolean.parseBoolean(module.value("female-body")) && !Boolean.parseBoolean(module.value("female-body-self-only")) && !Boolean.parseBoolean(module.value("female-body-custom-players"))));
        this.option(PackModuleOption.bool("female-body-custom-players", "Custom Players", false).group("Appearance").description("Use selected players.").visible(module -> Boolean.parseBoolean(module.value("female-body")) && !Boolean.parseBoolean(module.value("female-body-self-only"))));
        this.option(PackModuleOption.bool("female-body-custom-include-self", "Include Self", true).group("Appearance").description("Also include you.").visible(module -> Boolean.parseBoolean(module.value("female-body")) && !Boolean.parseBoolean(module.value("female-body-self-only")) && Boolean.parseBoolean(module.value("female-body-custom-players"))));
        this.option(PackModuleOption.stringList("female-body-player-names", "Players", "").group("Appearance").description("Choose player names.").playerNameList().visible(module -> Boolean.parseBoolean(module.value("female-body")) && !Boolean.parseBoolean(module.value("female-body-self-only")) && Boolean.parseBoolean(module.value("female-body-custom-players"))));
        active = isEnabled();
        this.refreshFemaleBodySettings();
    }

    @Override
    public void onEnable() {
        active = true;
        PackModuleRenderUtil.refreshWorldRenderer();
    }

    @Override
    public void onDisable() {
        active = false;
        PackModuleRenderUtil.refreshWorldRenderer();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if (optionId != null && optionId.startsWith("female-body")) {
            this.refreshFemaleBodySettings();
        }
    }

    @Override
    protected void onSettingsReset() {
        femaleBody = false;
        femaleBodySelfOnly = true;
        femaleBodyOnlyOthers = false;
        femaleBodyCustomPlayers = false;
        femaleBodyCustomIncludeSelf = true;
        femaleBodyPlayerNames = Set.of();
    }

    public static boolean isFemaleBodyActive() {
        return active && femaleBody && !PackHideState.isHardLocked();
    }

    public static boolean shouldApplyFemaleBody(int entityId) {
        if (!isFemaleBodyActive()) {
            return false;
        } else if (MC.player == null) {
            return false;
        } else {
            boolean self = MC.player.getId() == entityId;
            if (femaleBodySelfOnly) {
                return self;
            } else if (femaleBodyCustomPlayers) {
                if (self) {
                    return femaleBodyCustomIncludeSelf;
                } else if (!femaleBodyPlayerNames.isEmpty() && MC.level != null) {
                    Entity entity = MC.level.getEntity(entityId);
                    if (entity instanceof Player player) {
                        if (player.getGameProfile() != null && player.getGameProfile().name() != null) {
                            return femaleBodyPlayerNames.contains(normalizePlayerName(player.getGameProfile().name()));
                        }
                    }
                    return false;
                } else {
                    return false;
                }
            } else {
                return !femaleBodyOnlyOthers || !self;
            }
        }
    }

    private void refreshFemaleBodySettings() {
        femaleBody = this.bool("female-body");
        femaleBodySelfOnly = this.bool("female-body-self-only");
        femaleBodyOnlyOthers = this.bool("female-body-only-others");
        femaleBodyCustomPlayers = this.bool("female-body-custom-players");
        femaleBodyCustomIncludeSelf = this.bool("female-body-custom-include-self");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String entry : this.list("female-body-player-names")) {
            String normalized = normalizePlayerName(entry);
            if (!normalized.isEmpty()) {
                names.add(normalized);
            }
        }
        femaleBodyPlayerNames = Set.copyOf(names);
    }

    private static String normalizePlayerName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isStylingActive() {
        return active;
    }

    public static boolean shouldStyle(ItemStack stack) {
        return isActive() && stack != null && stack.is(Items.LEVER);
    }

    public static boolean shouldStyle(BlockState state) {
        return isActive() && state != null && state.is(Blocks.LEVER);
    }

    public static Component leverName() {
        return Component.literal("Golden Lever").withStyle(ChatFormatting.GOLD);
    }

    public static void tintItemStackRenderState(ItemStackRenderState output) {
        if (output == null) return;
        AutismItemStackRenderStateAccessor accessor = (AutismItemStackRenderStateAccessor) output;
        ItemStackRenderState.LayerRenderState[] layers = accessor.autism$getLayers();
        int count = Math.min(accessor.autism$getActiveLayerCount(), layers.length);
        for (int i = 0; i < count; i++) {
            tintLayer(layers[i]);
        }
    }

    private static void tintLayer(ItemStackRenderState.LayerRenderState layer) {
    }

    private static boolean isActive() {
        return active;
    }
}

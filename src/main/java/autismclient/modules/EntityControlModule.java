package autismclient.modules;

import autismclient.modules.PackModuleOption.Type;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

public final class EntityControlModule extends PackModule {
    private static final String DEFAULT_ENTITIES = String.join("|",
        "minecraft:pig", "minecraft:strider", "minecraft:horse", "minecraft:donkey", "minecraft:mule",
        "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:camel", "minecraft:camel_husk",
        "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat", "minecraft:jungle_boat",
        "minecraft:acacia_boat", "minecraft:cherry_boat", "minecraft:dark_oak_boat", "minecraft:pale_oak_boat",
        "minecraft:mangrove_boat", "minecraft:bamboo_raft", "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat",
        "minecraft:birch_chest_boat", "minecraft:jungle_chest_boat", "minecraft:acacia_chest_boat",
        "minecraft:cherry_chest_boat", "minecraft:dark_oak_chest_boat", "minecraft:pale_oak_chest_boat",
        "minecraft:mangrove_chest_boat", "minecraft:bamboo_chest_raft", "minecraft:nautilus",
        "minecraft:zombie_nautilus", "minecraft:happy_ghast");

    private static EntityControlModule instance;
    private final Set<String> selectedEntities = new HashSet<>();
    private String selectedEntitiesValue = "";
    private int antiKickTicks;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean restorePacketPending;
    private boolean sendingSyntheticPacket;

    public EntityControlModule() {
        super("entity-control", "Entity Control", PackModuleCategory.MOVEMENT, "Controls selected rideable entities.");
        instance = this;
        this.option(PackModuleOption.registryList(Type.ENTITY_TYPE_LIST, "entities", "Entities", DEFAULT_ENTITIES).group("Control").description("Choose controlled mounts."));
        this.option(PackModuleOption.bool("spoof-saddle", "Spoof Saddle", true).group("Control").description("Control without saddles."));
        this.option(PackModuleOption.bool("max-jump", "Max Jump", true).group("Control").description("Always charge fully."));
        this.option(PackModuleOption.bool("lock-yaw", "Lock Yaw", true).group("Control").description("Match your view."));
        this.option(PackModuleOption.bool("cancel-server-packets", "Cancel Server Packets", true).group("Control").description("Ignore server corrections."));
        this.option(PackModuleOption.builder(Type.DOUBLE, "horizontal-speed", "Horizontal Speed", "10.0").range(0.0, 100.0).sliderRange(0.0, 50.0).step(0.5).group("Speed").visible(module -> module.bool("speed")).description("Sets horizontal speed.").build());
        this.option(PackModuleOption.bool("speed", "Speed", false).group("Speed").description("Boost mount speed."));
        this.option(PackModuleOption.bool("only-on-ground", "Only Ground", false).group("Speed").visible(module -> module.bool("speed")).description("Require ground contact."));
        this.option(PackModuleOption.bool("in-water", "In Water", true).group("Speed").visible(module -> module.bool("speed")).description("Allow water speed."));
        this.option(PackModuleOption.bool("fly", "Fly", false).group("Flight").description("Enable mount flight."));
        this.option(PackModuleOption.builder(Type.DOUBLE, "vertical-speed", "Vertical Speed", "6.0").range(0.0, 100.0).sliderRange(0.0, 20.0).step(0.5).group("Flight").visible(module -> module.bool("fly")).description("Sets vertical speed.").build());
        this.option(PackModuleOption.builder(Type.DOUBLE, "fall-speed", "Fall Speed", "0.0").range(0.0, 100.0).sliderRange(0.0, 20.0).step(0.25).group("Flight").visible(module -> module.bool("fly")).description("Sets downward drift.").build());
        this.option(PackModuleOption.bool("anti-kick", "Anti Kick", true).group("Flight").visible(module -> module.bool("fly")).description("Reduce flight kicks."));
        this.option(PackModuleOption.integer("anti-kick-delay", "Anti Kick Delay", 40, 1, 80, 1).group("Flight").visible(module -> module.bool("fly") && module.bool("anti-kick")).description("Sets anti-kick interval."));
    }

    @Override
    public void onEnable() {
        this.resetAntiKick();
        this.refreshSelectedEntities();
    }

    @Override
    public void onDisable() {
        this.resetAntiKick();
    }

    @Override
    public void onGameLeft() {
        this.resetAntiKick();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if ("entities".equals(optionId)) {
            this.refreshSelectedEntities();
        }
        if ("anti-kick-delay".equals(optionId)) {
            this.antiKickTicks = this.integer("anti-kick-delay");
        }
    }

    @Override
    protected void onSettingsReset() {
        this.refreshSelectedEntities();
        this.resetAntiKick();
    }

    @Override
    public void preMovementTick() {
        if (MC.player != null && MC.getConnection() != null) {
            if (this.restorePacketPending) {
                Entity vehicle = MC.player.getVehicle();
                if (this.isControlledVehicle(vehicle)) {
                    this.sendSynthetic(new ServerboundMoveVehiclePacket(new Vec3(vehicle.getX(), this.lastPacketY, vehicle.getZ()), vehicle.getYRot(), vehicle.getXRot(), vehicle.onGround()));
                }
                this.restorePacketPending = false;
            }
            if (this.antiKickTicks > 0) {
                --this.antiKickTicks;
            }
        }
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!this.sendingSyntheticPacket && this.bool("fly") && this.bool("anti-kick")) {
            if (packet instanceof ServerboundMoveVehiclePacket movePacket) {
                Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
                if (this.isControlledVehicle(vehicle) && !isFlyingVehicleCompat(vehicle) && isOnAir(vehicle)) {
                    double currentY = movePacket.position().y;
                    if (this.antiKickTicks <= 0 && !this.restorePacketPending && this.shouldFlyDown(currentY)) {
                        double baseline = this.lastPacketY == Double.MAX_VALUE ? currentY : this.lastPacketY;
                        ServerboundMoveVehiclePacket lowered = new ServerboundMoveVehiclePacket(new Vec3(movePacket.position().x, baseline - 0.0313, movePacket.position().z), movePacket.yRot(), movePacket.xRot(), movePacket.onGround());
                        this.sendSynthetic(lowered);
                        this.restorePacketPending = true;
                        this.antiKickTicks = this.integer("anti-kick-delay");
                        this.lastPacketY = currentY;
                        return true;
                    } else {
                        this.lastPacketY = currentY;
                        return false;
                    }
                } else {
                    this.lastPacketY = movePacket.position().y;
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        return this.bool("cancel-server-packets") && packet instanceof ClientboundMoveVehiclePacket;
    }

    static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
        EntityControlModule module = instance;
        if (isActive(module) && type == MoverType.SELF && module.isControlledVehicle(vehicle)) {
            double velocityX = movement.x;
            double velocityY = movement.y;
            double velocityZ = movement.z;
            if (module.bool("lock-yaw")) {
                vehicle.setYRot(MC.player.getYRot());
            }
            if (module.bool("speed") && (!module.bool("only-on-ground") || vehicle.onGround() || isFlyingVehicleCompat(vehicle)) && (module.bool("in-water") || !vehicle.isInWater())) {
                Vec3 horizontal = horizontalVelocity(module.decimal("horizontal-speed"));
                velocityX = horizontal.x;
                velocityZ = horizontal.z;
            }
            if (module.bool("fly")) {
                velocityY = -module.decimal("fall-speed") / 20.0;
                if (MC.options.keyJump.isDown()) {
                    velocityY += module.decimal("vertical-speed") / 20.0;
                }
                if (MC.options.keySprint.isDown()) {
                    velocityY -= module.decimal("vertical-speed") / 20.0;
                }
            }
            return new Vec3(velocityX, velocityY, velocityZ);
        } else {
            return movement;
        }
    }

    public static boolean shouldLockBoatYaw() {
        EntityControlModule module = instance;
        return isActive(module) && module.bool("lock-yaw") && module.isControlledVehicle(MC.player == null ? null : MC.player.getVehicle());
    }

    public static boolean shouldSpoofSaddle(Mob mob) {
        EntityControlModule module = instance;
        return isActive(module) && module.bool("spoof-saddle") && module.isSelected(mob);
    }

    public static boolean shouldMaxJump() {
        EntityControlModule module = instance;
        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        return isActive(module) && module.bool("max-jump") && module.isControlledVehicle(vehicle);
    }

    public static boolean shouldCancelRidingJump() {
        EntityControlModule module = instance;
        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        return isActive(module) && module.bool("fly") && vehicle instanceof PlayerRideableJumping && module.isControlledVehicle(vehicle);
    }

    private static boolean isActive(EntityControlModule module) {
        return module != null && module.isEnabled() && !PackHideState.isHardLocked() && MC.player != null;
    }

    private boolean isControlledVehicle(Entity vehicle) {
        return vehicle != null && MC.player != null && MC.player.getVehicle() == vehicle && vehicle.getControllingPassenger() == MC.player && this.isSelected(vehicle);
    }

    private boolean isSelected(Entity entity) {
        if (entity == null) {
            return false;
        } else {
            this.refreshSelectedEntities();
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return id != null && this.selectedEntities.contains(id.toString());
        }
    }

    private void refreshSelectedEntities() {
        String current = this.value("entities");
        if (!current.equals(this.selectedEntitiesValue)) {
            this.selectedEntitiesValue = current;
            this.selectedEntities.clear();
            this.selectedEntities.addAll(this.list("entities"));
        }
    }

    private void resetAntiKick() {
        this.antiKickTicks = this.integer("anti-kick-delay");
        this.lastPacketY = Double.MAX_VALUE;
        this.restorePacketPending = false;
        this.sendingSyntheticPacket = false;
    }

    private void sendSynthetic(ServerboundMoveVehiclePacket packet) {
        if (MC.getConnection() != null) {
            this.sendingSyntheticPacket = true;
            try {
                MC.getConnection().send(packet);
            } finally {
                this.sendingSyntheticPacket = false;
            }
        }
    }

    private boolean shouldFlyDown(double currentY) {
        return currentY >= this.lastPacketY || this.lastPacketY - currentY < 0.0313;
    }

    private static boolean isFlyingVehicleCompat(Entity entity) {
        //? if >=1.21.6 {
        return entity.isFlyingVehicle();
        //?} else {
        /*return false;*///?}
    }

    private static boolean isOnAir(Entity entity) {
        return entity.level().getBlockStates(entity.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0)).allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    private static Vec3 horizontalVelocity(double blocksPerSecond) {
        double speed = blocksPerSecond / 20.0;
        float forward = 0.0F;
        float sideways = 0.0F;
        if (MC.options.keyUp.isDown()) {
            ++forward;
        }
        if (MC.options.keyDown.isDown()) {
            --forward;
        }
        if (MC.options.keyLeft.isDown()) {
            ++sideways;
        }
        if (MC.options.keyRight.isDown()) {
            --sideways;
        }
        if (forward == 0.0F && sideways == 0.0F) {
            return Vec3.ZERO;
        } else {
            double length = Math.sqrt(forward * forward + sideways * sideways);
            forward /= (float) length;
            sideways /= (float) length;
            double yaw = Math.toRadians(MC.player.getYRot());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            return new Vec3((sideways * cos - forward * sin) * speed, 0.0, (forward * cos + sideways * sin) * speed);
        }
    }
}

package autismclient.mixin;

import autismclient.modules.PackFreecamState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class AutismCameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    //? if >=1.21.11 {
    private void autism$freecamCamera(net.minecraft.world.level.Level area, net.minecraft.world.entity.Entity entity, boolean thirdPerson, boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {
    //?} else {
    /*private void autism$freecamCamera(net.minecraft.world.level.BlockGetter area, net.minecraft.world.entity.Entity entity, boolean thirdPerson, boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {*/
    //?}
        if (!PackFreecamState.isActive()) return;
        detached = true;
        setPosition(new Vec3(PackFreecamState.getX(partialTicks), PackFreecamState.getY(partialTicks), PackFreecamState.getZ(partialTicks)));
        setRotation(PackFreecamState.getYaw(partialTicks), PackFreecamState.getPitch(partialTicks));
    }
}

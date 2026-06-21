package autismclient.mixin;

import autismclient.modules.EntityControlModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//?} else {
/*import net.minecraft.world.entity.vehicle.AbstractBoat;*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBoat.class)
public abstract class AutismAbstractBoatEntityControlMixin {
    @ModifyExpressionValue(method = "controlBoat", at = @At(value = "FIELD",
        //? if >=1.21.11 {
        target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputLeft:Z",
        //?} else {
        /*target = "Lnet/minecraft/world/entity/vehicle/AbstractBoat;inputLeft:Z",*///?}
        opcode = 180))
    private boolean autism$lockLeftTurn(boolean original) {
        return EntityControlModule.shouldLockBoatYaw() ? false : original;
    }

    @ModifyExpressionValue(method = "controlBoat", at = @At(value = "FIELD",
        //? if >=1.21.11 {
        target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;inputRight:Z",
        //?} else {
        /*target = "Lnet/minecraft/world/entity/vehicle/AbstractBoat;inputRight:Z",*///?}
        opcode = 180))
    private boolean autism$lockRightTurn(boolean original) {
        return EntityControlModule.shouldLockBoatYaw() ? false : original;
    }
}

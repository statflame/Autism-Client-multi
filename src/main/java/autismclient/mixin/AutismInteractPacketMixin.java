package autismclient.mixin;

import autismclient.ducks.AutismInteractPacketDuck;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundInteractPacket.class)
public abstract class AutismInteractPacketMixin implements AutismInteractPacketDuck {
    @Shadow
    @Final
    private int entityId;

    @Shadow
    public abstract void dispatch(ServerboundInteractPacket.Handler handler);

    @Unique
    private boolean autism$captured;

    @Unique
    private InteractionHand autism$hand;

    @Unique
    private Vec3 autism$hitPos;

    @Override
    public int autism$entityId() {
        return this.entityId;
    }

    @Override
    public InteractionHand autism$hand() {
        autism$capture();
        return this.autism$hand;
    }

    @Override
    public Vec3 autism$hitPos() {
        autism$capture();
        return this.autism$hitPos;
    }

    @Unique
    private void autism$capture() {
        if (this.autism$captured) {
            return;
        }
        this.autism$captured = true;
        this.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                AutismInteractPacketMixin.this.autism$hand = hand;
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {
                AutismInteractPacketMixin.this.autism$hand = hand;
                AutismInteractPacketMixin.this.autism$hitPos = pos;
            }

            @Override
            public void onAttack() {
            }
        });
    }
}

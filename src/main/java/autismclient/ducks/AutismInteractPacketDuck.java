package autismclient.ducks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public interface AutismInteractPacketDuck {
    int autism$entityId();

    InteractionHand autism$hand();

    Vec3 autism$hitPos();
}

package autismclient.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;

public final class AutismPacketCompat {
    private AutismPacketCompat() {
    }

    public static ServerboundContainerClickPacket click(int containerId, int stateId, short slot, byte button, ClickType type) {
        //? if >=1.21.5 {
        return new ServerboundContainerClickPacket(containerId, stateId, slot, button, type, new Int2ObjectArrayMap<>(), net.minecraft.network.HashedStack.EMPTY);
        //?} else {
        /*return new ServerboundContainerClickPacket(containerId, stateId, slot, button, type, net.minecraft.world.item.ItemStack.EMPTY, new Int2ObjectArrayMap<>());
        *///?}
    }
}

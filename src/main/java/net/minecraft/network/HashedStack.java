//? if <1.21.5 {
/*package net.minecraft.network;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;

public interface HashedStack {
    HashedStack EMPTY = new HashedStack() {
    };

    record ActualItem(Holder<Item> item, int count, Object components) implements HashedStack {
    }
}
*///?}

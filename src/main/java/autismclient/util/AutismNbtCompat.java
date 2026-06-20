package autismclient.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class AutismNbtCompat {
    private AutismNbtCompat() {
    }

    public static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.keySet();
        //?} else {
        /*return tag.getAllKeys();
        *///?}
    }

    public static Set<Map.Entry<String, Tag>> entries(CompoundTag tag) {
        //? if >=1.21.5 {
        return tag.entrySet();
        //?} else {
        /*Set<Map.Entry<String, Tag>> out = new LinkedHashSet<>();
        for (String k : tag.getAllKeys()) out.add(Map.entry(k, tag.get(k)));
        return out;
        *///?}
    }

    public static ListTag list(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getListOrEmpty(key);
        //?} else {
        /*Tag t = tag.get(key);
        return t instanceof ListTag ? (ListTag) t : new ListTag();
        *///?}
    }

    public static Optional<CompoundTag> compound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getCompound(key);
        //?} else {
        /*Tag t = tag.get(key);
        return t instanceof CompoundTag ? Optional.of((CompoundTag) t) : Optional.empty();
        *///?}
    }
}

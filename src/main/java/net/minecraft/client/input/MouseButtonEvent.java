//? if <1.21.9 {
/*package net.minecraft.client.input;

public record MouseButtonEvent(double x, double y, MouseButtonInfo info) {
    public int button() {
        return this.info.button();
    }

    public boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    public boolean hasControlDown() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    public boolean hasAltDown() {
        return net.minecraft.client.gui.screens.Screen.hasAltDown();
    }
}
*///?}

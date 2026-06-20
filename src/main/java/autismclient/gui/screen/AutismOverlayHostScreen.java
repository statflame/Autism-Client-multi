package autismclient.gui.screen;

import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismUiScale;
import autismclient.util.IAutismOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AutismOverlayHostScreen extends Screen {

    private final IAutismOverlay tiedOverlay;
    private final Screen returnScreen;

    public AutismOverlayHostScreen() {
        this(null, null);
    }

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay) {
        this(tiedOverlay, null);
    }

    public AutismOverlayHostScreen(IAutismOverlay tiedOverlay, Screen returnScreen) {
        super(Component.literal("Autism Overlays"));
        this.tiedOverlay = tiedOverlay;
        this.returnScreen = returnScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (tiedOverlay != null && !tiedOverlay.isVisible() && minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(returnScreen);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor)(Object) g;

        AutismOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    //?} else {
    /*@Override
    public boolean mouseClicked(double autism$x, double autism$y, int autism$b) {
        MouseButtonEvent event = new MouseButtonEvent(autism$x, autism$y, new net.minecraft.client.input.MouseButtonInfo(autism$b, 0));
        boolean doubled = false;*/
    //?}
        return AutismOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button());
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
    //?} else {
    /*@Override
    public boolean mouseReleased(double autism$x, double autism$y, int autism$b) {
        MouseButtonEvent event = new MouseButtonEvent(autism$x, autism$y, new net.minecraft.client.input.MouseButtonInfo(autism$b, 0));*/
    //?}
        return AutismOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button());
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
    //?} else {
    /*@Override
    public boolean mouseDragged(double autism$x, double autism$y, int autism$b, double dx, double dy) {
        MouseButtonEvent event = new MouseButtonEvent(autism$x, autism$y, new net.minecraft.client.input.MouseButtonInfo(autism$b, 0));*/
    //?}
        return AutismOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return AutismOverlayManager.get().handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent input) {
    //?} else {
    /*@Override
    public boolean keyPressed(int autism$k, int autism$s, int autism$m) {
        KeyEvent input = new KeyEvent(autism$k, autism$s, autism$m);*/
    //?}

        boolean inventoryKey = minecraft != null && minecraft.options != null
            && minecraft.options.keyInventory != null
            //? if >=1.21.9 {
            && minecraft.options.keyInventory.matches(input);
            //?} else {
            /*&& minecraft.options.keyInventory.matches(input.key(), input.scancode());*///?}
        boolean closeKey = input.key() == GLFW.GLFW_KEY_ESCAPE || inventoryKey;

        if (closeKey && minecraft != null && !AutismOverlayManager.get().isAnyTextFieldFocused()) {
            minecraft.setScreen(returnScreen);
            return true;
        }

        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        if (closeKey && minecraft != null) {
            minecraft.setScreen(returnScreen);
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean charTyped(CharacterEvent input) {
    //?} else {
    /*@Override
    public boolean charTyped(char autism$c, int autism$mods) {
        CharacterEvent input = new CharacterEvent(autism$c, autism$mods);*/
    //?}
        return AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0);
    }
}



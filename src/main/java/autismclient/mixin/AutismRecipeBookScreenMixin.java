package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.util.AutismOverlayManager;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AutismRecipeBookScreenMixin {

    @Unique
    private boolean autism$isActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.isActive();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    //? if >=1.21.9 {
    private void autism$handleOverlayClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void autism$handleOverlayClick(double autism$x, double autism$y, int autism$b, CallbackInfoReturnable<Boolean> cir) {
        MouseButtonEvent click = new MouseButtonEvent(autism$x, autism$y, new net.minecraft.client.input.MouseButtonInfo(autism$b, 0));
        boolean doubled = false;*/
    //?}
        if (!autism$isActive()) return;

        AutismOverlayManager manager = AutismOverlayManager.get();
        if (manager.handleMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    //? if >=1.21.9 {
    private void autism$handleOverlayDrag(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void autism$handleOverlayDrag(double autism$x, double autism$y, int autism$b, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        MouseButtonEvent click = new MouseButtonEvent(autism$x, autism$y, new net.minecraft.client.input.MouseButtonInfo(autism$b, 0));*/
    //?}
        if (!autism$isActive()) return;

        if (AutismOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    //? if >=1.21.9 {
    private void autism$handleOverlayKeys(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void autism$handleOverlayKeys(int autism$k, int autism$s, int autism$m, CallbackInfoReturnable<Boolean> cir) {
        KeyEvent input = new KeyEvent(autism$k, autism$s, autism$m);*/
    //?}
        if (!autism$isActive()) return;

        if (AutismOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    //? if >=1.21.9 {
    private void autism$handleOverlayChars(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void autism$handleOverlayChars(char autism$c, int autism$mods, CallbackInfoReturnable<Boolean> cir) {
        CharacterEvent input = new CharacterEvent(autism$c, autism$mods);*/
    //?}
        if (!autism$isActive()) return;

        if (AutismOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }
}

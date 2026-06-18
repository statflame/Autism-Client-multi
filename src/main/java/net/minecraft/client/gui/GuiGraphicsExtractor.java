package net.minecraft.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public interface GuiGraphicsExtractor {
    private GuiGraphics autism$gg() {
        return (GuiGraphics) (Object) this;
    }

    default int guiWidth() {
        return autism$gg().guiWidth();
    }

    default int guiHeight() {
        return autism$gg().guiHeight();
    }

    default Matrix3x2fStack pose() {
        return autism$gg().pose();
    }

    default void enableScissor(int x1, int y1, int x2, int y2) {
        autism$gg().enableScissor(x1, y1, x2, y2);
    }

    default void disableScissor() {
        autism$gg().disableScissor();
    }

    default void nextStratum() {
        autism$gg().nextStratum();
    }

    default void fill(int x1, int y1, int x2, int y2, int color) {
        autism$gg().fill(x1, y1, x2, y2, color);
    }

    default void fill(RenderPipeline pipeline, int x1, int y1, int x2, int y2, int color) {
        autism$gg().fill(pipeline, x1, y1, x2, y2, color);
    }

    default void drawString(Font font, String text, int x, int y, int color) {
        autism$gg().drawString(font, text, x, y, color);
    }

    default void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        autism$gg().drawString(font, text, x, y, color, shadow);
    }

    default void drawString(Font font, Component text, int x, int y, int color) {
        autism$gg().drawString(font, text, x, y, color);
    }

    default void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        autism$gg().drawString(font, text, x, y, color, shadow);
    }

    default void drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        autism$gg().drawString(font, text, x, y, color);
    }

    default void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        autism$gg().drawString(font, text, x, y, color, shadow);
    }

    default void drawCenteredString(Font font, String text, int x, int y, int color) {
        autism$gg().drawCenteredString(font, text, x, y, color);
    }

    default void drawCenteredString(Font font, Component text, int x, int y, int color) {
        autism$gg().drawCenteredString(font, text, x, y, color);
    }

    default void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        autism$gg().drawCenteredString(font, text, x, y, color);
    }

    default void renderItem(ItemStack stack, int x, int y) {
        autism$gg().renderItem(stack, x, y);
    }

    default void renderItem(ItemStack stack, int x, int y, int seed) {
        autism$gg().renderItem(stack, x, y, seed);
    }

    default void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        autism$gg().renderItemDecorations(font, stack, x, y);
    }

    default void blit(Identifier texture, int x, int y, int width, int height, float u, float v, float regionWidth, float regionHeight) {
        autism$gg().blit(texture, x, y, width, height, u, v, regionWidth, regionHeight);
    }

    default void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        autism$gg().blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    default void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
        autism$gg().blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
    }

    default void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        autism$gg().blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    default void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        autism$gg().blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
    }

    default void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        autism$gg().blitSprite(pipeline, sprite, x, y, width, height);
    }

    default void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, float alpha) {
        autism$gg().blitSprite(pipeline, sprite, x, y, width, height, alpha);
    }

    default void blitSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color) {
        autism$gg().blitSprite(pipeline, sprite, x, y, width, height, color);
    }

    default Font textRenderer() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    default void item(ItemStack stack, int x, int y) {
        renderItem(stack, x, y);
    }

    default void itemDecorations(Font font, ItemStack stack, int x, int y) {
        renderItemDecorations(font, stack, x, y);
    }

    default void skin(net.minecraft.client.model.player.PlayerModel model, Identifier texture, float scale, float rotX, float rotY, float pivot, int x0, int y0, int x1, int y1) {
    }

    default void text(Font font, String text, int x, int y, int color) {
        drawString(font, text, x, y, color);
    }

    default void text(Font font, Component text, int x, int y, int color) {
        drawString(font, text, x, y, color);
    }

    default void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        drawString(font, text, x, y, color);
    }

    default void text(Font font, String text, int x, int y, int color, boolean shadow) {
        drawString(font, text, x, y, color, shadow);
    }

    default void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        drawString(font, text, x, y, color, shadow);
    }

    default void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        drawString(font, text, x, y, color, shadow);
    }

    default void centeredText(Font font, String text, int x, int y, int color) {
        drawCenteredString(font, text, x, y, color);
    }

    default void centeredText(Font font, Component text, int x, int y, int color) {
        drawCenteredString(font, text, x, y, color);
    }

    default void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        drawCenteredString(font, text, x, y, color);
    }
}

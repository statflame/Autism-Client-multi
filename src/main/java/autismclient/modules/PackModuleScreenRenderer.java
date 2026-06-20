package autismclient.modules;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismPerf;
import autismclient.util.AutismUiScale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PackModuleScreenRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<Integer, SmoothedScreenBox> SMOOTHED_BOXES = new HashMap<>();
    private static final Map<Integer, double[]> COMPASS_POINTS = new HashMap<>();
    private static final double COMPASS_EASE = 0.3;
    private static Object lastLevel;
    private static long frameIndex;
    private static long compassFrame;

    private PackModuleScreenRenderer() {
    }

    public static void render(GuiGraphicsExtractor context) {
        if (PackHideState.isActive()) return;
        if (MC == null || MC.level == null || MC.player == null || MC.options.hideGui) return;
        boolean esp2d = PackModuleRenderUtil.has2dEspWork();
        boolean compass = PackModuleRenderUtil.hasWorldTracerWork();
        if (!esp2d && !compass) return;
        if (PackModuleRenderUtil.shouldSuppressEspForUi()) return;
        long perf = AutismPerf.begin();
        try {
            if (esp2d) renderEsp2d(context);
            if (compass) renderCompass(context);
        } finally {
            AutismPerf.end("modules.esp2d", perf);
        }
    }

    private static void renderEsp2d(GuiGraphicsExtractor context) {
        Camera camera = MC.gameRenderer.getMainCamera();
        if (camera == null || MC.getWindow() == null) return;
        if (lastLevel != MC.level) {
            SMOOTHED_BOXES.clear();
            lastLevel = MC.level;
        }
        long frame = ++frameIndex;
        Matrix4f viewProj = PackModuleWorldRenderer.viewProjMatrix();
        if (viewProj == null) return;
        Projection projection = new Projection(
            camera.position(),
            viewProj,
            AutismUiScale.getVirtualScreenWidth(),
            AutismUiScale.getVirtualScreenHeight()
        );
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!PackModuleRenderUtil.shouldEsp(entity)) continue;
            ScreenBox box = projectBox(entity, tickDelta, projection);
            if (box == null) continue;
            box = smoothBox(entity, box, frame);
            int color = PackModuleRenderUtil.espColor(entity);
            drawLiquidBounce2dBox(context, box, color, entity);
        }
        pruneSmoothingCache(frame);
    }

    private static void renderCompass(GuiGraphicsExtractor context) {
        Camera camera = MC.gameRenderer.getMainCamera();
        if (camera == null || MC.getWindow() == null) return;
        Matrix4f viewProj = PackModuleWorldRenderer.viewProjMatrix();
        if (viewProj == null) return;
        int width = AutismUiScale.getVirtualScreenWidth();
        int height = AutismUiScale.getVirtualScreenHeight();
        if (width <= 0 || height <= 0) return;

        double cx = width / 2.0;
        double cy = height / 2.0;
        double topY = cy;
        double botY = height;
        Vec3 cam = camera.position();
        Quaternionf invRot = new Quaternionf(camera.rotation()).conjugate();
        float tickDelta = MC.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float lineWidth = compassLineWidth();

        Vector4f clip = new Vector4f();
        Vector3f view = new Vector3f();
        long frame = ++compassFrame;
        int drawn = 0;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (drawn >= 64) break;
            if (!PackModuleRenderUtil.shouldTrace(entity)) continue;
            double wx = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double wy = Mth.lerp(tickDelta, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5;
            double wz = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
            double rx = wx - cam.x;
            double ry = wy - cam.y;
            double rz = wz - cam.z;

            clip.set((float) rx, (float) ry, (float) rz, 1.0f);
            viewProj.transform(clip);
            double dirX;
            double dirY;
            if (clip.w > 0.001f) {
                double sx = (clip.x / clip.w * 0.5 + 0.5) * width;
                double sy = (0.5 - clip.y / clip.w * 0.5) * height;
                if (sx >= 0.0 && sx <= width && sy >= 0.0 && sy <= height) continue;
                dirX = sx - cx;
                dirY = sy - cy;
            } else {
                view.set((float) rx, (float) ry, (float) rz);
                invRot.transform(view);
                double theta = Math.atan2(view.x, -view.z);
                dirX = Math.sin(theta);
                dirY = -Math.cos(theta);
            }

            double len = Math.sqrt(dirX * dirX + dirY * dirY);
            if (len < 1.0e-6) continue;
            dirX /= len;
            dirY /= len;
            if (dirY < 0.0) dirY = -dirY;

            double tTarget = rayClampT(dirX, dirY, cx, cy, 0.0, width, topY, botY);
            if (tTarget == Double.MAX_VALUE || tTarget <= 1.0) continue;
            double targetX = cx + dirX * tTarget;
            double targetY = cy + dirY * tTarget;

            double tEdge = rayClampT(dirX, dirY, cx, cy, 0.0, width, 0.0, botY);
            double edgeX = tEdge == Double.MAX_VALUE ? targetX : cx + dirX * tEdge;
            double edgeY = tEdge == Double.MAX_VALUE ? targetY : cy + dirY * tEdge;

            int id = entity.getId();
            double[] pt = COMPASS_POINTS.get(id);
            if (pt == null) {
                pt = new double[]{edgeX, edgeY, frame};
                COMPASS_POINTS.put(id, pt);
            } else {
                pt[0] += (targetX - pt[0]) * COMPASS_EASE;
                pt[1] += (targetY - pt[1]) * COMPASS_EASE;
                pt[2] = frame;
            }

            int color = 0xFF000000 | (PackModuleRenderUtil.tracerColor(entity) & 0x00FFFFFF);
            drawLine2d(context, cx, cy, pt[0], pt[1], color, lineWidth * 0.5f);
            drawn++;
        }

        java.util.Iterator<Map.Entry<Integer, double[]>> it = COMPASS_POINTS.entrySet().iterator();
        while (it.hasNext()) {
            if (frame - (long) it.next().getValue()[2] > 3L) it.remove();
        }
    }

    private static void drawLine2d(GuiGraphicsExtractor context, double x0, double y0, double x1, double y1, int color, float thickness) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0) return;
        float angle = (float) Math.atan2(dy, dx);
        float th = Math.max(0.1f, thickness);
        int end = (int) Math.round(len);
        //? if >=1.21.6 {
        context.pose().pushMatrix();
        context.pose().translate((float) x0, (float) y0);
        context.pose().rotate(angle);
        context.pose().translate(0.0f, -th / 2.0f);
        context.pose().scale(1.0f, th);
        context.fill(0, 0, end, 1, color);
        context.pose().popMatrix();
        //?} else {
        /*com.mojang.blaze3d.vertex.PoseStack ps = context.pose();
        ps.pushPose();
        ps.translate((float) x0, (float) y0, 0.0f);
        ps.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        ps.translate(0.0f, -th / 2.0f, 0.0f);
        ps.scale(1.0f, th, 1.0f);
        context.fill(0, 0, end, 1, color);
        ps.popPose();
        *///?}
    }

    private static double rayClampT(double dirX, double dirY, double ox, double oy, double xmin, double xmax, double ymin, double ymax) {
        double t = Double.MAX_VALUE;
        if (dirX > 1.0e-6) t = Math.min(t, (xmax - ox) / dirX);
        else if (dirX < -1.0e-6) t = Math.min(t, (xmin - ox) / dirX);
        if (dirY > 1.0e-6) t = Math.min(t, (ymax - oy) / dirY);
        else if (dirY < -1.0e-6) t = Math.min(t, (ymin - oy) / dirY);
        return t;
    }

    private static float compassLineWidth() {
        PackModule tracer = PackModuleRegistry.get("tracers");
        if (tracer == null) return 1.0f;
        try {
            return Mth.clamp(Float.parseFloat(tracer.value("line-width")), 0.5f, 4.0f);
        } catch (Exception ignored) {
            return 1.0f;
        }
    }

    private static ScreenBox projectBox(Entity entity, float tickDelta, Projection projection) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double halfWidth = dimensions.width() / 2.0;
        double minX = x - halfWidth - 0.05;
        double minY = y - 0.05;
        double minZ = z - halfWidth - 0.05;
        double maxX = x + halfWidth + 0.05;
        double maxY = y + dimensions.height() + 0.05;
        double maxZ = z + halfWidth + 0.05;
        ScreenBox out = new ScreenBox();
        out.include(minX, minY, minZ, projection);
        out.include(maxX, minY, minZ, projection);
        out.include(minX, minY, maxZ, projection);
        out.include(maxX, minY, maxZ, projection);
        out.include(minX, maxY, minZ, projection);
        out.include(maxX, maxY, minZ, projection);
        out.include(minX, maxY, maxZ, projection);
        out.include(maxX, maxY, maxZ, projection);
        return out.isValid() ? out : null;
    }

    private static ScreenBox smoothBox(Entity entity, ScreenBox raw, long frame) {
        int id = entity.getId();
        SmoothedScreenBox cached = SMOOTHED_BOXES.get(id);
        if (cached == null || cached.shouldReset(raw)) {
            cached = new SmoothedScreenBox(raw);
            SMOOTHED_BOXES.put(id, cached);
        } else {
            cached.lerpTo(raw);
        }
        cached.lastFrame = frame;
        cached.applyTo(raw);
        return raw;
    }

    private static void pruneSmoothingCache(long frame) {
        if ((frame & 31L) != 0L) return;
        Iterator<Map.Entry<Integer, SmoothedScreenBox>> iterator = SMOOTHED_BOXES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (frame - iterator.next().getValue().lastFrame > 10L) iterator.remove();
        }
    }

    private static void drawLiquidBounce2dBox(GuiGraphicsExtractor context, ScreenBox box, int color, Entity entity) {
        int x = Mth.floor(box.minX);
        int y = Mth.floor(box.minY);
        int w = Mth.ceil(box.maxX) - x;
        int h = Mth.ceil(box.maxY) - y;
        if (w <= 0 || h <= 0) return;
        int rgb = color & 0x00FFFFFF;
        int fill = (50 << 24) | rgb;
        int outline = 0xFF000000 | rgb;
        UiRenderer.rect(context, UiBounds.of(x, y, w, h), fill);
        drawBorderedRectOutline(context, x, y, w, h, outline);
        if (entity instanceof LivingEntity living) {
            drawHealthBar(context, x, y, h, living);
        }
    }

    private static void drawBorderedRectOutline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        int black = 0xFF000000;
        UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, w + 2, 3), black);
        UiRenderer.rect(context, UiBounds.of(x - 1, y - 1, 3, h + 2), black);
        UiRenderer.rect(context, UiBounds.of(x - 1, y + h - 2, w + 2, 3), black);
        UiRenderer.rect(context, UiBounds.of(x + w - 2, y - 1, 3, h + 2), black);
        UiRenderer.outline(context, UiBounds.of(x, y, w, h), color);
    }

    private static void drawHealthBar(GuiGraphicsExtractor context, int x, int y, int h, LivingEntity entity) {
        float maxHealth = Math.max(1.0f, entity.getMaxHealth());
        float health = Mth.clamp(entity.getHealth() / maxHealth, 0.0f, 1.0f);
        int filled = Math.max(0, Math.min(h, Math.round(h * health)));
        int barX = x - 5;
        UiRenderer.rect(context, UiBounds.of(barX - 1, y - 1, 3, h + 2), 0xFF000000);
        if (filled <= 0) return;
        int red = Math.round(255.0f * (1.0f - health));
        int green = Math.round(255.0f * health);
        int healthColor = 0xFF000000 | (red << 16) | (green << 8);
        UiRenderer.rect(context, UiBounds.of(barX, y + h - filled, 1, filled), healthColor);
    }

    private static final class ScreenBox {
        private final Vector4f vector = new Vector4f();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        boolean include(double worldX, double worldY, double worldZ, Projection projection) {
            Vec3 cam = projection.cameraPosition();
            vector.set((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z), 1.0f);
            projection.matrix().transform(vector);
            if (vector.w <= 0.001f) return false;
            float ndcX = vector.x / vector.w;
            float ndcY = vector.y / vector.w;
            if (Float.isNaN(ndcX) || Float.isNaN(ndcY) || Float.isInfinite(ndcX) || Float.isInfinite(ndcY)) return false;
            float x = (ndcX * 0.5f + 0.5f) * projection.screenWidth();
            float y = (0.5f - ndcY * 0.5f) * projection.screenHeight();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            return true;
        }

        boolean isValid() {
            return maxX > minX && maxY > minY;
        }
    }

    private static final class SmoothedScreenBox {
        private static final float SMOOTHING = 0.42f;
        private static final float RESET_DISTANCE = 42.0f;
        private static final float RESET_SIZE_DELTA = 24.0f;

        float minX;
        float minY;
        float maxX;
        float maxY;
        long lastFrame;

        SmoothedScreenBox(ScreenBox raw) {
            set(raw);
        }

        void set(ScreenBox raw) {
            minX = raw.minX;
            minY = raw.minY;
            maxX = raw.maxX;
            maxY = raw.maxY;
        }

        void lerpTo(ScreenBox raw) {
            minX += (raw.minX - minX) * SMOOTHING;
            minY += (raw.minY - minY) * SMOOTHING;
            maxX += (raw.maxX - maxX) * SMOOTHING;
            maxY += (raw.maxY - maxY) * SMOOTHING;
        }

        boolean shouldReset(ScreenBox raw) {
            float centerDx = ((raw.minX + raw.maxX) - (minX + maxX)) * 0.5f;
            float centerDy = ((raw.minY + raw.maxY) - (minY + maxY)) * 0.5f;
            float widthDelta = Math.abs((raw.maxX - raw.minX) - (maxX - minX));
            float heightDelta = Math.abs((raw.maxY - raw.minY) - (maxY - minY));
            return (centerDx * centerDx) + (centerDy * centerDy) > RESET_DISTANCE * RESET_DISTANCE
                || widthDelta > RESET_SIZE_DELTA
                || heightDelta > RESET_SIZE_DELTA;
        }

        void applyTo(ScreenBox box) {
            box.minX = minX;
            box.minY = minY;
            box.maxX = maxX;
            box.maxY = maxY;
        }
    }

    private record Projection(Vec3 cameraPosition, Matrix4f matrix, int screenWidth, int screenHeight) {
    }
}

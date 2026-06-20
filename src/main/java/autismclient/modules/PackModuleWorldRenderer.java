package autismclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import autismclient.util.AutismPerf;
//? if <1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;*/
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PackModuleWorldRenderer {
    private static boolean initialized;
    private static StorageSnapshot storageSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot blockSnapshot = StorageSnapshot.empty();
    private static volatile Vec3 capturedTracerOrigin;
    private static volatile org.joml.Matrix4f capturedViewProj;

    public static org.joml.Matrix4f viewProjMatrix() {
        return capturedViewProj;
    }

    private PackModuleWorldRenderer() {
    }

    static void initialize() {
        if (initialized) return;
        initialized = true;
        //? if <1.21.9 {
        /*WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            PoseStack ps = context.matrixStack();
            if (ps == null) ps = new PoseStack();
            captureTracerOrigin(context.projectionMatrix(), new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewStack()).mul(ps.last().pose()));
            renderWorld(ps, context.consumers());
        });*/
        //?}
    }

    public static void renderWorld(PoseStack poseStack, MultiBufferSource consumers) {
        Minecraft mc = Minecraft.getInstance();
        if (PackHideState.isActive()) return;
        if (mc == null || mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (poseStack == null || consumers == null) return;
        boolean drawTracers = PackModuleRenderUtil.hasWorldTracerWork();
        boolean storageEnabled = PackModuleRegistry.isModuleEnabled("storage-esp");
        boolean blockEnabled = PackModuleRegistry.isModuleEnabled("block-esp");
        boolean entityEsp = PackModuleRegistry.isModuleEnabled("esp") && !PackModuleRenderUtil.has2dEspWork();
        boolean itemEsp = PackModuleRegistry.isModuleEnabled("item-esp");
        if (!drawTracers && !storageEnabled && !blockEnabled && !entityEsp && !itemEsp) return;
        if (PackModuleRenderUtil.shouldSuppressEspForUi()) return;
        PackModule tracer = PackModuleRegistry.get("tracers");
        PackModule storage = PackModuleRegistry.get("storage-esp");
        PackModule blockEsp = PackModuleRegistry.get("block-esp");

        boolean storageFill = storageEnabled && Boolean.parseBoolean(storage.value("fill"));
        boolean storageWire = storageEnabled;
        boolean storageTrace = storageEnabled && Boolean.parseBoolean(storage.value("tracers"));
        boolean blockFill = blockEnabled && Boolean.parseBoolean(blockEsp.value("fill"));
        boolean blockWire = blockEnabled;
        boolean blockTrace = blockEnabled && Boolean.parseBoolean(blockEsp.value("tracers"));
        if (!drawTracers && !storageFill && !storageWire && !storageTrace && !blockFill && !blockWire && !blockTrace && !entityEsp && !itemEsp) return;

        final Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        final Vec3 from = eyeVector(mc);
        final Vec3 origin = crosshairOrigin(mc, from);
        float tracerWidth = parseFloat(tracer == null ? null : tracer.value("line-width"), 1.0f, 0.5f, 4.0f);
        StorageSnapshot storageFrame = storageEnabled ? storageSnapshot(storage, mc.level, mc.player, tickDelta) : StorageSnapshot.empty();
        StorageSnapshot blockFrame = blockEnabled ? blockSnapshot(blockEsp, mc.level, mc.player) : StorageSnapshot.empty();

        final PoseStack.Pose pose = poseStack.last();

        if (storageFill || blockFill) {
            final VertexConsumer fillBuf = consumers.getBuffer(AutismRenderTypes.storageEspFillSeeThrough());
            final float fillOpacity = 0.3f;
            if (storageFill && storageFrame != null) {
                storageFrame.forEachBox((box, color) -> fillBox(pose, fillBuf, box.move(-camera.x, -camera.y, -camera.z), withAlpha(color, fillOpacity)));
            }
            if (blockFill && blockFrame != null) {
                blockFrame.forEachBox((box, color) -> fillBox(pose, fillBuf, box.move(-camera.x, -camera.y, -camera.z), withAlpha(color, fillOpacity)));
            }
        }

        final VertexConsumer lineBuf = consumers.getBuffer(AutismRenderTypes.storageEspLinesSeeThrough());
        final float finalTracerWidth = tracerWidth;
        if (drawTracers) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!PackModuleRenderUtil.shouldTrace(entity)) continue;
                Vec3 feet = interpolatedPosition(entity, tickDelta).subtract(camera);
                double height = entity.getBbHeight();
                Vec3 center = feet.add(0, height * 0.5, 0);
                if (!isOnScreen(center)) continue;
                int color = PackModuleRenderUtil.tracerColor(entity);
                clippedLine(pose, lineBuf, origin, center, from, color, finalTracerWidth);
                clippedLine(pose, lineBuf, feet, feet.add(0, height, 0), from, color, finalTracerWidth);
            }
        }
        if (storageWire && storageFrame != null) {
            storageFrame.forEachBox((box, color) -> renderStorageBox(pose, lineBuf, box.move(-camera.x, -camera.y, -camera.z), color, 1.5f));
        }
        if (blockWire && blockFrame != null) {
            blockFrame.forEachBox((box, color) -> renderStorageBox(pose, lineBuf, box.move(-camera.x, -camera.y, -camera.z), color, 1.5f));
        }
        if (storageTrace && storageFrame != null) {
            storageFrame.forEachTrace((target, color) -> clippedLine(pose, lineBuf, origin, target.subtract(camera), from, color, 2.0f));
        }
        if (blockTrace && blockFrame != null) {
            blockFrame.forEachTrace((target, color) -> clippedLine(pose, lineBuf, origin, target.subtract(camera), from, color, 2.0f));
        }

        if (entityEsp || itemEsp) {
            java.util.List<AABB> espBoxes = new java.util.ArrayList<>();
            java.util.List<Integer> espColors = new java.util.ArrayList<>();
            for (Entity entity : mc.level.entitiesForRendering()) {
                boolean isItem = entity instanceof net.minecraft.world.entity.item.ItemEntity;
                if (isItem) {
                    if (!itemEsp || !PackModuleRenderUtil.shouldItemEsp(entity)) continue;
                } else {
                    if (!entityEsp || !PackModuleRenderUtil.shouldEsp(entity)) continue;
                }
                espBoxes.add(entityBox(entity, tickDelta).move(-camera.x, -camera.y, -camera.z));
                espColors.add(isItem ? PackModuleRenderUtil.itemEspColor(entity) : PackModuleRenderUtil.espColor(entity));
            }
            if (!espBoxes.isEmpty()) {
                final VertexConsumer espFillBuf = consumers.getBuffer(AutismRenderTypes.storageEspFillSeeThrough());
                for (int i = 0; i < espBoxes.size(); i++) {
                    fillBox(pose, espFillBuf, espBoxes.get(i), withAlpha(espColors.get(i), 0.25f));
                }
                final VertexConsumer espLineBuf = consumers.getBuffer(AutismRenderTypes.storageEspLinesSeeThrough());
                for (int i = 0; i < espBoxes.size(); i++) {
                    renderStorageBox(pose, espLineBuf, espBoxes.get(i), espColors.get(i), 1.5f);
                }
            }
        }
    }

    private static AABB entityBox(Entity entity, float tickDelta) {
        Vec3 pos = interpolatedPosition(entity, tickDelta);
        AABB b = entity.getBoundingBox();
        return b.move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
    }

    private static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
        return new Vec3(
            Mth.lerp(tickDelta, entity.xOld, entity.getX()),
            Mth.lerp(tickDelta, entity.yOld, entity.getY()),
            Mth.lerp(tickDelta, entity.zOld, entity.getZ())
        );
    }

    public static void captureTracerOrigin(org.joml.Matrix4f projection, org.joml.Matrix4f view) {
        try {
            org.joml.Matrix4f combined = new org.joml.Matrix4f(projection).mul(view);
            capturedViewProj = combined;
            org.joml.Matrix4f inv = new org.joml.Matrix4f(combined).invert();
            org.joml.Vector4f p = inv.transform(new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f));
            if (p.w != 0.0f) {
                capturedTracerOrigin = new Vec3(p.x / p.w, p.y / p.w, p.z / p.w);
                return;
            }
        } catch (Exception ignored) {
        }
        capturedTracerOrigin = null;
    }

    private static Vec3 crosshairOrigin(Minecraft mc, Vec3 fallback) {
        Vec3 captured = capturedTracerOrigin;
        if (captured != null) return captured;
        try {
            org.joml.Quaternionf rotation = mc.gameRenderer.getMainCamera().rotation();
            org.joml.Vector3f dir = rotation.transform(new org.joml.Vector3f(0.0f, 0.0f, -0.1f));
            return new Vec3(dir.x, dir.y, dir.z);
        } catch (Exception ignored) {
        }
        return fallback.scale(0.1);
    }

    private static Vec3 eyeVector(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return Vec3.ZERO;
        org.joml.Vector3f dir = camera.rotation().transform(new org.joml.Vector3f(0.0f, 0.0f, -1.0f));
        return new Vec3(dir.x, dir.y, dir.z);
    }

    private static boolean isOnScreen(Vec3 cameraRelative) {
        org.joml.Matrix4f vp = capturedViewProj;
        if (vp == null) return true;
        org.joml.Vector4f clip = vp.transform(new org.joml.Vector4f((float) cameraRelative.x, (float) cameraRelative.y, (float) cameraRelative.z, 1.0f));
        if (clip.w <= 1.0e-6f) return false;
        float nx = clip.x / clip.w;
        float ny = clip.y / clip.w;
        return nx >= -1.0f && nx <= 1.0f && ny >= -1.0f && ny <= 1.0f;
    }

    private static void renderEntityBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        renderStorageBox(pose, buffer, box, color, 1.5f);
    }

    private static void renderStorageBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
        line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
        line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
        line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
        line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
        line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
    }

    private static final double NEAR_PLANE = 0.001;

    private static void clippedLine(PoseStack.Pose pose, VertexConsumer buffer,
                                    Vec3 a, Vec3 b, Vec3 forward,
                                    int color, float width) {
        double da = a.x * forward.x + a.y * forward.y + a.z * forward.z;
        double db = b.x * forward.x + b.y * forward.y + b.z * forward.z;
        if (da < NEAR_PLANE && db < NEAR_PLANE) return;
        double ax = a.x, ay = a.y, az = a.z;
        double bx = b.x, by = b.y, bz = b.z;
        if (da < NEAR_PLANE) {
            double t = (NEAR_PLANE - da) / (db - da);
            ax = a.x + (b.x - a.x) * t;
            ay = a.y + (b.y - a.y) * t;
            az = a.z + (b.z - a.z) * t;
        } else if (db < NEAR_PLANE) {
            double t = (NEAR_PLANE - db) / (da - db);
            bx = b.x + (a.x - b.x) * t;
            by = b.y + (a.y - b.y) * t;
            bz = b.z + (a.z - b.z) * t;
        }
        line(pose, buffer, ax, ay, az, bx, by, bz, color, width);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color, float width) {
        Vector3f normal = new Vector3f((float) (x1 - x2), (float) (y1 - y2), (float) (z1 - z2));
        if (normal.lengthSquared() > 0.0f) normal.normalize();
        //? if >=1.21.11 {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, normal).setLineWidth(width);
        normal.negate();
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, normal).setLineWidth(width);
        //?} else {
        /*buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, normal);
        normal.negate();
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, normal);*/
        //?}
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        if (((color >>> 24) & 0xFF) <= 0) return;
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }

    private static int withAlpha(int color, float alphaMultiplier) {
        int alpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alphaMultiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return Mth.clamp(Float.parseFloat(value), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static StorageSnapshot storageSnapshot(PackModule module, ClientLevel level, Player player, float tickDelta) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = PackModuleRegistry.revision();
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        StorageSnapshot cached = storageSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        PackModuleStorageEsp.collectBoth(
            module,
            level,
            player,
            tickDelta,
            (box, color) -> boxes.add(new StorageBox(box, color)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = new StorageSnapshot(gameTime, revision, chunkX, chunkZ, List.copyOf(boxes), List.copyOf(traces));
        storageSnapshot = next;
        AutismPerf.endJoinSpike("join.storageEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot blockSnapshot(PackModule module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = PackModuleRegistry.revision();
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        StorageSnapshot cached = blockSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        PackModuleBlockEsp.collectBoth(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = new StorageSnapshot(gameTime, revision, chunkX, chunkZ, List.copyOf(boxes), List.copyOf(traces));
        blockSnapshot = next;
        AutismPerf.endJoinSpike("join.blockEsp.scan", perf, 6_000_000L);
        return next;
    }

    private record StorageSnapshot(long gameTime, int revision, int chunkX, int chunkZ,
                                   List<StorageBox> boxes, List<StorageTrace> traces) {
        private static StorageSnapshot empty() {
            return new StorageSnapshot(Long.MIN_VALUE, -1, Integer.MIN_VALUE, Integer.MIN_VALUE, List.of(), List.of());
        }

        private boolean matches(long gameTime, int revision, int chunkX, int chunkZ) {
            return this.gameTime == gameTime && this.revision == revision && this.chunkX == chunkX && this.chunkZ == chunkZ;
        }

        private void forEachBox(java.util.function.BiConsumer<AABB, Integer> consumer) {
            for (StorageBox box : boxes) consumer.accept(box.box(), box.color());
        }

        private void forEachTrace(java.util.function.BiConsumer<Vec3, Integer> consumer) {
            for (StorageTrace trace : traces) consumer.accept(trace.target(), trace.color());
        }
    }

    private record StorageBox(AABB box, int color) {
    }

    private record StorageTrace(Vec3 target, int color) {
    }

}

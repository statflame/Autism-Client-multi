package autismclient.render;

//? if >=1.21.9 {
import autismclient.modules.GoldenLeverModule;
import autismclient.modules.PackHideState;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Vector3f;

public final class AutismFemaleBodyRenderer {
    private static final float ARM_WIDTH_SCALE = 0.78F;
    private static final Set<PlayerModel> MAIN_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<PlayerModel> ARMOR_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final MeshSet FULL_MESHES = MeshSet.build(MeshBuilder.Detail.FULL);
    private static final MeshSet MEDIUM_MESHES = MeshSet.build(MeshBuilder.Detail.MEDIUM);
    private static final MeshSet FAR_MESHES = MeshSet.build(MeshBuilder.Detail.FAR);
    private static final MeshSet CROWD_MESHES = MeshSet.build(MeshBuilder.Detail.CROWD);
    private static final double FULL_DETAIL_DISTANCE_SQ = 36.0;
    private static final double MEDIUM_DETAIL_DISTANCE_SQ = 324.0;
    private static final double CROWD_SAMPLE_DISTANCE_SQ = 1024.0;
    private static final int CROWD_DETAIL_PLAYER_COUNT = 24;
    private static final Map<Identifier, JacketAlpha> JACKET_ALPHA_CACHE = new HashMap<>();
    private static Object crowdSampleLevel;
    private static long crowdSampleTick = Long.MIN_VALUE;
    private static int nearbyPlayerCount;
    private static boolean initialized;
    private static volatile boolean layerReady;

    private AutismFemaleBodyRenderer() {
    }

    public static void initialize() {
        if (!initialized) {
            initialized = true;
            LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
                if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
                    PlayerModel playerModel = avatarRenderer.getModel();
                    MAIN_MODELS.add(playerModel);
                    helper.register(new FemaleBodyLayer(avatarRenderer, context.getEquipmentRenderer()));
                }
            });
        }
    }

    public static void markArmorModels(ArmorModelSet<?> modelSet) {
        if (modelSet != null) {
            markArmorModel(modelSet.head());
            markArmorModel(modelSet.chest());
            markArmorModel(modelSet.legs());
            markArmorModel(modelSet.feet());
        }
    }

    private static void markArmorModel(Object model) {
        if (model instanceof PlayerModel playerModel) {
            ARMOR_MODELS.add(playerModel);
        }
    }

    public static void applyModelVisibility(PlayerModel model, AvatarRenderState state) {
        if (model != null && state != null && layerReady && GoldenLeverModule.shouldApplyFemaleBody(state.id) && !PackHideState.isHardLocked()) {
            model.leftArm.xScale = ARM_WIDTH_SCALE;
            model.rightArm.xScale = ARM_WIDTH_SCALE;
            if (MAIN_MODELS.contains(model)) {
                model.body.visible = false;
                model.jacket.visible = false;
            } else if (ARMOR_MODELS.contains(model)) {
                model.body.visible = false;
            }
        }
    }

    public static void compensateHeldItemArmScale(AvatarRenderState state, PoseStack poseStack) {
        if (state != null && poseStack != null && GoldenLeverModule.shouldApplyFemaleBody(state.id)) {
            poseStack.scale(1.2820513F, 1.0F, 1.0F);
        }
    }

    //? if >=1.21.11 {
    private static RenderType rtTranslucent(Identifier t) { return RenderTypes.entityTranslucent(t); }
    private static RenderType rtSolid(Identifier t) { return RenderTypes.entitySolid(t); }
    private static RenderType rtCutoutNoCull(Identifier t) { return RenderTypes.entityCutoutNoCull(t); }
    private static RenderType rtOutline(Identifier t) { return RenderTypes.outline(t); }
    //?} else {
    /*private static RenderType rtTranslucent(Identifier t) { return RenderType.entityTranslucent(t); }
    private static RenderType rtSolid(Identifier t) { return RenderType.entitySolid(t); }
    private static RenderType rtCutoutNoCull(Identifier t) { return RenderType.entityCutoutNoCull(t); }
    private static RenderType rtOutline(Identifier t) { return RenderType.outline(t); }
    *///?}

    private static final class FemaleBodyLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final EquipmentLayerRenderer equipmentRenderer;

        private FemaleBodyLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EquipmentLayerRenderer equipmentRenderer) {
            super(renderer);
            this.equipmentRenderer = equipmentRenderer;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector output, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
            if (GoldenLeverModule.shouldApplyFemaleBody(state.id) && !PackHideState.isHardLocked() && !state.isSpectator) {
                AutismFemaleBodyRenderer.layerReady = true;
                poseStack.pushPose();
                PlayerModel parent = this.getParentModel();
                parent.root().translateAndRotate(poseStack);
                parent.body.translateAndRotate(poseStack);
                MeshSet meshes = meshesFor(state);
                renderSkin(meshes, state, poseStack, output, lightCoords);
                this.renderArmorPiece(state.chestEquipment, EquipmentSlot.CHEST, EquipmentClientInfo.LayerType.HUMANOID, meshes.chestArmor(), state, poseStack, output, lightCoords, 3);
                this.renderArmorPiece(state.legsEquipment, EquipmentSlot.LEGS, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, meshes.leggingsArmor(), state, poseStack, output, lightCoords, 8);
                poseStack.popPose();
            }
        }

        private static void renderSkin(MeshSet meshes, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector output, int lightCoords) {
            Identifier texture = state.skin.body().texturePath();
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            JacketAlpha jacketAlpha = state.showJacket ? jacketAlpha(texture) : JacketAlpha.EMPTY;
            if (!state.isInvisible) {
                if (meshes == AutismFemaleBodyRenderer.CROWD_MESHES) {
                    MeshModel crowdMesh = jacketAlpha == JacketAlpha.EMPTY ? meshes.body() : meshes.bodyWithJacket();
                    output.order(0).submitModel(crowdMesh, state, poseStack, rtTranslucent(texture), lightCoords, overlay, -1, (TextureAtlasSprite) null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                } else {
                    output.order(0).submitModel(meshes.body(), state, poseStack, rtSolid(texture), lightCoords, overlay, -1, (TextureAtlasSprite) null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                    if (jacketAlpha != JacketAlpha.EMPTY) {
                        RenderType jacketRenderType = switch (jacketAlpha) {
                            case UNKNOWN, TRANSLUCENT -> rtTranslucent(texture);
                            case EMPTY -> throw new IllegalStateException("Empty jacket submitted");
                            case OPAQUE -> rtSolid(texture);
                            case CUTOUT -> rtCutoutNoCull(texture);
                        };
                        output.order(0).submitModel(meshes.jacket(), state, poseStack, jacketRenderType, lightCoords, overlay, -1, (TextureAtlasSprite) null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                    }
                }
            } else {
                MeshModel visibleMesh = jacketAlpha == JacketAlpha.EMPTY ? meshes.body() : meshes.bodyWithJacket();
                if (!state.isInvisibleToPlayer) {
                    output.order(0).submitModel(visibleMesh, state, poseStack, rtTranslucent(texture), lightCoords, overlay, 654311423, (TextureAtlasSprite) null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                } else if (state.appearsGlowing()) {
                    output.order(0).submitModel(visibleMesh, state, poseStack, rtOutline(texture), lightCoords, overlay, -1, (TextureAtlasSprite) null, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay) null);
                }
            }
        }

        private static MeshSet meshesFor(AvatarRenderState state) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.getId() != state.id && sampleNearbyPlayerCount(minecraft) >= CROWD_DETAIL_PLAYER_COUNT) {
                return AutismFemaleBodyRenderer.CROWD_MESHES;
            } else {
                double distanceToCameraSq = state.distanceToCameraSq;
                if (distanceToCameraSq <= FULL_DETAIL_DISTANCE_SQ) {
                    return AutismFemaleBodyRenderer.FULL_MESHES;
                } else {
                    return distanceToCameraSq <= MEDIUM_DETAIL_DISTANCE_SQ ? AutismFemaleBodyRenderer.MEDIUM_MESHES : AutismFemaleBodyRenderer.FAR_MESHES;
                }
            }
        }

        private static int sampleNearbyPlayerCount(Minecraft minecraft) {
            if (minecraft.level != null && minecraft.player != null) {
                long tick = minecraft.level.getGameTime();
                if (minecraft.level == AutismFemaleBodyRenderer.crowdSampleLevel && tick == AutismFemaleBodyRenderer.crowdSampleTick) {
                    return AutismFemaleBodyRenderer.nearbyPlayerCount;
                } else {
                    AutismFemaleBodyRenderer.crowdSampleLevel = minecraft.level;
                    AutismFemaleBodyRenderer.crowdSampleTick = tick;
                    int count = 0;

                    for (AbstractClientPlayer player : minecraft.level.players()) {
                        if (player.distanceToSqr(minecraft.player) <= CROWD_SAMPLE_DISTANCE_SQ) {
                            ++count;
                        }
                    }

                    AutismFemaleBodyRenderer.nearbyPlayerCount = count;
                    return count;
                }
            } else {
                return 0;
            }
        }

        private static JacketAlpha jacketAlpha(Identifier textureId) {
            JacketAlpha cached = AutismFemaleBodyRenderer.JACKET_ALPHA_CACHE.get(textureId);
            if (cached != null) {
                return cached;
            } else {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
                if (!(texture instanceof DynamicTexture dynamicTexture)) {
                    AutismFemaleBodyRenderer.JACKET_ALPHA_CACHE.put(textureId, JacketAlpha.UNKNOWN);
                    return JacketAlpha.UNKNOWN;
                } else {
                    NativeImage pixels = dynamicTexture.getPixels();
                    if (pixels != null && pixels.getWidth() >= 64 && pixels.getHeight() >= 64) {
                        boolean hasTransparent = false;
                        boolean hasVisible = false;
                        boolean hasPartial = false;

                        for (int y = 32; y < 48; ++y) {
                            for (int x = 16; x < 40; ++x) {
                                int alpha = ARGB.alpha(pixels.getPixel(x, y));
                                hasTransparent |= alpha == 0;
                                hasVisible |= alpha != 0;
                                hasPartial |= alpha > 0 && alpha < 255;
                            }
                        }

                        JacketAlpha result;
                        if (!hasVisible) {
                            result = JacketAlpha.EMPTY;
                        } else if (hasPartial) {
                            result = JacketAlpha.TRANSLUCENT;
                        } else if (hasTransparent) {
                            result = JacketAlpha.CUTOUT;
                        } else {
                            result = JacketAlpha.OPAQUE;
                        }

                        AutismFemaleBodyRenderer.JACKET_ALPHA_CACHE.put(textureId, result);
                        return result;
                    } else {
                        return JacketAlpha.UNKNOWN;
                    }
                }
            }
        }

        private void renderArmorPiece(ItemStack stack, EquipmentSlot expectedSlot, EquipmentClientInfo.LayerType layerType, MeshModel model, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector output, int lightCoords, int order) {
            if (stack != null && !stack.isEmpty()) {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null && equippable.slot() == expectedSlot && !equippable.assetId().isEmpty()) {
                    this.equipmentRenderer.renderLayers(layerType, equippable.assetId().orElseThrow(), model, state, stack, poseStack, output, lightCoords, state.skin.body().texturePath(), state.outlineColor, order);
                }
            }
        }
    }

    private static final class MeshModel extends Model<AvatarRenderState> {
        private MeshModel(ModelPart root) {
            super(root, AutismFemaleBodyRenderer::rtTranslucent);
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
        }
    }

    private record UvLayout(float textureWidth, float textureHeight, float frontV, float topV) {
        private static final UvLayout SKIN_BODY = new UvLayout(64.0F, 64.0F, 20.0F, 16.0F);
        private static final UvLayout SKIN_JACKET = new UvLayout(64.0F, 64.0F, 36.0F, 32.0F);
        private static final UvLayout ARMOR = new UvLayout(64.0F, 32.0F, 20.0F, 16.0F);
    }

    private enum JacketAlpha {
        UNKNOWN,
        EMPTY,
        OPAQUE,
        CUTOUT,
        TRANSLUCENT
    }

    private record MeshSet(MeshModel body, MeshModel jacket, MeshModel bodyWithJacket, MeshModel chestArmor, MeshModel leggingsArmor) {
        private static MeshSet build(MeshBuilder.Detail detail) {
            return new MeshSet(
                MeshBuilder.build(UvLayout.SKIN_BODY, 0.0F, detail),
                MeshBuilder.build(UvLayout.SKIN_JACKET, 0.25F, detail),
                MeshBuilder.buildCombined(UvLayout.SKIN_BODY, 0.0F, UvLayout.SKIN_JACKET, 0.25F, detail),
                MeshBuilder.build(UvLayout.ARMOR, 0.58F, detail),
                MeshBuilder.build(UvLayout.ARMOR, 0.32F, detail));
        }
    }

    private static final class MeshBuilder {
        private static final int FRONT_COLUMNS = 29;
        private static final int[] FLAT_COLUMNS = new int[]{0, 1, 27, 28};
        private static final float[] ROW_Y = new float[]{0.0F, 0.5F, 1.0F, 1.5F, 2.0F, 2.5F, 3.0F, 3.5F, 4.0F, 4.5F, 5.1F, 5.5F, 5.8F, 6.15F, 6.45F, 6.7F, 7.0F, 8.5F, 12.0F};
        private static final float[] SHAPE_Y = new float[]{0.0F, 1.0F, 2.5F, 4.8F, 6.15F, 8.5F, 12.0F};
        private static final float[] HALF_WIDTH = new float[]{4.4F, 4.66F, 4.78F, 4.55F, 4.2F, 3.35F, 4.2F};
        private static final float[] FRONT_DEPTH = new float[]{-2.02F, -2.05F, -2.08F, -2.03F, -2.0F, -1.94F, -2.05F};
        private static final float[] BACK_DEPTH = new float[]{2.0F, 2.02F, 2.04F, 2.02F, 2.0F, 1.94F, 2.05F};
        private static final float BREAST_CENTER_X = 1.55F;
        private static final float BREAST_CENTER_Y = 2.55F;
        private static final float BREAST_INNER_RADIUS_X = 2.65F;
        private static final float BREAST_OUTER_RADIUS_X = 3.4F;
        private static final float BREAST_UPPER_RADIUS_Y = 2.85F;
        private static final float BREAST_LOWER_RADIUS_Y = 4.05F;
        private static final float BREAST_DEPTH = 2.7F;
        private static final double BLEND_POWER = 6.0;

        private static MeshModel build(UvLayout uv, float shellOffset, Detail detail) {
            return model(List.of(new MeshCube(buildQuads(uv, shellOffset, detail))));
        }

        private static MeshModel buildCombined(UvLayout firstUv, float firstOffset, UvLayout secondUv, float secondOffset, Detail detail) {
            return model(List.of(new MeshCube(buildQuads(firstUv, firstOffset, detail)), new MeshCube(buildQuads(secondUv, secondOffset, detail))));
        }

        private static MeshModel model(List<ModelPart.Cube> cubes) {
            return new MeshModel(new ModelPart(cubes, Map.of()));
        }

        private static List<Quad> buildQuads(UvLayout uv, float shellOffset, Detail detail) {
            SurfacePoint[][] front = buildSurface(true, shellOffset);
            SurfacePoint[][] back = buildSurface(false, shellOffset);
            List<Quad> quads = new ArrayList<>(640);

            for (int segment = 0; segment < detail.rows().length - 1; ++segment) {
                int topRow = detail.rows()[segment];
                int bottomRow = detail.rows()[segment + 1];
                int[] frontColumns = intersectsChest(ROW_Y[topRow], ROW_Y[bottomRow]) ? detail.columns() : FLAT_COLUMNS;
                addFrontRow(quads, front, topRow, bottomRow, frontColumns, uv);
                addBackRow(quads, back, topRow, bottomRow, uv);
                addSideQuad(quads, front, back, topRow, bottomRow, false, uv);
                addSideQuad(quads, front, back, topRow, bottomRow, true, uv);
            }

            addCapQuads(quads, front, back, detail.rows()[0], false, detail.columns(), uv);
            addCapQuads(quads, front, back, detail.rows()[detail.rows().length - 1], true, FLAT_COLUMNS, uv);
            return List.copyOf(quads);
        }

        private static void addFrontRow(List<Quad> quads, SurfacePoint[][] surface, int topRow, int bottomRow, int[] columns, UvLayout uv) {
            for (int segment = 0; segment < columns.length - 1; ++segment) {
                int left = columns[segment];
                int right = columns[segment + 1];
                quads.add(quad(meshVertex(surface[topRow][right], frontU(right), frontV(topRow), uv), meshVertex(surface[topRow][left], frontU(left), frontV(topRow), uv), meshVertex(surface[bottomRow][left], frontU(left), frontV(bottomRow), uv), meshVertex(surface[bottomRow][right], frontU(right), frontV(bottomRow), uv)));
            }
        }

        private static void addBackRow(List<Quad> quads, SurfacePoint[][] surface, int topRow, int bottomRow, UvLayout uv) {
            for (int segment = 0; segment < FLAT_COLUMNS.length - 1; ++segment) {
                int left = FLAT_COLUMNS[segment];
                int right = FLAT_COLUMNS[segment + 1];
                quads.add(quad(meshVertex(surface[topRow][left], backU(left), frontV(topRow), uv), meshVertex(surface[topRow][right], backU(right), frontV(topRow), uv), meshVertex(surface[bottomRow][right], backU(right), frontV(bottomRow), uv), meshVertex(surface[bottomRow][left], backU(left), frontV(bottomRow), uv)));
            }
        }

        private static boolean intersectsChest(float topY, float bottomY) {
            return bottomY > -0.29999995F && topY < 6.6000004F;
        }

        private static SurfacePoint[][] buildSurface(boolean frontSurface, float shellOffset) {
            SurfacePoint[][] base = new SurfacePoint[ROW_Y.length][FRONT_COLUMNS];

            for (int row = 0; row < ROW_Y.length; ++row) {
                float y = ROW_Y[row];
                float halfWidth = interpolate(y, HALF_WIDTH);
                float baseDepth = interpolate(y, frontSurface ? FRONT_DEPTH : BACK_DEPTH);

                for (int column = 0; column < FRONT_COLUMNS; ++column) {
                    float factor = factor(column);
                    float x = factor * halfWidth;
                    float z = frontSurface ? baseDepth - breastProjection(x, y) : baseDepth;
                    base[row][column] = new SurfacePoint(x, y, z, new Vector3f());
                }
            }

            calculateSmoothNormals(base, frontSurface);
            if (shellOffset == 0.0F) {
                return base;
            } else {
                SurfacePoint[][] expanded = new SurfacePoint[ROW_Y.length][FRONT_COLUMNS];

                for (int row = 0; row < ROW_Y.length; ++row) {
                    for (int column = 0; column < FRONT_COLUMNS; ++column) {
                        SurfacePoint point = base[row][column];
                        Vector3f normal = point.normal;
                        expanded[row][column] = new SurfacePoint(point.x + normal.x() * shellOffset, point.y + normal.y() * shellOffset, point.z + normal.z() * shellOffset, new Vector3f(normal));
                    }
                }

                return expanded;
            }
        }

        private static void calculateSmoothNormals(SurfacePoint[][] surface, boolean frontSurface) {
            int lastRow = surface.length - 1;
            int lastColumn = surface[0].length - 1;

            for (int row = 0; row <= lastRow; ++row) {
                int previousRow = Math.max(0, row - 1);
                int nextRow = Math.min(lastRow, row + 1);

                for (int column = 0; column <= lastColumn; ++column) {
                    int previousColumn = Math.max(0, column - 1);
                    int nextColumn = Math.min(lastColumn, column + 1);
                    Vector3f horizontal = difference(surface[row][nextColumn], surface[row][previousColumn]);
                    Vector3f vertical = difference(surface[nextRow][column], surface[previousRow][column]);
                    Vector3f normal = frontSurface ? vertical.cross(horizontal) : horizontal.cross(vertical);
                    if (normal.lengthSquared() < 1.0E-6F) {
                        normal.set(0.0F, 0.0F, frontSurface ? -1.0F : 1.0F);
                    } else {
                        normal.normalize();
                    }

                    surface[row][column].normal.set(normal);
                }
            }
        }

        private static float breastProjection(float x, float y) {
            float leftDx = x + BREAST_CENTER_X;
            float rightDx = x - BREAST_CENTER_X;
            float left = dome(leftDx, y - BREAST_CENTER_Y, leftDx < 0.0F ? BREAST_OUTER_RADIUS_X : BREAST_INNER_RADIUS_X);
            float right = dome(rightDx, y - BREAST_CENTER_Y, rightDx > 0.0F ? BREAST_OUTER_RADIUS_X : BREAST_INNER_RADIUS_X);
            if (left <= 0.0F) {
                return right * BREAST_DEPTH;
            } else if (right <= 0.0F) {
                return left * BREAST_DEPTH;
            } else {
                double blended = Math.pow(Math.pow(left, BLEND_POWER) + Math.pow(right, BLEND_POWER), 1.0 / BLEND_POWER);
                return (float) blended * BREAST_DEPTH;
            }
        }

        private static float dome(float dx, float dy, float radiusX) {
            float nx = dx / radiusX;
            float ny = dy / (dy < 0.0F ? BREAST_UPPER_RADIUS_Y : BREAST_LOWER_RADIUS_Y);
            float radiusSquared = nx * nx + ny * ny;
            if (radiusSquared >= 1.0F) {
                return 0.0F;
            } else {
                float inside = 1.0F - radiusSquared;
                return inside * inside * (3.0F - 2.0F * inside);
            }
        }

        private static void addSideQuad(List<Quad> quads, SurfacePoint[][] front, SurfacePoint[][] back, int topRow, int bottomRow, boolean east, UvLayout uv) {
            int column = east ? 28 : 0;
            SurfacePoint frontTop = front[topRow][column];
            SurfacePoint backTop = back[topRow][column];
            SurfacePoint frontBottom = front[bottomRow][column];
            SurfacePoint backBottom = back[bottomRow][column];
            float frontU = east ? 28.0F : 20.0F;
            float backU = east ? 32.0F : 16.0F;
            Vector3f normal = faceNormal(east ? backTop : frontTop, east ? frontTop : backTop, east ? frontBottom : backBottom);
            if (east) {
                quads.add(quad(meshVertex(backTop, backU, frontV(topRow), normal, uv), meshVertex(frontTop, frontU, frontV(topRow), normal, uv), meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv), meshVertex(backBottom, backU, frontV(bottomRow), normal, uv)));
            } else {
                quads.add(quad(meshVertex(frontTop, frontU, frontV(topRow), normal, uv), meshVertex(backTop, backU, frontV(topRow), normal, uv), meshVertex(backBottom, backU, frontV(bottomRow), normal, uv), meshVertex(frontBottom, frontU, frontV(bottomRow), normal, uv)));
            }
        }

        private static void addCapQuads(List<Quad> quads, SurfacePoint[][] front, SurfacePoint[][] back, int row, boolean bottom, int[] columns, UvLayout uv) {
            Vector3f normal = new Vector3f(0.0F, bottom ? 1.0F : -1.0F, 0.0F);

            for (int segment = 0; segment < columns.length - 1; ++segment) {
                int left = columns[segment];
                int right = columns[segment + 1];
                SurfacePoint frontLeft = front[row][left];
                SurfacePoint frontRight = front[row][right];
                SurfacePoint backLeft = back[row][left];
                SurfacePoint backRight = back[row][right];
                float uLeft = (bottom ? 32.0F : 24.0F) + factor(left) * 4.0F;
                float uRight = (bottom ? 32.0F : 24.0F) + factor(right) * 4.0F;
                float frontCapV = uv.topV + 4.0F;
                float backCapV = uv.topV;
                if (bottom) {
                    quads.add(quad(meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv), meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv), meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv), meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv)));
                } else {
                    quads.add(quad(meshVertexAbsoluteUv(backRight, uRight, backCapV, normal, uv), meshVertexAbsoluteUv(backLeft, uLeft, backCapV, normal, uv), meshVertexAbsoluteUv(frontLeft, uLeft, frontCapV, normal, uv), meshVertexAbsoluteUv(frontRight, uRight, frontCapV, normal, uv)));
                }
            }
        }

        private static int[] sequence(int length) {
            int[] values = new int[length];

            for (int index = 0; index < length; ++index) {
                values[index] = index;
            }

            return values;
        }

        private static float interpolate(float y, float[] values) {
            if (y <= SHAPE_Y[0]) {
                return values[0];
            } else {
                for (int i = 1; i < SHAPE_Y.length; ++i) {
                    if (y <= SHAPE_Y[i]) {
                        float progress = (y - SHAPE_Y[i - 1]) / (SHAPE_Y[i] - SHAPE_Y[i - 1]);
                        return values[i - 1] + (values[i] - values[i - 1]) * progress;
                    }
                }

                return values[values.length - 1];
            }
        }

        private static float factor(int column) {
            return -1.0F + 2.0F * (float) column / 28.0F;
        }

        private static float frontU(int column) {
            return 24.0F + factor(column) * 4.0F;
        }

        private static float backU(int column) {
            return 36.0F - factor(column) * 4.0F;
        }

        private static float frontV(int row) {
            return ROW_Y[row];
        }

        private static Vector3f difference(SurfacePoint a, SurfacePoint b) {
            return new Vector3f(a.x - b.x, a.y - b.y, a.z - b.z);
        }

        private static Vector3f faceNormal(SurfacePoint a, SurfacePoint b, SurfacePoint c) {
            Vector3f ab = difference(b, a);
            Vector3f ac = difference(c, a);
            Vector3f normal = ab.cross(ac);
            return normal.lengthSquared() < 1.0E-6F ? new Vector3f(0.0F, 0.0F, -1.0F) : normal.normalize();
        }

        private static MeshVertex meshVertex(SurfacePoint point, float u, float v, UvLayout uv) {
            return meshVertex(point, u, v, point.normal, uv);
        }

        private static MeshVertex meshVertex(SurfacePoint point, float u, float v, Vector3f normal, UvLayout uv) {
            return new MeshVertex(point.x, point.y, point.z, u / uv.textureWidth, (uv.frontV + v) / uv.textureHeight, normal.x(), normal.y(), normal.z());
        }

        private static MeshVertex meshVertexAbsoluteUv(SurfacePoint point, float u, float v, Vector3f normal, UvLayout uv) {
            return new MeshVertex(point.x, point.y, point.z, u / uv.textureWidth, v / uv.textureHeight, normal.x(), normal.y(), normal.z());
        }

        private static Quad quad(MeshVertex a, MeshVertex b, MeshVertex c, MeshVertex d) {
            return new Quad(a, b, c, d);
        }

        private record Detail(int[] columns, int[] rows) {
            private static final Detail FULL = new Detail(sequence(FRONT_COLUMNS), sequence(ROW_Y.length));
            private static final Detail MEDIUM = new Detail(new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28}, new int[]{0, 1, 3, 5, 7, 9, 10, 12, 14, 16, 17, 18});
            private static final Detail FAR = new Detail(new int[]{0, 4, 8, 12, 16, 20, 24, 28}, new int[]{0, 2, 5, 8, 10, 13, 16, 17, 18});
            private static final Detail CROWD = new Detail(new int[]{0, 7, 10, 14, 18, 21, 28}, new int[]{0, 5, 8, 10, 13, 16, 18});
        }

        private static final class MeshCube extends ModelPart.Cube {
            private final MeshVertex[] vertices;
            private final Vector3f transformedNormal = new Vector3f();
            private final Vector3f transformedPosition = new Vector3f();

            private MeshCube(List<Quad> quads) {
                super(0, 0, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, Set.of());
                this.vertices = new MeshVertex[quads.size() * 4];
                int index = 0;

                for (Quad quad : quads) {
                    for (MeshVertex point : quad.points) {
                        this.vertices[index++] = point;
                    }
                }
            }

            @Override
            public void compile(PoseStack.Pose pose, VertexConsumer output, int lightCoords, int overlayCoords, int color) {
                for (MeshVertex point : this.vertices) {
                    pose.transformNormal(point.nx(), point.ny(), point.nz(), this.transformedNormal);
                    pose.pose().transformPosition(point.x() / 16.0F, point.y() / 16.0F, point.z() / 16.0F, this.transformedPosition);
                    output.addVertex(this.transformedPosition.x(), this.transformedPosition.y(), this.transformedPosition.z(), color, point.u(), point.v(), overlayCoords, lightCoords, this.transformedNormal.x(), this.transformedNormal.y(), this.transformedNormal.z());
                }
            }
        }

        private static final class SurfacePoint {
            private final float x;
            private final float y;
            private final float z;
            private final Vector3f normal;

            private SurfacePoint(float x, float y, float z, Vector3f normal) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.normal = normal;
            }
        }

        private record MeshVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        }

        private static final class Quad {
            private final MeshVertex[] points;

            private Quad(MeshVertex a, MeshVertex b, MeshVertex c, MeshVertex d) {
                this.points = new MeshVertex[]{a, b, c, d};
            }
        }
    }
}
//?}
//? if <1.21.9 {
/*public final class AutismFemaleBodyRenderer {
    private AutismFemaleBodyRenderer() {
    }

    public static void initialize() {
    }
}*/
//?}

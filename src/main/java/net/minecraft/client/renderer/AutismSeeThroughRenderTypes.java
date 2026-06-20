package net.minecraft.client.renderer;

//? if >=1.21.6 && <1.21.11 {
/*public final class AutismSeeThroughRenderTypes {
    private static final RenderType FILL = buildFill();
    private static final RenderType LINES = buildLines();

    private AutismSeeThroughRenderTypes() {
    }

    public static RenderType fill() {
        return FILL != null ? FILL : RenderType.debugQuads();
    }

    public static RenderType lines() {
        return LINES != null ? LINES : RenderType.lines();
    }

    private static RenderType buildFill() {
        try {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation("pipeline/autism_storage_esp_fill_see_through")
                .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                .build();
            return RenderType.create("autism_storage_esp_fill_see_through", 1536, pipeline,
                RenderType.CompositeState.builder().createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }

    private static RenderType buildLines() {
        try {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation("pipeline/autism_storage_esp_lines_see_through")
                .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES)
                .build();
            return RenderType.create("autism_storage_esp_lines_see_through", 1536, pipeline,
                RenderType.CompositeState.builder()
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(1.5)))
                    .createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }
}
*///?} elif >=1.21.5 {
/*public final class AutismSeeThroughRenderTypes {
    private static final RenderType FILL = buildFill();
    private static final RenderType LINES = buildLines();

    private AutismSeeThroughRenderTypes() {
    }

    public static RenderType fill() {
        return FILL != null ? FILL : RenderType.debugQuads();
    }

    public static RenderType lines() {
        return LINES != null ? LINES : RenderType.lines();
    }

    private static RenderType buildFill() {
        try {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation("pipeline/autism_storage_esp_fill_see_through")
                .withUniform("ModelViewMat", com.mojang.blaze3d.shaders.UniformType.MATRIX4X4)
                .withUniform("ProjMat", com.mojang.blaze3d.shaders.UniformType.MATRIX4X4)
                .withUniform("ColorModulator", com.mojang.blaze3d.shaders.UniformType.VEC4)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                .build();
            return RenderType.create("autism_storage_esp_fill_see_through", 1536, pipeline,
                RenderType.CompositeState.builder().createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }

    private static RenderType buildLines() {
        try {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                .withLocation("pipeline/autism_storage_esp_lines_see_through")
                .withUniform("ModelViewMat", com.mojang.blaze3d.shaders.UniformType.MATRIX4X4)
                .withUniform("ProjMat", com.mojang.blaze3d.shaders.UniformType.MATRIX4X4)
                .withUniform("ColorModulator", com.mojang.blaze3d.shaders.UniformType.VEC4)
                .withUniform("FogStart", com.mojang.blaze3d.shaders.UniformType.FLOAT)
                .withUniform("FogEnd", com.mojang.blaze3d.shaders.UniformType.FLOAT)
                .withUniform("FogShape", com.mojang.blaze3d.shaders.UniformType.INT)
                .withUniform("FogColor", com.mojang.blaze3d.shaders.UniformType.VEC4)
                .withUniform("LineWidth", com.mojang.blaze3d.shaders.UniformType.FLOAT)
                .withUniform("ScreenSize", com.mojang.blaze3d.shaders.UniformType.VEC2)
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL, com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES)
                .build();
            return RenderType.create("autism_storage_esp_lines_see_through", 1536, pipeline,
                RenderType.CompositeState.builder()
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(1.5)))
                    .createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }
}
*///?} elif <1.21.5 {
/*public final class AutismSeeThroughRenderTypes {
    private static final RenderType FILL = buildFill();
    private static final RenderType LINES = buildLines();

    private AutismSeeThroughRenderTypes() {
    }

    public static RenderType fill() {
        return FILL != null ? FILL : RenderType.debugQuads();
    }

    public static RenderType lines() {
        return LINES != null ? LINES : RenderType.lines();
    }

    private static RenderType buildFill() {
        try {
            return RenderType.create("autism_storage_esp_fill_see_through",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }

    private static RenderType buildLines() {
        try {
            return RenderType.create("autism_storage_esp_lines_see_through",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, 1536,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(1.5)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));
        } catch (Throwable t) {
            return null;
        }
    }
}
*///?} else {
public final class AutismSeeThroughRenderTypes {
    private AutismSeeThroughRenderTypes() {
    }
}
//?}

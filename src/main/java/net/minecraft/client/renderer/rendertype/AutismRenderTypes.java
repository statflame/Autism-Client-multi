package net.minecraft.client.renderer.rendertype;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}

public final class AutismRenderTypes {
    //? if >=1.21.11 {
    private static final RenderPipeline STORAGE_ESP_FILL_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/storage_esp_fill_see_through"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .build();

    private static final RenderPipeline STORAGE_ESP_LINES_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.fromNamespaceAndPath("autismclient", "pipeline/storage_esp_lines_see_through"))
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withUniform("Fog", UniformType.UNIFORM_BUFFER)
        .withUniform("Globals", UniformType.UNIFORM_BUFFER)
        .withVertexShader("core/rendertype_lines")
        .withFragmentShader("core/rendertype_lines")
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
        .build();

    private static final RenderType STORAGE_ESP_FILL = RenderType.create(
        "autism_storage_esp_fill_see_through",
        RenderSetup.builder(STORAGE_ESP_FILL_PIPELINE).sortOnUpload().createRenderSetup()
    );

    private static final RenderType STORAGE_ESP_LINES = RenderType.create(
        "autism_storage_esp_lines_see_through",
        RenderSetup.builder(STORAGE_ESP_LINES_PIPELINE).createRenderSetup()
    );
    //?}

    private AutismRenderTypes() {
    }

    public static RenderType storageEspFillSeeThrough() {
        //? if >=1.21.11 {
        return STORAGE_ESP_FILL;
        //?} else {
        /*return net.minecraft.client.renderer.AutismSeeThroughRenderTypes.fill();*/
        //?}
    }

    public static RenderType storageEspLinesSeeThrough() {
        //? if >=1.21.11 {
        return STORAGE_ESP_LINES;
        //?} else {
        /*return net.minecraft.client.renderer.AutismSeeThroughRenderTypes.lines();*/
        //?}
    }
}

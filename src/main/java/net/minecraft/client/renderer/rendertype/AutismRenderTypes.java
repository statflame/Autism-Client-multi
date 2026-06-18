package net.minecraft.client.renderer.rendertype;

//? if <1.21.11 {
/*import net.minecraft.client.renderer.RenderType;*/
//?}

public final class AutismRenderTypes {
    private AutismRenderTypes() {
    }

    public static RenderType storageEspFillSeeThrough() {
        //? if >=1.21.11 {
        return RenderTypes.debugQuads();
        //?} else {
        /*return RenderType.debugQuads();*/
        //?}
    }

    public static RenderType storageEspLinesSeeThrough() {
        //? if >=1.21.11 {
        return RenderTypes.lines();
        //?} else {
        /*return RenderType.lines();*/
        //?}
    }
}

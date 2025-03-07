package net.superkat.tidal.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import org.joml.Matrix4f;

import java.util.List;

public class WaveRenderer {
    public TidalWaveHandler handler;
    public ClientWorld world;

    public float u = 0f;

    public WaveRenderer(TidalWaveHandler handler, ClientWorld world) {
        this.handler = handler;
        this.world = world;
    }

    public void render(WorldRenderContext context) {
        ObjectArrayList<Wave> waves = this.handler.getWaves();
        if(waves == null || waves.isEmpty() || waves.size() < 2) return;

        LightmapTextureManager lightmapTextureManager = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager();
        float tickDelta = context.tickCounter().getTickDelta(false);
        Camera camera = context.camera();

        lightmapTextureManager.enable();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeTripwireProgram);
        RenderSystem.setShaderTexture(0, Identifier.of(Tidal.MOD_ID, "textures/wave/wave7.png"));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (Wave wave : waves) {
            renderWave(wave);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        lightmapTextureManager.disable();

    }

    public void renderWave(Wave wave) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        if(buffer == null) return;

        List<WaveSegment> waveSegments = wave.getWaveSegments();
        if(waveSegments.isEmpty() || waveSegments.size() < 2) return;

        MatrixStack matrices = new MatrixStack();
        renderList(matrices, buffer, wave.getWaveSegments());

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderList(MatrixStack matrixStack, BufferBuilder vertexConsumer, List<WaveSegment> waves) {
        this.u = 0;
        matrixStack.push();
        int curvePoints = 1;

        float width = 1f;

        for (int i = 0; i < waves.size() - 1; i++) {
            WaveSegment prevWaveSegment = waves.get(i != 0 ? i - 1 : 0);
            WaveSegment originWaveSegment = waves.get(i);
            WaveSegment targetWaveSegment = waves.get(i + 1);
            WaveSegment nextWaveSegment = waves.get(i + 2 <= waves.size() - 1 ? i + 2 : i + 1);

            float red = originWaveSegment.red;
            float green = originWaveSegment.green;
            float blue = originWaveSegment.blue;

            //Catmull spline points
            Vec3d prevPoint = prevWaveSegment.getPos();
            Vec3d originPoint = originWaveSegment.getPos();
            Vec3d targetPoint = targetWaveSegment.getPos();
            Vec3d nextPoint = nextWaveSegment.getPos();

            double distFromTarget = originPoint.distanceTo(targetPoint);
            curvePoints = (int) Math.ceil(distFromTarget);
            //Extra point used for catmull spline during smoothing
            Vec3d prevCurvePoint = prevPoint;

            //light adjustment - interpolates between the origin and target points
            int originBlockLight = getLightLevel(LightType.BLOCK, originPoint);
            int targetBlockLight = getLightLevel(LightType.BLOCK, targetPoint);
            int originSkyLight = getLightLevel(LightType.SKY, originPoint);
            int targetSkyLight = getLightLevel(LightType.SKY, targetPoint);

            for (int j = 0; j <= curvePoints; j++) {
                float delta = (float) j / curvePoints;

                //easing/interpolation
                float curveX = MathHelper.catmullRom(delta, (float) prevPoint.getX(), (float) originPoint.getX(), (float) targetPoint.getX(), (float) nextPoint.getX());
                float curveY = MathHelper.catmullRom(delta, (float) prevPoint.getY(), (float) originPoint.getY(), (float) targetPoint.getY(), (float) nextPoint.getY());
                float curveZ = MathHelper.catmullRom(delta, (float) prevPoint.getZ(), (float) originPoint.getZ(), (float) targetPoint.getZ(), (float) nextPoint.getZ());
                Vec3d curvePoint = new Vec3d(curveX, curveY, curveZ);

                //light adjustment per segment
                int blockLight = MathHelper.lerp(delta, originBlockLight, targetBlockLight);
                int skyLight = MathHelper.lerp(delta, originSkyLight, targetSkyLight);
                int light = LightmapTextureManager.pack(blockLight, skyLight);

                if(delta > 0f) {
                    renderSegment(matrixStack, vertexConsumer, curvePoint, prevCurvePoint, originWaveSegment.yaw, red, green, blue, 1f, light, width);
                }
                prevCurvePoint = curvePoint;
            }
        }
        matrixStack.pop();
    }

    /**
     * Translates and rotates the MatrixStack. Also calculates the to-be rendered line's width and length.
     *
     * @param matrixStack The MatrixStack used for rendering.
     * @param vertexConsumer The VertexConsumer used for rendering.
     * @param origin The starting point to render from.
     * @param target The ending point to render to.
     * @param alpha The rendered segment's opacity/alpha value.
     */
    private void renderSegment(MatrixStack matrixStack, VertexConsumer vertexConsumer, Vec3d origin, Vec3d target, float segmentYaw, float red, float green, float blue, float alpha, int light, float width) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d transformedMatrixPos = origin.subtract(camera.getPos());
        matrixStack.push();

        //offsets to the origin's pos
        matrixStack.translate(transformedMatrixPos.x, transformedMatrixPos.y, transformedMatrixPos.z);

        //calculates length
        float length = (float) origin.distanceTo(target);

        //rotates towards target from origin
        Vec3d transformedPos = target.subtract(origin);
        transformedPos = transformedPos.normalize();
        float rightAngle = (float) Math.toRadians(90);
        float n = (float)Math.acos(transformedPos.y);
        float o = (float)Math.atan2(transformedPos.z, transformedPos.x);

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(rightAngle - o))); //rotates left/right
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.toDegrees(rightAngle + n))); //rotates up/down
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180)); //rotate upside to be viewed from the top

//        boolean flipTexture = Math.abs(segmentYaw) >= 90; //acquired by hoping for the best (aka trial & error)
        boolean flipTexture = false;
        //depends on the start/end position?
        int v1 = flipTexture ? 1 : 0;
        int v2 = flipTexture ? 0 : 1;

        drawTriangle(matrixStack.peek().getPositionMatrix(), vertexConsumer, width, -length, red, green, blue, alpha, light, v1, v2);

        matrixStack.pop();
    }

    /**
     * Renders a triangle with a specific width and length. For Contrail rendering, this is called after the MatrixStack has been translated/rotated.
     *
     * @param matrix The MatrixStack's Position Matrix used for rendering.
     * @param vertexConsumer The VertexConsumer used for rendering
     * @param width The rendered rectangle's width. Should be determined by a config option.
     * @param length The rendered rectangle's length. Should be determined by the length from the origin point to the target point in {@link #renderSegment(MatrixStack, VertexConsumer, Vec3d, Vec3d, float, float, float, float, float, int, float)}.
     * @param alpha The rendered rectangle's opacity/alpha value.
     */
    private void drawTriangle(Matrix4f matrix, VertexConsumer vertexConsumer, float width, float length, float red, float green, float blue, float alpha, int light, int v1, int v2) {
        this.u += Math.abs(length);

        //dividing the width by 2 to ensure that the width given is accurately rendered
        vertexConsumer.vertex(matrix, width / 2f, 0, length)
                .color(red, green, blue, alpha)
                .texture(u, v1).light(light);
        vertexConsumer.vertex(matrix, -width / 2f, 0, length)
                .color(red, green, blue, alpha)
                .texture(u, v2).light(light);
    }

    private int getLightLevel(LightType lightType, Vec3d pos) {
        BlockPos blockPos = new BlockPos((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());
        if(MinecraftClient.getInstance().world != null) {
            return MinecraftClient.getInstance().world.getLightLevel(lightType, blockPos);
        }
        return LightmapTextureManager.pack(7, 15);
    }

}

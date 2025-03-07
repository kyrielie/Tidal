package net.superkat.tidal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.particles.WaveParticle;
import net.superkat.tidal.particles.WhiteWaveParticle;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;

public class TidalClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        ParticleFactoryRegistry.getInstance().register(TidalParticles.WAVE_PARTICLE, WaveParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.WHITE_WAVE_PARTICLE, WhiteWaveParticle.Factory::new);

        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_WATERBODY_PARTICLE, DebugWaterParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_SHORELINE_PARTICLE, DebugShoreParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(TidalParticles.DEBUG_WAVEMOVEMENT_PARTICLE, DebugWaveMovementParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.addChunk(chunk);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.removeChunk(chunk);
        });

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.world;
            tidalWorld.tidal$tidalWaveHandler().waterHandler.onBlockUpdate(pos, state);
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
            tidalWorld.tidal$tidalWaveHandler().waterHandler.rebuild();
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if(context.world() == null) return;
            TidalWorld tidalWorld = (TidalWorld) context.world();
            tidalWorld.tidal$tidalWaveHandler().render(context);
        });

//        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
//            Vec3d origin = new Vec3d(0, -55, 0);
//            Camera camera = context.camera();
//            Vec3d transformedPos = origin.subtract(camera.getPos());
//            MatrixStack matrixStack = new MatrixStack();
//            matrixStack.push();
//            matrixStack.translate(transformedPos.x, transformedPos.y, transformedPos.z);
//            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
//
//            Matrix4f posMatrix = matrixStack.peek().getPositionMatrix();
//
//            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
//            RenderSystem.depthMask(true);
//            RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
////            RenderSystem.setShaderTexture(0, Identifier.of(Tidal.MOD_ID, "textures/wave/wave7.png"));
////            Sprite sprite = SPRITE.getSprite();
//            RenderSystem.enableBlend();
//            RenderSystem.defaultBlendFunc();
//            buffer.vertex(posMatrix, 0, 1, 0)
//                    .color(1f, 1f, 1f, 0.5f)
////                    .texture(sprite.getMinU(), sprite.getMinV())
//                    .texture(0, 0)
//                    .light(LightmapTextureManager.pack(15, 15));
//            buffer.vertex(posMatrix, 0, 0, 0)
//                    .color(1f, 1f, 1f, 0.5f)
////                    .texture(sprite.getMaxU(), sprite.getMinV())
//                    .texture(1, 0)
//                    .light(LightmapTextureManager.pack(15, 15));
//            buffer.vertex(posMatrix, 1, 0, 0)
//                    .color(1f, 1f, 1f, 0.5f)
////                    .texture(sprite.getMaxU(), sprite.getMaxV())
//                    .texture(1, 1)
//                    .light(LightmapTextureManager.pack(15, 15));
//            buffer.vertex(posMatrix, 1, 1, 0)
//                    .color(1f, 1f, 1f, 0.5f)
////                    .texture(sprite.getMinU(), sprite.getMaxV())
//                    .texture(0, 1)
//                    .light(LightmapTextureManager.pack(15, 15));
//
//            BufferRenderer.drawWithGlobalProgram(buffer.end());
//
//
//            matrixStack.pop();
//
//            RenderSystem.depthMask(true);
//            RenderSystem.disableBlend();
//        });
    }

}

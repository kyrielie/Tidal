package net.superkat.tidal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.minecraft.client.MinecraftClient;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;

public class TidalClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_WATERBODY_PARTICLE, DebugWaterBodyParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_SHORELINE_PARTICLE, DebugShorelineParticle.Factory::new);

        //Called after joining a world, or changing dimensions
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            TidalWorld tidalWorld = (TidalWorld) world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
        });

        //Called when an individual block is updated(placed, broken, state changed, etc.)
        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
//            System.out.println(state.getBlock().getName().toString() + ": " + pos.toShortString() + " (" + updates++ + ")");
        });

        //Called when the chunks are reloaded(f3+a, resource pack change, etc.)
        InvalidateRenderStateCallback.EVENT.register(() -> {
            //actually have to check for null stuff here because this could be in the title screen I think
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.world == null || client.player == null) return;
            TidalWorld tidalWorld = (TidalWorld) client.world;
            tidalWorld.tidal$tidalWaveHandler().reloadNearbyChunks();
            tidalWorld.tidal$tidalWaveHandler().waterBodyHandler.clear();
            tidalWorld.tidal$tidalWaveHandler().waterBodyHandler.build(client.player);
        });


    }
}

package net.superkat.tidal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.superkat.tidal.event.ClientBlockUpdateEvent;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;

public class TidalClient implements ClientModInitializer {
    public static int updates = 0;

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_WATERBODY_PARTICLE, DebugWaterBodyParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_SHORELINE_PARTICLE, DebugShorelineParticle.Factory::new);

        ClientBlockUpdateEvent.BLOCK_UPDATE.register((pos, state) -> {
//            System.out.println(state.getBlock().getName().toString() + ": " + pos.toShortString() + " (" + updates++ + ")");
        });

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
//            System.out.println(chunk);
        });
    }
}

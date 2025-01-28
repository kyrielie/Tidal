package net.superkat.tidal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.superkat.tidal.particles.DebugShorelineParticle;
import net.superkat.tidal.particles.DebugWaterBodyParticle;

public class TidalClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_WATERBODY_PARTICLE, DebugWaterBodyParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Tidal.DEBUG_SHORELINE_PARTICLE, DebugShorelineParticle.Factory::new);
    }
}

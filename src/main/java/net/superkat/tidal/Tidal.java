package net.superkat.tidal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.superkat.tidal.duck.TidalWorld;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import net.superkat.tidal.particles.debug.DebugWaveMovementParticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tidal implements ModInitializer {
	public static final String MOD_ID = "tidal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ParticleType<DebugWaterParticle.DebugWaterParticleEffect> DEBUG_WATERBODY_PARTICLE = FabricParticleTypes.complex(
			DebugWaterParticle.DebugWaterParticleEffect.CODEC,
			DebugWaterParticle.DebugWaterParticleEffect.PACKET_CODEC
	);
	public static final ParticleType<DebugShoreParticle.DebugShoreParticleEffect> DEBUG_SHORELINE_PARTICLE = FabricParticleTypes.complex(
			DebugShoreParticle.DebugShoreParticleEffect.CODEC,
			DebugShoreParticle.DebugShoreParticleEffect.PACKET_CODEC
	);
	public static final ParticleType<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> DEBUG_WAVEMOVEMENT_PARTICLE = FabricParticleTypes.complex(
			DebugWaveMovementParticle.DebugWaveMovementParticleEffect.CODEC,
			DebugWaveMovementParticle.DebugWaveMovementParticleEffect.PACKET_CODEC
	);

	@Override
	public void onInitialize() {
		ClientTickEvents.END_WORLD_TICK.register(clientWorld -> {
			TidalWorld tidalWorld = (TidalWorld) clientWorld;
			tidalWorld.tidal$tidalWaveHandler().tick();
		});

		Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, "debug_waterbody_particle"), DEBUG_WATERBODY_PARTICLE);
		Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, "debug_shoreline_particle"), DEBUG_SHORELINE_PARTICLE);
		Registry.register(Registries.PARTICLE_TYPE, Identifier.of(MOD_ID, "debug_wavemovement_particle"), DEBUG_WAVEMOVEMENT_PARTICLE);
	}
}
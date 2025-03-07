package net.superkat.tidal.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.superkat.tidal.TidalParticles;

import java.util.List;

public class WaveParticleEffect extends AbstractWaveParticleEffect {

    public static final MapCodec<WaveParticleEffect> CODEC = createCodec(WaveParticleEffect::new);

    public static final PacketCodec<RegistryByteBuf, WaveParticleEffect> PACKET_CODEC = createPacketCodec(WaveParticleEffect::new);

//    public WaveParticleEffect(float yaw, float speed, float scale, int width, int lifetime) {
    public WaveParticleEffect(List<Float> yaw, float speed, float scale, List<BlockPos> pos, int lifetime) {
//        super(yaw, speed, scale, width, lifetime);
        super(yaw, speed, scale, pos, lifetime);
    }

    public WaveParticleEffect(AbstractWaveParticleEffect params) {
        super(params);
    }

    @Override
    public ParticleType<?> getType() {
        return TidalParticles.WAVE_PARTICLE;
    }
}

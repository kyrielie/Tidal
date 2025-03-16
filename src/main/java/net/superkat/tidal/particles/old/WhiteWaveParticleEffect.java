package net.superkat.tidal.particles.old;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.superkat.tidal.TidalParticles;

import java.util.List;

public class WhiteWaveParticleEffect extends AbstractWaveParticleEffect{

    public static final MapCodec<WhiteWaveParticleEffect> CODEC = createCodec(WhiteWaveParticleEffect::new);
    public static final PacketCodec<RegistryByteBuf, WhiteWaveParticleEffect> PACKET_CODEC = createPacketCodec(WhiteWaveParticleEffect::new);

//    public WhiteWaveParticleEffect(float yaw, float speed, float scale, int width, int lifetime) {
//        super(yaw, speed, scale, width, lifetime);
//    }
    public WhiteWaveParticleEffect(List<Float> yaw, float speed, float scale, List<BlockPos> pos, int lifetime) {
        super(yaw, speed, scale, pos, lifetime);
    }

    public WhiteWaveParticleEffect(AbstractWaveParticleEffect params) {
        super(params);
    }

    @Override
    public ParticleType<?> getType() {
        return TidalParticles.WHITE_WAVE_PARTICLE;
    }
}

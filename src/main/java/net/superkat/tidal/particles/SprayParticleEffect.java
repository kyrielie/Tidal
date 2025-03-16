package net.superkat.tidal.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.superkat.tidal.TidalParticles;

import java.util.function.BiFunction;

public class SprayParticleEffect implements ParticleEffect {

    protected static <T extends SprayParticleEffect> MapCodec<T> createCodec(BiFunction<Float, Float, T> particle) {
        return RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.FLOAT.fieldOf("yaw").forGetter(SprayParticleEffect::getYaw),
                        Codec.FLOAT.fieldOf("intensity").forGetter(SprayParticleEffect::getIntensity)
                ).apply(instance, particle)
        );
    }

    protected static <T extends SprayParticleEffect> PacketCodec<RegistryByteBuf, T> createPacketCodec(BiFunction<Float, Float, T> particle) {
        return PacketCodec.tuple(
                PacketCodecs.FLOAT, T::getYaw,
                PacketCodecs.FLOAT, T::getIntensity,
                particle
        );
    }

    public static final MapCodec<SprayParticleEffect> CODEC = createCodec(SprayParticleEffect::new);
    public static final PacketCodec<RegistryByteBuf, SprayParticleEffect> PACKET_CODEC = createPacketCodec(SprayParticleEffect::new);

    protected final float yaw;
    protected final float intensity;

    public SprayParticleEffect(float yaw, float intensity) {
        this.yaw = yaw;
        this.intensity = intensity;
    }
    public float getYaw() {
        return yaw;
    }

    public float getIntensity() {
        return intensity;
    }

    @Override
    public ParticleType<?> getType() {
        return TidalParticles.SPRAY_PARTICLE;
    }
}

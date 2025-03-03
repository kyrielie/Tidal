package net.superkat.tidal.particles;

import com.mojang.datafixers.util.Function5;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public abstract class AbstractWaveParticleEffect implements ParticleEffect {

    public static <T extends AbstractWaveParticleEffect>MapCodec<T> createCodec(Function5<Float, Float, Float, List<BlockPos>, Integer, T> particle) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            Codecs.POSITIVE_FLOAT.fieldOf("yaw").forGetter(effect -> effect.yaw),
                            Codecs.POSITIVE_FLOAT.fieldOf("speed").forGetter(effect -> effect.speed),
                            Codecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(effect -> effect.scale),
//                            Codecs.POSITIVE_INT.fieldOf("width").forGetter(effect -> effect.width),
                            BlockPos.CODEC.listOf().fieldOf("positions").forGetter(effect -> effect.positions),
                            Codecs.POSITIVE_INT.fieldOf("lifetime").forGetter(effect -> effect.lifetime)
                    ).apply(instance, particle)
        );
    }

    public static <T extends AbstractWaveParticleEffect>PacketCodec<RegistryByteBuf, T> createPacketCodec(Function5<Float, Float, Float, List<BlockPos>, Integer, T> particle) {
        return PacketCodec.tuple(
                PacketCodecs.FLOAT, T::getYaw,
                PacketCodecs.FLOAT, T::getSpeed,
                PacketCodecs.FLOAT, T::getScale,
                BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), T::getPositions,
//                PacketCodecs.INTEGER, T::getWidth,
                PacketCodecs.INTEGER, T::getLifetime,
                particle
        );
    }

//    public static final MapCodec<WaveParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
//            instance -> instance.group(
//                            Codecs.POSITIVE_FLOAT.fieldOf("yaw").forGetter(effect -> effect.yaw),
//                            Codecs.POSITIVE_FLOAT.fieldOf("speed").forGetter(effect -> effect.speed),
//                            Codecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(effect -> effect.scale),
//                            Codecs.POSITIVE_INT.fieldOf("width").forGetter(effect -> effect.width),
//                            Codecs.POSITIVE_INT.fieldOf("lifetime").forGetter(effect -> effect.lifetime)
//                    )
//                    .apply(instance, WaveParticleEffect::new)
//    );
//
//    public static final PacketCodec<RegistryByteBuf, WaveParticleEffect> PACKET_CODEC = PacketCodec.tuple(
//            PacketCodecs.FLOAT, WaveParticleEffect::getYaw,
//            PacketCodecs.FLOAT, WaveParticleEffect::getSpeed,
//            PacketCodecs.FLOAT, WaveParticleEffect::getScale,
//            PacketCodecs.INTEGER, WaveParticleEffect::getLifetime,
//            PacketCodecs.INTEGER, WaveParticleEffect::getWidth,
//            WaveParticleEffect::new
//    );

    protected final float yaw;
    protected final float speed;
    protected final float scale;
    protected final int width = 1;
    protected final int lifetime;
    protected final List<BlockPos> positions;

//    public AbstractWaveParticleEffect(float yaw, float speed, float scale, int width, int lifetime) {
    public AbstractWaveParticleEffect(float yaw, float speed, float scale, List<BlockPos> positions, int lifetime) {
        this.yaw = yaw;
        this.speed = speed;
        this.scale = scale;
//        this.width = width;
        this.lifetime = lifetime;
        this.positions = positions;
    }

    public AbstractWaveParticleEffect(AbstractWaveParticleEffect params) {
//        this(params.getYaw(), params.getSpeed(), params.getScale(), params.getWidth(), params.getLifetime());
        this(params.getYaw(), params.getSpeed(), params.getScale(), params.getPositions(), params.getLifetime());
    }

    public float getYaw() {
        return yaw;
    }

    public float getSpeed() {
        return speed;
    }

    public float getScale() {
        return scale;
    }

    public int getWidth() {
        return width;
    }

    public int getLifetime() {
        return lifetime;
    }

    public List<BlockPos> getPositions() {
        return positions;
    }
}

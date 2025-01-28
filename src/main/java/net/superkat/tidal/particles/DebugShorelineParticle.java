package net.superkat.tidal.particles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.AbstractDustParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.dynamic.Codecs;
import net.superkat.tidal.Tidal;
import org.joml.Vector3f;

public class DebugShorelineParticle extends DebugAbstractColoredParticle<DebugShorelineParticle.DebugShorelineParticleEffect> {

    public DebugShorelineParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugShorelineParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.red = this.darken(parameters.color.x(), 0.8f);
        this.green = this.darken(parameters.color.y(), 0.8f);
        this.blue = this.darken(parameters.color.z(), 0.8f);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugShorelineParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(DebugShorelineParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugShorelineParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugShorelineParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugShorelineParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color), SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale)
                        )
                        .apply(instance, DebugShorelineParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugShorelineParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR3F, effect -> effect.color, PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale, DebugShorelineParticleEffect::new
        );
        private final Vector3f color;

        public DebugShorelineParticleEffect(Vector3f color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugShorelineParticleEffect> getType() {
            return Tidal.DEBUG_SHORELINE_PARTICLE;
        }
    }

}

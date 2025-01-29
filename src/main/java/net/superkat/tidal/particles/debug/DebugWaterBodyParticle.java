package net.superkat.tidal.particles.debug;

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

public class DebugWaterBodyParticle extends DebugAbstractColoredParticle<DebugWaterBodyParticle.DebugWaterBodyParticleEffect> {

    public DebugWaterBodyParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugWaterBodyParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);
        this.red = this.darken(parameters.color.x(), 1);
        this.green = this.darken(parameters.color.y(), 1);
        this.blue = this.darken(parameters.color.z(), 1);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugWaterBodyParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(DebugWaterBodyParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugWaterBodyParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaterBodyParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugWaterBodyParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color), SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale)
                        )
                        .apply(instance, DebugWaterBodyParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugWaterBodyParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR3F, effect -> effect.color, PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale, DebugWaterBodyParticleEffect::new
        );
        private final Vector3f color;

        public DebugWaterBodyParticleEffect(Vector3f color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugWaterBodyParticleEffect> getType() {
            return Tidal.DEBUG_WATERBODY_PARTICLE;
        }
    }

}

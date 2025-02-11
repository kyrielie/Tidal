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

public class DebugWaveMovementParticle extends DebugAbstractColoredParticle<DebugWaveMovementParticle.DebugWaveMovementParticleEffect> {
    private final double startX;
    private final double startY;
    private final double startZ;
    public DebugWaveMovementParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, DebugWaveMovementParticleEffect parameters, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, parameters, spriteProvider);

        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.prevPosX = x + velocityX;
        this.prevPosY = y + velocityY;
        this.prevPosZ = z + velocityZ;
        this.x = this.prevPosX;
        this.y = this.prevPosY;
        this.z = this.prevPosZ;

        this.red = this.darken(parameters.color.x(), 1);
        this.green = this.darken(parameters.color.y(), 1);
        this.blue = this.darken(parameters.color.z(), 1);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
        } else {
            float f = (float)this.age / (float)this.maxAge;
            f = 1.0F - f;
            float g = 1.0F - f;
            g *= g;
            g *= g;
            this.x = this.startX + this.velocityX * (double)f;
            this.y = this.startY + this.velocityY * (double)f - (double)(g * 1.2F);
            this.z = this.startZ + this.velocityZ * (double)f;
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        this.setBoundingBox(this.getBoundingBox().offset(dx, dy, dz));
        this.repositionFromBoundingBox();
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DebugWaveMovementParticleEffect> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(DebugWaveMovementParticleEffect dustParticleEffect, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            return new DebugWaveMovementParticle(clientWorld, d, e, f, g, h, i, dustParticleEffect, this.spriteProvider);
//            return new DebugWaveMovementParticle(clientWorld, g, h, i, d, e, f, dustParticleEffect, this.spriteProvider);
        }
    }

    public static class DebugWaveMovementParticleEffect extends AbstractDustParticleEffect {
        public static final MapCodec<DebugWaveMovementParticleEffect> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                                Codecs.VECTOR_3F.fieldOf("color").forGetter(effect -> effect.color), SCALE_CODEC.fieldOf("scale").forGetter(AbstractDustParticleEffect::getScale)
                        )
                        .apply(instance, DebugWaveMovementParticleEffect::new)
        );
        public static final PacketCodec<RegistryByteBuf, DebugWaveMovementParticleEffect> PACKET_CODEC = PacketCodec.tuple(
                PacketCodecs.VECTOR3F, effect -> effect.color, PacketCodecs.FLOAT, AbstractDustParticleEffect::getScale, DebugWaveMovementParticleEffect::new
        );
        private final Vector3f color;

        public DebugWaveMovementParticleEffect(Vector3f color, float scale) {
            super(scale);
            this.color = color;
        }

        @Override
        public ParticleType<DebugWaveMovementParticleEffect> getType() {
            return Tidal.DEBUG_WAVEMOVEMENT_PARTICLE;
        }
    }
}

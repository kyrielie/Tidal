package net.superkat.tidal.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

public class WhiteWaveParticle extends AbstractWaveParticle {

    public WhiteWaveParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, WhiteWaveParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ, params, spriteProvider);
    }

    @Override
    public void onBlockHit() {
        this.markDead();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Environment(EnvType.CLIENT)
    public static class Factory extends AbstractWaveParticle.Factory<WhiteWaveParticleEffect> {
        public Factory(SpriteProvider spriteProvider) {
            super(spriteProvider);
        }

        @Override
        public Particle createParticle(WhiteWaveParticleEffect params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ) {
            return new WhiteWaveParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }
}

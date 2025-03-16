package net.superkat.tidal.particles.old;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

public class WaveParticle extends AbstractWaveParticle {

    //TODO - custom sprites for colorable/white: water & land & washing up
    //TODO - merge water wave spawning into lines
    //TODO - implement width to reduce vertices & easier lines
    //TODO - remove color from particle effect, as that is now set by the tick method

    //After that...
    //TODO - Length variable controlled by the particle?
    //TODO - collision detecting for spray effect
    //TODO - dark grey version for wet block

    public WaveParticle(ClientWorld world, double x, double y, double z, double velX, double velY, double velZ, WaveParticleEffect params, SpriteProvider spriteProvider) {
        super(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        this.updateWaterColor();

        this.world.addParticle(new WhiteWaveParticleEffect(params), x, y + 0.05f, z, velX, velY, velZ);

        //incase I want it for later: This creates a SpriteProvider with this particle's sprites as an example
        //AW: ParticleManager#SimpleSpriteProviderer, ParticleManager#loadTextureList
//        SpriteAtlasTexture spriteAtlas = (SpriteAtlasTexture) MinecraftClient.getInstance().getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
//        SpriteProvider test = new ParticleManager.SimpleSpriteProvider();
//
//        MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of(Tidal.MOD_ID, "particles/wave_particle.json")).ifPresent(resource -> {
//            Optional<List<Identifier>> sprites = MinecraftClient.getInstance().particleManager.loadTextureList(Identifier.of(Tidal.MOD_ID, "wave_particle"), resource);
//            if(sprites.isPresent()) {
//                List<Sprite> list = new ArrayList<>();
//
//                for (Identifier spriteId : sprites.get()) {
//                    Sprite sprite = spriteAtlas.getSprite(spriteId);
//                    if(sprite != null) list.add(sprite);
//                }
//
//                ((ParticleManager.SimpleSpriteProvider)test).setSprites(list);
//            }
//        });
    }

    @Override
    public void onBlockHit() {
        this.spray();
    }

    @Override
    public void tick() {
        super.tick();
        if(!returningBack()) updateWaterColor();
    }

    @Environment(EnvType.CLIENT)
    public static class Factory extends AbstractWaveParticle.Factory<WaveParticleEffect> {
        public Factory(SpriteProvider spriteProvider) {
            super(spriteProvider);
        }

        @Override
        public Particle createParticle(WaveParticleEffect params, ClientWorld world, double x, double y, double z, double velX, double velY, double velZ) {
            return new WaveParticle(world, x, y, z, velX, velY, velZ, params, spriteProvider);
        }
    }

//    @Environment(EnvType.CLIENT)
//    public static class Factory implements ParticleFactory<WaveParticleEffect> {
//        private final SpriteProvider spriteProvider;
//
//        public Factory(SpriteProvider spriteProvider) {
//            this.spriteProvider = spriteProvider;
//        }
//
//        public Particle createParticle(WaveParticleEffect particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
//            return new WaveParticle(clientWorld, d, e, f, g, h, i, particleType, this.spriteProvider);
//        }
//    }
}

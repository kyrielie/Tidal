package net.superkat.tidal.sprite;

import net.minecraft.client.texture.Sprite;

public class WaveSpriteProvider {
    public TidalSpriteHandler handler;
    public Sprite movingColorableSprite;
    public Sprite movingWhiteSprite;
    public Sprite washingColorableSprite;
    public Sprite washingWhiteSprite;

    public WaveSpriteProvider(TidalSpriteHandler handler, Sprite movingColorableSprite, Sprite movingWhiteSprite, Sprite washingColorableSprite, Sprite washingWhiteSprite) {
        this.handler = handler;
        this.movingColorableSprite = movingColorableSprite;
        this.movingWhiteSprite = movingWhiteSprite;
        this.washingColorableSprite = washingColorableSprite;
        this.washingWhiteSprite = washingWhiteSprite;
    }

    //TODO - remove wave width thing from waveresourcemetadata
    //TODO - sine-wave y-level incrementals on wave spawning

    public static int getFrameFromAge(Sprite sprite, int age) {
        int frameCount = getFrameCount(sprite);
        int frameTime = getMetadata(sprite).frameTime();
        return (age / frameTime) % frameCount;
    }

    public static float getMinU(Sprite sprite) {
        return sprite.getMinU();
    }

    public static float getMaxU(Sprite sprite) {
        return sprite.getMaxU();
    }

    public static float getMinV(Sprite sprite, int frame) {
        int frameCount = getFrameCount(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / frameCount) * frame;
    }

    public static float getMaxV(Sprite sprite, int frame) {
        int frameCount = getFrameCount(sprite);
        float vRange = sprite.getMaxV() - sprite.getMinV();
        return sprite.getMinV() + (vRange / frameCount) * (frame + 1);
    }

    private static int getFrameCount(Sprite sprite) {
        return sprite.getContents().getHeight() / getMetadata(sprite).frameHeight();
    }

    private static WaveResourceMetadata getMetadata(Sprite sprite) {
        return sprite.getContents().getMetadata().decode(WaveResourceMetadata.SERIALIZER).orElse(WaveResourceMetadata.DEFAULT);
    }
}

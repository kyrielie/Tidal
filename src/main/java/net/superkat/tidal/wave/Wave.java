package net.superkat.tidal.wave;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.superkat.tidal.TidalClient;
import net.superkat.tidal.sprite.TidalSpriteHandler;
import net.superkat.tidal.sprite.WaveSpriteProvider;

import java.awt.*;

/**
 * Contains positional data for a Wave to use. The yaw is used for the initial velocity direction.
 */
public class Wave {
    public ClientWorld world;
    public BlockPos spawnPos;
    public float yaw;

    public Box box;
    public float width = 1f;
    public float length = 1f;
    public float x;
    public float y;
    public float z;
    public float prevX;
    public float prevY;
    public float prevZ;

    public float velX;
    public float velY;
    public float velZ;

    public int age;
    public int maxAge;
    public boolean dead = false;

    public BlockState beneathBlock = Blocks.WATER.getDefaultState();
    public boolean aboveWater = true;
    public boolean washingUp = false;
    public boolean ending = false;

    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public float alpha = 1f;
    public float prevRed;
    public float prevGreen;
    public float prevBlue;

    public Wave(ClientWorld world, BlockPos spawnPos, float yaw) {
        this.world = world;
        this.spawnPos = spawnPos;
        this.yaw = yaw;

        this.x = spawnPos.getX();
        this.y = (float) (spawnPos.getY() + Math.abs((world.getRandom().nextGaussian() / 16)));
//        this.y = spawnPos.getY();
        this.z = spawnPos.getZ();

        float f = 0.2f / 2.0F;
        float g = 0.2f;
        this.box = (new Box(x - (double)f, y, z - (double)f, x + (double)f, y + (double)g, z + (double)f));

        float speed = 0.15f;

        this.velX = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.velY = 0;
        this.velZ = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.maxAge = 100;
//        this.maxAge = 80;
    }

    public Sprite getColorableSprite() {
        TidalSpriteHandler spriteHandler = TidalClient.TIDAL_SPRITE_HANDLER;
        WaveSpriteProvider spriteProvider = spriteHandler.getSpriteProvider();
        return this.isWashingUp() ? spriteProvider.washingColorableSprite : spriteProvider.movingColorableSprite;
    }

    public Sprite getWhiteSprite() {
        TidalSpriteHandler spriteHandler = TidalClient.TIDAL_SPRITE_HANDLER;
        WaveSpriteProvider spriteProvider = spriteHandler.getSpriteProvider();
        return this.isWashingUp() ? spriteProvider.washingWhiteSprite : spriteProvider.movingWhiteSprite;
    }

    public Vec3d getPos() {
        return new Vec3d(this.x, this.y + 1.5, this.z);
    }

    public BlockPos getBlockPos() {
        return BlockPos.ofFloored(this.x, this.y, this.z);
    }

    public void tick() {
        if(this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        if(updateWashingUp()) {
            this.velX *= 0.9f;
            this.velY = -0.0005f;
            this.velZ *= 0.9f;
            this.ending = Math.abs(this.velX) <= 0.03f && Math.abs(this.velZ) <= 0.3f;
            this.length += Math.abs(velX * 8f);
        } else {
            this.updateWaterColor();
        }

        this.move(this.velX, this.velY, this.velZ);
        this.updateBeneathBlock();
    }

    public void move(float velX, float velY, float velZ) {
        this.box = this.box.offset(velX, velY, velZ);
        this.x = (float) (box.minX + box.maxX) / 2f;
        this.y = (float) box.minY;
        this.z = (float) (box.minZ + box.maxZ) / 2f;
    }

    public boolean updateWashingUp() {
        if(!washingUp && !aboveWater) {
            this.washingUp = true;
        }
        return this.washingUp;
    }

    public boolean isWashingUp() {
        return this.washingUp;
    }

    public void updateBeneathBlock() {
        this.beneathBlock = world.getBlockState(this.getBlockPos());
        this.aboveWater = TidalWaveHandler.stateIsWater(beneathBlock);
    }

    public Box getBoundingBox() {
        return this.box.expand(0.5);
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void updateWaterColor() {
        int color = BiomeColors.getWaterColor(this.world, this.getBlockPos());
        float r = (float) (color >> 16 & 0xFF) / 255.0F;
        float g = (float) (color >> 8 & 0xFF) / 255.0F;
        float b = (float) (color & 0xFF) / 255.0F;
        this.setColor(r, g, b);
    }

    public void setColor(Color color) {
        this.setColor(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
    }

    /**
     * @param red Float 0f through 1f
     * @param green Float 0f through 1f
     * @param blue Float 0f through 255f - nah I'm just kidding its 0f through 1f
     */
    public void setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    //TODO - emissive on full moon
    public int getLight() {
        return LightmapTextureManager.pack(15, 15);
    }

    public void markDead() {
        this.dead = true;
    }

    public boolean isDead() {
        return this.dead;
    }

}

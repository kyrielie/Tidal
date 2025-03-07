package net.superkat.tidal.renderer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

/**
 * Contains positional data for a Wave to use. The yaw is used for the initial velocity direction.
 */
public class WaveSegment {
    public BlockPos spawnPos;
    public float yaw;

    public float x;
    public float y;
    public float z;

    public float velX = 0;
    public float velY = 0;
    public float velZ = 0;

    public int age;
    public int maxAge;
    public boolean dead = false;

    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;

    public WaveSegment(BlockPos spawnPos, float yaw) {
        this.spawnPos = spawnPos;
        this.yaw = yaw;

        this.x = spawnPos.getX();
        this.y = spawnPos.getY();
        this.z = spawnPos.getZ();

        float speed = 0.15f;

        this.velX = (float) (Math.cos(Math.toRadians(yaw)) * speed);
        this.velY = 0;
        this.velZ = (float) (Math.sin(Math.toRadians(yaw)) * speed);

        this.maxAge = 100;
//        this.maxAge = 80;
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

        this.move(this.velX, this.velY, this.velZ);
    }

    public void move(float velX, float velY, float velZ) {
        this.x += velX;
        this.y += velY;
        this.z += velZ;
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

    public void markDead() {
        this.dead = true;
    }

    public boolean isDead() {
        return this.dead;
    }

}

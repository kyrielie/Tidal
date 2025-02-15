package net.superkat.tidal.water.trackers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.math.BlockPos;

public class SitePos {
    public BlockPos pos;
//    public BlockPos center;
    public int centerX = 0;
    public int centerZ = 0;
    public float yaw = 0f;
    public boolean yawCalculated = false;

    //cache x's and z's
    public IntArrayList xList = new IntArrayList();
    public IntArrayList zList = new IntArrayList();

    public SitePos(BlockPos pos) {
        this.pos = pos;
    }

    public void addPos(BlockPos pos) {
        this.xList.add(pos.getX());
        this.zList.add(pos.getZ());
    }

    public void removePos(BlockPos pos) {
        int xIndex = this.xList.indexOf(pos.getX());
        this.xList.removeInt(xIndex);

        int zIndex = this.zList.indexOf(pos.getZ());
        this.zList.removeInt(zIndex);
    }

    public void updateCenter() {
        int xSize = this.xList.size();
        this.centerX = this.xList.intStream().sum() / xSize;

        int zSize = this.zList.size();
        this.centerZ = this.zList.intStream().sum() / zSize;

        updateYaw();
    }

    public void updateYaw() {
        this.yawCalculated = true;
        this.yaw = (float) Math.toDegrees(Math.atan2(pos.getZ() - centerZ, pos.getX() - centerX));
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getX() {
        return this.pos.getX();
    }

    public int getY() {
        return this.pos.getY();
    }

    public int getZ() {
        return this.pos.getZ();
    }
}

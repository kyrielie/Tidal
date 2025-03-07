package net.superkat.tidal.renderer;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.superkat.tidal.DebugHelper;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import org.apache.commons.compress.utils.Lists;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * A Wave holds a list of WaveSegments, which all move independently of one another.<br><br>
 * This Wave will then determine when to break off other WaveSegments into their own Wave, or delete them completely.<br><br>
 * All Waves are rendered individually, with their WaveSegments being rendered in a line connected together.
 */
public class Wave {
    public final TidalWaveHandler handler;
    public final World world;

    //A sorted list of WaveSegments, sorted in the render order.
    public ObjectArrayList<WaveSegment> waveSegments = new ObjectArrayList<>();

    public Color color;

    public boolean merged = false;

    //BlockPos' of the first and last WaveSegments' positions, used for checking to merge Waves together
    public BlockPos startPos;
    public BlockPos endPos;

    public Wave(TidalWaveHandler handler, World world) {
        this.handler = handler;
        this.world = world;
        this.color = DebugHelper.randomDebugColor();
    }

    public Wave(TidalWaveHandler handler, World world, BlockPos segmentPos, float segmentYaw) {
        this(handler, world);
        this.add(new WaveSegment(segmentPos, segmentYaw));
    }

    public void merge(Wave wave) {
//        this.waveSegments.addAll(wave.getWaveSegments());
//        this.waveSegments.forEach(segment -> segment.setColor(this.color));
//        this.sortSegments();
//        wave.markMerged();
    }

    public void markMerged() {
        this.waveSegments.clear();
        this.merged = true;
    }

    public void add(WaveSegment segment) {
        segment.setColor(this.color);
        this.waveSegments.add(segment);
    }

    public void tick() {
//        sortSegments();

        if (this.world.getTime() % 20 == 0) {
            int j = 0;
            for (WaveSegment waveSegment : this.getWaveSegments()) {
                Color color = DebugHelper.debugTransitionColor(j, waveSegments.size());
                Vec3d block = waveSegment.getBlockPos().toCenterPos();
                j++;
                ParticleEffect particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, true, block.getX(), block.getY() + 5, block.getZ(), 0, 0, 0);
            }
        }

        List<BlockPos> occupiedBlocks = Lists.newArrayList();
        List<Integer> splitPoints = Lists.newArrayList();

        ObjectArrayList<WaveSegment> segments = this.getWaveSegments();
        for (int i = 0; i < segments.size() - 1; i++) {
            WaveSegment waveSegment = segments.get(i);
            waveSegment.tick();

            BlockPos blockPos = waveSegment.getBlockPos();
            if (occupiedBlocks.contains(blockPos)) waveSegment.markDead();
            else occupiedBlocks.add(blockPos);

            WaveSegment next = segments.get(i + 1);
            double dist = waveSegment.getBlockPos().getSquaredDistance(next.getBlockPos());
            if(dist >= 9) {
                splitPoints.add(i + 1);
            }
        }

        List<WaveSegment> removed = Lists.newArrayList();
        for (Integer splitPoint : splitPoints) {
            ObjectArrayList<WaveSegment> splitOff = new ObjectArrayList<>(this.waveSegments.subList(splitPoint, this.waveSegments.size()));
            removed.addAll(splitOff);

            Wave wave = new Wave(this.handler, this.world);
            wave.waveSegments = splitOff;
//            this.handler.waves.add(wave);
        }

        this.waveSegments.removeIf(WaveSegment::isDead);
        this.waveSegments.removeAll(removed);

    }

    public boolean isDead() {
        return this.waveSegments.isEmpty() || this.merged;
    }

    public void sortSegments() {
        this.waveSegments.sort(Comparator.comparing(WaveSegment::getBlockPos));
    }

    public ObjectArrayList<WaveSegment> getWaveSegments() {
        return waveSegments;
    }
}

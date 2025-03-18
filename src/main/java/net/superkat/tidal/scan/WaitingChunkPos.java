package net.superkat.tidal.scan;

import com.google.common.primitives.Doubles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.superkat.tidal.config.TidalConfig;
import org.jetbrains.annotations.NotNull;

public class WaitingChunkPos implements Comparable<WaitingChunkPos> {
    public final ChunkPos pos;
    public double distance;

    public WaitingChunkPos(ChunkPos pos) {
        this.pos = pos;
    }

    public void calcDist() {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        ChunkPos cameraPos = new ChunkPos(camera.getBlockPos());
        this.distance = cameraPos.getSquaredDistance(this.pos);
    }

    public boolean shouldScan() {
        return this.distance <= MathHelper.square(TidalConfig.chunkRadius);
    }

    public ChunkPos getPos() {
        return pos;
    }

    @Override
    public int compareTo(@NotNull WaitingChunkPos other) {
        return Doubles.compare(this.distance, other.distance);
    }
}

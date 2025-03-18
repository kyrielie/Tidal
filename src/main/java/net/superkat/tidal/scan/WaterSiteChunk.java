package net.superkat.tidal.scan;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.BlockPos;

public class WaterSiteChunk {
    public final long chunkPos;
    public Object2ObjectOpenHashMap<BlockPos, SitePos> waterSiteMap = new Object2ObjectOpenHashMap<>();
    public Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>> distWaterMap = new Int2ObjectOpenHashMap<>();

    public WaterSiteChunk(long chunkPos, Object2ObjectOpenHashMap<BlockPos, SitePos> waterSiteMap, Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>> distWaterMap) {
        this.chunkPos = chunkPos;
        this.waterSiteMap = waterSiteMap;
        this.distWaterMap = distWaterMap;
    }

    public WaterSiteChunk(long chunkPos) {
        this.chunkPos = chunkPos;
    }

}

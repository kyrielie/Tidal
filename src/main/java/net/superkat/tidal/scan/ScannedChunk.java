package net.superkat.tidal.scan;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class ScannedChunk {
    public final long chunkPos;
    public ObjectOpenHashSet<BlockPos> waters;
    public ObjectOpenHashSet<BlockPos> shorelines;
    public ObjectOpenHashSet<SitePos> sites;

    public ScannedChunk(ChunkPos chunkPos, ObjectOpenHashSet<BlockPos> waters, ObjectOpenHashSet<BlockPos> shorelines, ObjectOpenHashSet<SitePos> sites) {
        this.chunkPos = chunkPos.toLong();
        this.waters = waters;
        this.shorelines = shorelines;
        this.sites = sites;
    }

    public void setWaters(ObjectOpenHashSet<BlockPos> waters) {
        this.waters = waters;
    }

    public void setShorelines(ObjectOpenHashSet<BlockPos> shorelines) {
        this.shorelines = shorelines;
    }

    public void setSites(ObjectOpenHashSet<SitePos> sites) {
        this.sites = sites;
    }
}

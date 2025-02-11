package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.superkat.tidal.TidalWaveHandler;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class which contains a map of ChunkPos'(as longs) & Set of BlockPos'. Includes general methods beyond that.
 * <br><br>
 * Note: there is no real reason for this to not be abstract and replace WaterBody and Shoreline, other than naming and keeping things organized in a way I can understand easier
 */
public abstract class AbstractBlockSetTracker {
//    public Long2ReferenceArrayMap<ObjectOpenHashSet<BlockPos>> chunkedBlocks = new Long2ReferenceArrayMap<>();
    public Long2ReferenceArrayMap<TrackerChunkInfo> chunkedBlocks = new Long2ReferenceArrayMap<>();

    public AbstractBlockSetTracker withBlocks(Collection<BlockPos> blocks) {
        this.addBlocks(blocks);
        return this;
    }

    /**
     * @return The center of all the BlockPos positions. May not be included in the blocks set!
     */
    public BlockPos centerPos() {
        Set<BlockPos> blocks = this.getBlocks();
        int size = blocks.size();
        if(size == 0) return BlockPos.ORIGIN;
        //I'm sorta proud of coming up with this map stream thingy all by myself :)
        int centerX = blocks.stream().mapToInt(Vec3i::getX).sum() / size;
        int randomY = blocks.stream().toList().getFirst().getY(); //random y because likely all the same y level
        int centerZ = blocks.stream().mapToInt(Vec3i::getZ).sum() / size;
        return new BlockPos(centerX, randomY, centerZ);
    }

    public BlockPos randomPos() {
        //there's probably better way of doing this but okay
        int size = this.getBlockAmount();
        return this.getBlocks().stream().toList().get(TidalWaveHandler.getRandom().nextInt(size));
    }

    public Set<BlockPos> getBlocks() {
//        return this.chunkedBlocks.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        return this.chunkedBlocks.values().stream().flatMap(trackerChunkInfo -> trackerChunkInfo.blocks.stream()).collect(Collectors.toSet());
    }

    public void addYaw(long chunkPosL, float averagedYaw) {
        this.chunkedBlocks.get(chunkPosL).addYaw(averagedYaw);
    }

    public void merge(AbstractBlockSetTracker blockTracker) {
        this.merge(blockTracker, false);
    }

    public void merge(AbstractBlockSetTracker blockTracker, boolean mergeYaw) {
        this.addBlocks(blockTracker.getBlocks());
        if(!mergeYaw) return;
        for (Long2ReferenceMap.Entry<TrackerChunkInfo> chunkedEntry : blockTracker.chunkedBlocks.long2ReferenceEntrySet()) {
            TrackerChunkInfo info = chunkedEntry.getValue();
            if(!info.shouldMergeYaw) continue;
            long chunkPosL = chunkedEntry.getLongKey();
            float chunkYaw = chunkedEntry.getValue().totalYaw;
            int chunkBlocksWithYaw = chunkedEntry.getValue().blocksWithYaw;
            this.chunkedBlocks.get(chunkPosL).mergeYaw(chunkYaw, chunkBlocksWithYaw);
        }
    }

    public void addBlocks(Collection<BlockPos> blocks) {
        blocks.forEach(this::addBlock);
    }

    public void addBlock(BlockPos pos) {
        this.getBlockSet(pos).add(pos);
    }

    public void removeBlock(BlockPos pos) {
        this.getBlockSet(pos).remove(pos);
    }

    public int getBlockAmount() {
//        return this.chunkedBlocks.values().stream().mapToInt(ObjectOpenHashSet::size).sum();
        return this.chunkedBlocks.values().stream().mapToInt(value -> value.blocks.size()).sum();
    }

    public ObjectOpenHashSet<BlockPos> getBlockSet(BlockPos pos) {
//        return this.chunkedBlocks.computeIfAbsent(new ChunkPos(pos).toLong(), aLong -> new ObjectOpenHashSet<>());
        return this.chunkedBlocks.computeIfAbsent(new ChunkPos(pos).toLong(), aLong -> new TrackerChunkInfo()).getBlocks();
    }

    public void removeChunkBlocks(long chunkPosL) {
        this.chunkedBlocks.remove(chunkPosL);
    }

    public static class TrackerChunkInfo {
        public ObjectOpenHashSet<BlockPos> blocks = new ObjectOpenHashSet<>();
        public boolean shouldMergeYaw = false;
        public int blocksWithYaw = 0;
        public float totalYaw = 0;
        public float yaw = 0;

        public ObjectOpenHashSet<BlockPos> getBlocks() {
            return this.blocks;
        }

        public float getYaw() {
            return this.totalYaw / this.blocksWithYaw;
        }

        public void mergeYaw(float totalYaw, int blocksWithYaw) {
            this.totalYaw += totalYaw;
            this.blocksWithYaw += blocksWithYaw;
            shouldMergeYaw = true;
        }

        public void addYaw(List<Float> shorelineYaws) {
            int size = shorelineYaws.size();
            float yaw = (float) shorelineYaws.stream().mapToDouble(Float::floatValue).sum();
            totalYaw += yaw;
            blocksWithYaw += size;
            shouldMergeYaw = true;
        }

        public void addYaw(float yaw) {
            this.yaw = (this.yaw + yaw) / 2;
            shouldMergeYaw = true;
        }
    }
}

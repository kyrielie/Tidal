package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.Chunk;
import net.superkat.tidal.TidalWaveHandler;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class which contains a Set of BlockPos', and ticking functionality. Includes general methods beyond that.
 * <br><br>
 * Note: there is no real reason for this to not be abstract and replace WaterBody and Shoreline, other than naming and keeping things organized in a way I can understand easier
 */
public abstract class AbstractBlockSetTracker {
    public Long2ReferenceArrayMap<ObjectOpenHashSet<BlockPos>> chunkedBlocks = new Long2ReferenceArrayMap<>();
    public int tick = 0;

    public AbstractBlockSetTracker withBlocks(Collection<BlockPos> blocks) {
        this.addBlocks(blocks);
        return this;
    }

    public void tick() {
        tick++;
    }

    /**
     * @return The center of all the BlockPos positions. May not be included in the blocks set!
     */
    public BlockPos center() {
        Set<BlockPos> blocks = this.getBlocks();
        int size = blocks.size();
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
        return this.chunkedBlocks.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
    }

    public boolean shouldRemove() {
        return false;
//        return this.tick >= 6000; //5 minutes
//        return this.tick >= 1000;
    }

    public void merge(AbstractBlockSetTracker blockTracker) {
        this.addBlocks(blockTracker.getBlocks());
    }

    public void addBlocks(Collection<BlockPos> blocks) {
        //set of set of blocks per chunk, then add chunk?
        blocks.forEach(this::addBlock);
    }

    public void addBlock(BlockPos pos) {
        this.getBlockSet(pos).add(pos);
    }

    public void removeBlock(BlockPos pos) {
//        if(blocks.remove(pos)) {
        if(this.getBlockSet(pos).remove(pos)) {
            //speed up the time it'll take to delete itself as its more likely a mistake can happen
            this.tick += 60;
        }
    }

    public int getBlockAmount() {
        return this.chunkedBlocks.values().stream().mapToInt(ObjectOpenHashSet::size).sum();
    }

    public ObjectOpenHashSet<BlockPos> getBlockSet(BlockPos pos) {
        return this.chunkedBlocks.computeIfAbsent(new ChunkPos(pos).toLong(), aLong -> new ObjectOpenHashSet<>());
    }


    /**
     * Iterate through a chunk's blocks, checking only the x and z coords and ignoring the y coord
     *
     * @param chunk The chunk whose blocks should be removed from this tracker.
     */
    public void removeChunkBlocks(Chunk chunk) {
        long chunkPosL = chunk.getPos().toLong();
        this.chunkedBlocks.remove(chunkPosL);
    }
}

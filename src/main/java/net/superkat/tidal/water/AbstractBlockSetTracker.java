package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.superkat.tidal.TidalWaveHandler;

import java.util.Collection;
import java.util.Set;

/**
 * Class which contains a Set of BlockPos', and ticking functionality. Includes general methods beyond that.
 */
public abstract class AbstractBlockSetTracker {
    public Set<BlockPos> blocks = Sets.newHashSet();
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
        int size = blocks.size();
        //I'm sorta proud of coming up with this map stream thingy all by myself :)
        int centerX = blocks.stream().mapToInt(Vec3i::getX).sum() / size;
        int randomY = blocks.stream().toList().getFirst().getY(); //random y because likely all the same y level
        int centerZ = blocks.stream().mapToInt(Vec3i::getZ).sum() / size;
        return new BlockPos(centerX, randomY, centerZ);
    }

    public BlockPos randomPos() {
        //there's probably better way of doing this but okay
        int size = blocks.size();
        return blocks.stream().toList().get(TidalWaveHandler.getRandom().nextInt(size));
    }

    public boolean shouldRemove() {
        return this.tick >= 6000; //5 minutes
//        return this.tick >= 1000;
    }

    public void merge(AbstractBlockSetTracker blockTracker) {
        this.blocks.addAll(blockTracker.blocks);
    }

    public void addBlocks(Collection<BlockPos> blocks) {
        this.blocks.addAll(blocks);
    }

    public void removeBlock(BlockPos pos) {
        if(this.blocks.remove(pos)) {
            //speed up the time it'll take to delete itself as its more likely a mistake can happen
            this.tick += 60;
        }
    }

    public boolean posIsWater(ClientWorld world, BlockPos pos) {
        return world.getFluidState(pos).isIn(FluidTags.WATER);
    }
}

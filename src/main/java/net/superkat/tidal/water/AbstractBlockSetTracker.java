package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Class which contains a Set of BlockPos', and ticking functionality. Includes general methods beyond that.
 */
public abstract class AbstractBlockSetTracker {
    public Set<BlockPos> blocks = Sets.newHashSet();
    public int tick = 0;

    public void tick() {
        tick++;
    }

    public boolean shouldRemove() {
        return this.tick >= 6000; //5 minutes
//        return this.tick >= 1000;
    }

    public void merge(AbstractBlockSetTracker blockTracker) {
        this.blocks.addAll(blockTracker.blocks);
    }

    public void addBlocks(Set<BlockPos> blocks) {
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

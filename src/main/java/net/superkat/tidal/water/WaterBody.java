package net.superkat.tidal.water;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;

/**
 * Keep track of water blocks, and understand roughly how big areas of water are.
 * <br><br>
 * Used to dynamically adjust tide waves.
 */
public class WaterBody extends AbstractBlockSetTracker {

    @Override
    public WaterBody withBlocks(Collection<BlockPos> blocks) {
        this.addBlocks(blocks);
        return this;
    }

}

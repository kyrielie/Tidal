package net.superkat.tidal.water;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;

/**
 * Keep track of blocks right next to water.
 * <br><br>
 * Used to determine where water tides should head towards.
 */
public class Shoreline extends AbstractBlockSetTracker {

    @Override
    public Shoreline withBlocks(Collection<BlockPos> blocks) {
        this.addBlocks(blocks);
        return this;
    }
}

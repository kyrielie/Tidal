package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Keep track of blocks right next to water.
 * <br><br>
 * Used to determine where water tides should head towards.
 */
public class Shoreline {

    public Set<BlockPos> blocks = Sets.newHashSet();

}

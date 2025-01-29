package net.superkat.tidal.water;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keep track of water blocks, and understand roughly how big areas of water are.
 * <br><br>
 * Used to dynamically adjust tide waves.
 */
public class WaterBody extends AbstractBlockSetTracker {

    public void addNeighbours(ClientWorld world, Set<BlockPos> neighbours) {
        Set<BlockPos> topNeighbours = neighbours.stream().map(pos -> topOfWater(world, pos)).collect(Collectors.toSet());
        addBlocks(topNeighbours);
    }

    public BlockPos topOfWater(ClientWorld world, BlockPos pos) {
        BlockPos.Mutable mutable = pos.mutableCopy().move(Direction.UP);

        while(posIsWater(world, mutable)) {
            mutable.move(Direction.UP);
        }
        return mutable.move(Direction.DOWN);
    }

}

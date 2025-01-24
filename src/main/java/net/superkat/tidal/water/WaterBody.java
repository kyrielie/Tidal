package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;

/**
 * Keep track of water blocks, and understand roughly how big areas of water are.
 * <br><br>
 * Used to dynamically adjust tide waves.
 */
public class WaterBody {

    public Set<BlockPos> waterBlocks = Sets.newHashSet();

    public final BlockPos initPos;
    public BlockPos mainPos;

    public WaterBody(BlockPos initPos) {
        this.initPos = initPos;
    }

    public void mergeWaterBody(WaterBody waterBody) {
        this.waterBlocks.addAll(waterBody.waterBlocks);
    }

    public void addNeighbours(ClientWorld world, int distance) {
        this.mainPos = topOfWater(world, this.initPos);
        for (BlockPos blockPos : BlockPos.iterate(this.mainPos.add(-distance, 0, -distance), this.mainPos.add(distance, 1, distance))) {
            if (posIsWater(world, blockPos)) {
                this.waterBlocks.add(topOfWater(world, blockPos));
            }
        }
    }

    public boolean posIsWater(ClientWorld world, BlockPos pos) {
        return world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    public BlockPos topOfWater(ClientWorld world, BlockPos pos) {
        BlockPos.Mutable mutable = pos.mutableCopy().move(Direction.UP);

        while(posIsWater(world, mutable)) {
            mutable.move(Direction.UP);
        }
        return mutable.move(Direction.DOWN);
    }

}

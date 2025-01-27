package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keep track of water blocks, and understand roughly how big areas of water are.
 * <br><br>
 * Used to dynamically adjust tide waves.
 */
public class WaterBody {

    public Set<BlockPos> waterBlocks = Sets.newHashSet();
    public int tick = 0;

    public final BlockPos initPos;
    public BlockPos mainPos;

    public WaterBody(BlockPos initPos) {
        this.initPos = initPos;
    }

    public void tick() {
        tick++;
    }

    public boolean shouldRemove() {
        return this.tick >= 6000; //5 minutes
//        return this.tick >= 1000;
    }

    public void mergeWaterBody(WaterBody waterBody) {
        this.waterBlocks.addAll(waterBody.waterBlocks);
    }

    public void addBlocks(Set<BlockPos> blocks) {
        this.waterBlocks.addAll(blocks);
    }

    public void addNeighbours(ClientWorld world, Set<BlockPos> neighbours) {
        this.mainPos = topOfWater(world, this.initPos);
        Set<BlockPos> topNeighbours = neighbours.stream().map(pos -> topOfWater(world, pos)).collect(Collectors.toSet());
        addBlocks(topNeighbours);
    }

    public void removeBlock(BlockPos pos) {
        if(this.waterBlocks.remove(pos)) {
            this.tick += 60;
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

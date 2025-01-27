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

    public final BlockPos initPos;
    public BlockPos mainPos;

    public WaterBody(BlockPos initPos) {
        this.initPos = initPos;
    }

    public void mergeWaterBody(WaterBody waterBody) {
        this.waterBlocks.addAll(waterBody.waterBlocks);
    }

//    public List<WaterBody> split(int amount) {
//        List<BlockPos> blocks = waterBlocks.stream().toList();
//
//        BlockPos maxX = Collections.max(blocks, Comparator.comparingInt(Vec3i::getX));
//        BlockPos minX = Collections.min(blocks, Comparator.comparingInt(Vec3i::getX));
//        int distanceX = maxX.getX() - minX.getX();
//        BlockPos maxZ = Collections.max(blocks, Comparator.comparingInt(Vec3i::getZ));
//        BlockPos minZ = Collections.min(blocks, Comparator.comparingInt(Vec3i::getZ));
//        int distanceZ = maxZ.getZ() - minZ.getZ();
//
//        List<BlockPos> minXHalf = blocks.stream().filter(pos -> pos.getX() < minX.getX() + distanceX / 2).toList();
//        List<BlockPos> maxXHalf = blocks.stream().filter(pos -> pos.getX() >= minX.getX() + distanceX / 2).toList();
//
//        List<BlockPos> minZQuarter = minXHalf.stream().filter(pos -> pos.getZ() < minZ.getZ() + distanceZ / 2).toList();
//        List<BlockPos> maxZQuarter = maxXHalf.stream().filter(pos -> pos.getZ() >= minZ.getZ() + distanceZ / 2).toList();
//        List<BlockPos> minXQuarter = minXHalf.stream().filter(pos -> !minZQuarter.contains(pos)).toList();
//        List<BlockPos> maxXQuarter = maxXHalf.stream().filter(pos -> !maxZQuarter.contains(pos)).toList();
//
//        List<List<BlockPos>> split = new ArrayList<>();
//        split.add(minXQuarter);
//        split.add(maxXQuarter);
//        split.add(minZQuarter);
//        split.add(maxZQuarter);
//        List<WaterBody> results = new ArrayList<>();
//
//        for (List<BlockPos> blockList : split) {
//            WaterBody splitResult = new WaterBody(this.initPos);
//            splitResult.addBlocks(new HashSet<>(blockList));
//            results.add(splitResult);
//        }
//
//        if(blocks.isEmpty() || blocks.size() / amount <= 0) return Collections.emptyList();
//        int splitAmount = blocks.size() / amount;
//        List<List<BlockPos>> split = Lists.partition(blocks, splitAmount);
//
//        List<WaterBody> results = new ArrayList<>();
//
//        for (List<BlockPos> blockList : split) {
//            WaterBody splitResult = new WaterBody(this.initPos);
//            splitResult.addBlocks(new HashSet<>(blockList));
//            results.add(splitResult);
//        }

//        return results;
//    }

    public void addBlocks(Set<BlockPos> blocks) {
        this.waterBlocks.addAll(blocks);
    }

    public void addNeighbours(ClientWorld world, Set<BlockPos> neighbours) {
        this.mainPos = topOfWater(world, this.initPos);
        Set<BlockPos> topNeighbours = neighbours.stream().map(pos -> topOfWater(world, pos)).collect(Collectors.toSet());
        addBlocks(topNeighbours);
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

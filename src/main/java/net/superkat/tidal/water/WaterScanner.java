package net.superkat.tidal.water;

import com.google.common.collect.Maps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.apache.commons.compress.utils.Lists;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Slowly find all connected blocks based on DFS "Depth-First Search"
 * <br><br>Updates after every call of the {@link WaterScanner#tick()} method.
 */
public class WaterScanner {
    //blocks which have been checked to be water or not water
    public Map<BlockPos, Boolean> cachedBlocks = Maps.newHashMap();

    //blocks which have had their neighbours checked(scanned) as water or not water, and added to water body/shoreline
    public Map<BlockPos, Boolean> visitedBlocks = Maps.newHashMap();

    public final WaterBodyHandler waterBodyHandler;
    public final ClientWorld world;
    public BlockPos initPos;
    public boolean finished = false;

    public WaterScanner(WaterBodyHandler waterBodyhandler, ClientWorld world, BlockPos initPos) {
        this.waterBodyHandler = waterBodyhandler;
        this.world = world;
        this.initPos = initPos;

        cacheBlock(this.initPos);
        scanPos(this.initPos);
    }

    /**
     * Scans the next block which should be scanned - meant to be called every tick, but not required
     */
    public void tick() {
        Iterator<BlockPos> iterator = stack();

        if(iterator.hasNext()) {
            BlockPos next = iterator.next();
            scanPos(next);
        } else {
            //debug
            MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
            this.finished = true;
        }
    }

    public boolean isFinished() {
        return this.finished;
    }

    /**
     * Calculate and return the WaterBody and its blocks
     *
     * @return The up to date WaterBody
     */
    public WaterBody getWaterBody() {
        WaterBody waterBody = new WaterBody();
        waterBody.addBlocks(
                cachedBlocks.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet())
        );
        return waterBody;
    }

    /**
     * @return An iterator which represents the next blocks in line to be scanned
     */
    public Iterator<BlockPos> stack() {
        return cachedBlocks.entrySet().stream()
                .filter(entry -> entry.getValue()
                        && !visitedBlocks.containsKey(entry.getKey())
                        && entry.getKey().isWithinDistance(this.initPos, 32))
                .map(Map.Entry::getKey)
                .iterator();
    }

    /**
     * Scans a block's immediately surrounding neighbours in a "+" plus shape.
     * <br>If the block has already been scanned, nothing will happen.
     *
     * @param pos Block pos to scan
     */
    public void scanPos(BlockPos pos) {
        if(visitedBlocks.containsKey(pos)) return;

        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            //super cursed idea - Map#computeIfAbsent returns the second type(value), which in this case is a boolean
            //this boolean stands for if the blockpos is water or not
            //if any block pos was not water, the corners should be calculated for shoreline(s) blocks
            //the return value for cacheBlock is if the block is water or not
            BlockPos checkedPos = pos.offset(direction);
            boolean blockIsWater = cacheBlock(checkedPos);
            if(!blockIsWater) {
                nonWaterBlocks.add(checkedPos);
            }
        }

        //initial checked blocks create a "+" shape, not including the center
        //corners should be calculated if any non-water blocks were found,
        //creating a square shape(not including center)
        if(!nonWaterBlocks.isEmpty()) {
            //find & cache corner blocks
            List<BlockPos> corners = List.of(pos.add(1, 0, 1), pos.add(-1, 0, 1), pos.add(-1, 0, -1), pos.add(1, 0, -1));
            for (BlockPos corner : corners) {
                if(!posIsWater(world, corner)) {
                    nonWaterBlocks.add(corner);
                    cachedBlocks.putIfAbsent(corner, false);
                }
            }

            List<BlockPos> visitedNonWater = Lists.newArrayList();

            for (BlockPos nonWater : nonWaterBlocks) {
                //don't check any of this if the block has already been checked/added for/to a shoreline
                if(!visitedNonWater.contains(nonWater)) {
                    List<BlockPos> shorelineBlocks = Lists.newArrayList();
                    shorelineBlocks.add(nonWater);

                    //add immediate neighbor blocks
                    for (Direction direction : Direction.Type.HORIZONTAL) {
                        BlockPos checkPos = nonWater.offset(direction);
                        if(nonWaterBlocks.contains(checkPos) || (cachedBlocks.containsKey(checkPos) && !cachedBlocks.get(checkPos))) {
                            shorelineBlocks.add(checkPos);
                        }
                    }

                    //mark all blocks being added to the new shoreline as visited
                    visitedNonWater.addAll(shorelineBlocks);

                    Shoreline shoreline = new Shoreline();
                    shoreline.addBlocks(new HashSet<>(shorelineBlocks));
                    //try to merge shoreline into another, otherwise add it
                    if(!waterBodyHandler.tryMergeShoreline(shoreline)) waterBodyHandler.shorelines.add(shoreline);
                }
            }
        }

        visitedBlocks.computeIfAbsent(pos, pos1 -> cachedBlocks.get(pos1));
    }

    /**
     * Cache a block and return if it is water, all in the same method!
     *
     * @param pos BlockPos to cache and check if its water
     * @return Returns if the BlockPos is water or not - NOT if the block was cached successfully(!!!), as you'd normally expect from a method like this
     */
    public boolean cacheBlock(BlockPos pos) {
        return cachedBlocks.computeIfAbsent(pos, pos1 -> posIsWater(world, pos1));
    }

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        return state.isIn(FluidTags.WATER);
    }

}

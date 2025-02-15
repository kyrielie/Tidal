package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.water.trackers.Shoreline;
import net.superkat.tidal.water.trackers.WaterBody;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a chunk to create a list of created water bodies and shorelines
 * @see WaterBodyHandler
 * @see WaterBody
 * @see Shoreline
 */
public class ChunkScanner {
    //blocks which have been checked to be water or not water
    public Map<BlockPos, Boolean> cachedBlocks = new Object2ObjectOpenHashMap<>();

    //blocks which have had their neighbours checked(scanned) as water or not water, and added to water body/shoreline
    public Set<BlockPos> visitedBlocks = new ObjectOpenHashSet<>();

    public Iterator<BlockPos> cachedIterator = null;

    //amount of shoreline blocks since the last created SitePos
    public int shorelinesSinceSite = 0;

    public final WaterBodyHandler handler;
    public final ClientWorld world;
    public ChunkPos chunkPos;
    public boolean finished = false;

    //TODO - scan above and below for water to jumps in the water
    public ChunkScanner(WaterBodyHandler handler, ClientWorld world, ChunkPos chunkPos) {
        this.handler = handler;
        this.world = world;
        this.chunkPos = chunkPos;
        BlockPos startPos = chunkPos.getStartPos();
        BlockPos endPos = startPos.add(15, 0, 15);
        this.cachedIterator = stack(startPos, endPos);
    }

    /**
     * Scans the next block which should be scanned
     */
    public void tick() {
        if(cachedIterator.hasNext()) {
            BlockPos next = cachedIterator.next();
            int y = this.world.getTopY(Heightmap.Type.WORLD_SURFACE, next.getX(), next.getZ()); //sample heightmap
            scanPos(next.withY(y - 1));
        } else {
            markFinished();
        }
    }

    /**
     * @return An iterator which represents the next blocks in line to be scanned
     */
    public Iterator<BlockPos> stack(BlockPos startPos, BlockPos endPos) {
        cachedIterator = BlockPos.iterate(startPos, endPos).iterator();
        return cachedIterator;
    }

    /**
     * Scan a block to be water or not water.
     * <br><br>If not already visited, the immediately surrounding neighbors are also checked for water and cached in a "+" shape. The corners for a square are NOT scanned.
     *
     * @param pos Block pos to scan
     */
    public void scanPos(BlockPos pos) {
        //if already visited or is air -> return
        if(visitedBlocks.contains(pos)) return;
        if(world.isAir(pos)) return;

        //mark visited
        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        //shorelines need to be checked for still
        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); //they keep getting more cursed

        //check and cache neighbours
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(direction);
            if(world.isAir(checkPos)) continue;
            boolean neighborIsWater = cacheAndIsWater(checkPos);
            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
            //if init scan pos is water OR if the check pos is the top of water
            if(neighborIsWater) waterBlocks.add(checkPos);
            else nonWaterBlocks.add(checkPos);
        }

        if(waterBlocks.isEmpty() || !posIsWater) return;

        //shoreline creation - neighbouring water blocks scan shoreline blocks and add them
        if(!nonWaterBlocks.isEmpty()) {
            //no water bodies should be created from non-water scanned pos
            //returning here to reduce the amount this check is called every so slightly
//            if(!posIsWater) return;

            for (BlockPos nonWater : nonWaterBlocks) {
                this.handler.addShorelineBlock(nonWater);
            }

            this.shorelinesSinceSite += nonWaterBlocks.size();
            if(this.shorelinesSinceSite >= 8) {
                this.handler.createSitePos(pos);
                this.shorelinesSinceSite = 0;
            }
        }

        this.handler.queueWaterBlocks(waterBlocks);

//        WaterBody waterBody = new WaterBody().withBlocks(waterBlocks);
//        if(waterBody.getBlocks().isEmpty() || handler.tryMergeWaterBody(waterBody)) return;
//        this.handler.waterBodies.add(waterBody);
    }
//    public void scanPos(BlockPos pos) {
//        //if already visited or is air -> return
//        if(visitedBlocks.contains(pos)) return;
//        if(world.isAir(pos)) return;
//
//        //mark visited
//        boolean posIsWater = cacheAndIsWater(pos);
//        visitedBlocks.add(pos);
//
//        //block is either the top of water, or a different non-air block
//        //shorelines need to be checked for still
//        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
//        List<BlockPos> waterBlocks = Lists.newArrayList();
//        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); //they keep getting more cursed
//        List<Float> shorelineYaw = Lists.newArrayList();
//
//        for (Direction direction : Direction.Type.HORIZONTAL) {
//            BlockPos checkPos = pos.offset(direction);
//            if(world.isAir(checkPos)) continue;
//            boolean neighborIsWater = cacheAndIsWater(checkPos);
//            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
//            //if init scan pos is water OR if the check pos is the top of water
//            if(neighborIsWater) waterBlocks.add(checkPos);
//            else {
//                nonWaterBlocks.add(checkPos);
//                if(posIsWater) shorelineYaw.add(direction.asRotation());
//            }
//        }
//
//        if(waterBlocks.isEmpty()) return;
//
//        if(!posIsWater) {
//            //get corners - corners should be checked for shorelines to increase chance of being merged
//            BlockPos[] corners = new BlockPos[]{pos.add(1, 0, 1), pos.add(-1, 0, 1), pos.add(-1, 0, -1), pos.add(1, 0, -1)};
//            for (BlockPos cornerPos : corners) {
//                if(world.isAir(cornerPos)) continue;
//                boolean cornerIsWater = cacheAndIsWater(cornerPos);
//                if(cornerIsWater) waterBlocks.add(cornerPos);
//                else nonWaterBlocks.add(cornerPos);
//            }
//        }
//
//        if(!nonWaterBlocks.isEmpty()) {
//            //water has to be next to the shoreline
//            if(waterBlocks.isEmpty()) return;
//            for (BlockPos nonWater : nonWaterBlocks) {
//                long chunkPosL = new ChunkPos(nonWater).toLong();
//                this.handler.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(nonWater);
////                this.handler.shoreBlocks.putIfAbsent(chunkPosL, nonWater);
//            }
////            Shoreline shoreline = new Shoreline().withBlocks(nonWaterBlocks);
////            if (!shoreline.getBlocks().isEmpty() && !handler.tryMergeShoreline(shoreline)) this.handler.shorelines.add(shoreline);
//
//            //no water bodies should be created from non-water scanned pos
//            //returning here to reduce the amount this check is called every so slightly
//            if(!posIsWater) return;
//        }
//
//        WaterBody waterBody = new WaterBody().withBlocks(waterBlocks);
//        if(waterBody.getBlocks().isEmpty() || handler.tryMergeWaterBody(waterBody)) return;
//        this.handler.waterBodies.add(waterBody);
//    }

    /**
     * Cache a block and return if it is water, all in the same method!
     *
     * @param pos BlockPos to cache and check if its water
     * @return Returns if the BlockPos is water or not - NOT if the block was cached successfully(!!!), as you'd normally expect from a method like this
     */
    public boolean cacheAndIsWater(BlockPos pos) {
        return cachedBlocks.computeIfAbsent(pos, pos1 -> TidalWaveHandler.posIsWater(world, pos1));
    }

    public void markFinished() {
        this.finished = true;
    }

    public boolean isFinished() {
        return this.finished;
    }
}

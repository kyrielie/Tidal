package net.superkat.tidal.water;

import com.google.common.collect.Maps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;
import net.superkat.tidal.TidalWaveHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scans a chunk to create a list of created water bodies and shorelines
 * @see WaterBody
 * @see Shoreline
 */
public class ChunkScanner {
    //blocks which have been checked to be water or not water
    public Map<BlockPos, Boolean> cachedBlocks = Maps.newHashMap();

    //blocks which have had their neighbours checked(scanned) as water or not water, and added to water body/shoreline
    public Map<BlockPos, Boolean> visitedBlocks = Maps.newHashMap();

    public Iterator<BlockPos> cachedIterator = null;

    //the created water bodies and shorelines from this scanner
    public List<WaterBody> waterBodies = Lists.newArrayList();
    public List<Shoreline> shorelines = Lists.newArrayList();

    public final ClientWorld world;
    public Camera camera;
    public Chunk chunk;
    public BlockPos startPos;
    public BlockPos endPos;
    public boolean finished = false;

    public ChunkScanner(ClientWorld world, Camera camera, Chunk chunk, int minY, int maxY) {
        this.world = world;
        this.camera = camera;
        this.chunk = chunk;
        this.startPos = ChunkSectionPos.from(chunk).getMinPos().withY(minY);
        this.endPos = this.startPos.add(15, 0, 15).withY(maxY);
    }

    /**
     * Scans the next block which should be scanned
     */
    public void tick() {
        Iterator<BlockPos> iterator = stack();

        if(iterator.hasNext()) {
            BlockPos next = iterator.next();
            scanPos(next);
        } else {
            //debug
            MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
            markFinished();
        }
    }

    /**
     * @return An iterator which represents the next blocks in line to be scanned
     */
    public Iterator<BlockPos> stack() {
        if(cachedIterator != null) return cachedIterator;
        cachedIterator = BlockPos.iterate(startPos, endPos).iterator();
        return cachedIterator;
    }

    /**
     * Scan a block to be water or not water.
     * <br><br>If water, the immediately surrounding neighbours in a "+" shape are also checked if they have already been scanned/cached, and are merged into a water body/shoreline(if already scanned/cached).
     *
     * @param pos Block pos to scan
     */
    public void scanPos(BlockPos pos) {
        //if already visited or is air -> return
        if(visitedBlocks.containsKey(pos)) return;
        if(world.isAir(pos)) return;

        boolean posIsWater = TidalWaveHandler.posIsWater(world, pos);
        if(posIsWater) { //move to top of water
            pos = TidalWaveHandler.topOfWater(world, pos);
            if(visitedBlocks.containsKey(pos)) return; //check again because of move
        }

        visitedBlocks.put(new BlockPos(pos), posIsWater);
//        if(!posIsWater) return;

        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); //they keep getting more cursed

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkBlock = pos.offset(direction);
//            if(!visitedBlocks.containsKey(checkBlock)) continue;
            boolean neighborIsWater = false;
            if(!visitedBlocks.containsKey(checkBlock)) {
                if(posIsWater) continue;
                boolean checkBlockIsWater = TidalWaveHandler.posIsWater(world, checkBlock);
                if(checkBlockIsWater) continue;
                if(startPos.getX() > checkBlock.getX() || startPos.getZ() > checkBlock.getZ()
                    || endPos.getX() < checkBlock.getX() || endPos.getZ() < checkBlock.getZ()) continue;
                nonWaterBlocks.add(checkBlock);
            } else {
                neighborIsWater = visitedBlocks.get(checkBlock);
            }

//            boolean neighborIsWater = visitedBlocks.get(checkBlock);
            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
            if(neighborIsWater) waterBlocks.add(checkBlock);
            else nonWaterBlocks.add(checkBlock);
        }

        if(!nonWaterBlocks.isEmpty()) {
            //get corners - corners should be checked for shorelines to increase chance of being merged
            BlockPos[] corners = new BlockPos[]{pos.add(1, 0, 1), pos.add(-1, 0, 1), pos.add(-1, 0, -1), pos.add(1, 0, -1)};
            for (BlockPos corner : corners) {
//                boolean cornerIsWater;
//                if(!visitedBlocks.containsKey(corner)) {
//                    if(posIsWater) continue;
//                    cornerIsWater = TidalWaveHandler.posIsWater(world, pos);
//                    if(cornerIsWater) continue;
//                } else {
//                    cornerIsWater = visitedBlocks.get(corner);
//                }
                if(!visitedBlocks.containsKey(corner)) continue;
                if(!visitedBlocks.get(corner)) nonWaterBlocks.add(corner.toImmutable());
                else if(!posIsWater) waterBlocks.add(corner.toImmutable());

                //if hasn't been visited yet or neighbor is water AND init pos is water -> continue(don't add to nonWaterBlocks)
//                if(!visitedBlocks.containsKey(corner) || (visitedBlocks.get(corner) && posIsWater)) continue;
//                nonWaterBlocks.add(corner);
            }

            //water has to be next to the shoreline
            if(!waterBlocks.isEmpty()) {
                Shoreline shoreline = new Shoreline().withBlocks(nonWaterBlocks);
                if (!tryMergeShoreline(shoreline)) {
                    this.shorelines.add(shoreline);
                }
            }
        }

        if(waterBlocks.isEmpty() && !posIsWater) return;

        WaterBody waterBody = new WaterBody().withBlocks(waterBlocks);
        if(tryMergeWaterBody(waterBody)) return;
        this.waterBodies.add(waterBody);
    }

//    public boolean tryMergeTracker(List<? extends AbstractBlockSetTracker> trackers, AbstractBlockSetTracker merge) {
//        if(trackers.isEmpty()) return false;
//        Set<? extends AbstractBlockSetTracker> checkedTrackers = Set.of();
//        for (BlockPos pos : merge.blocks) {
//            Set<? extends AbstractBlockSetTracker> trackersWithPos = trackers.stream()
//                    .filter(
//                            checkTracker ->
//                                    checkTracker.blocks.stream().anyMatch(pos1 -> pos1.equals(pos))
//                    )
//                    .collect(Collectors.toSet());
//            if(trackersWithPos.isEmpty()) continue;
//            checkedTrackers = trackersWithPos;
//            break;
//        }
//
//        if(checkedTrackers.isEmpty()) return false;
//        AbstractBlockSetTracker prevTracker = merge;
//        for (AbstractBlockSetTracker tracker : checkedTrackers) {
//            tracker.merge(prevTracker);
//            trackers.remove(tracker);
//            prevTracker = tracker;
//        }
//        return true;
//    }


    public boolean tryMergeWaterBody(WaterBody waterBody) {
        Set<WaterBody> checkedWaterBodies = Set.of();
        for (BlockPos pos : waterBody.blocks) {
            Set<WaterBody> waterBodies = posInWaterBodies(pos);
            if(waterBodies.isEmpty()) continue;
            checkedWaterBodies = waterBodies;
            break;
        }

        if(checkedWaterBodies.isEmpty()) return false;

        WaterBody prevWaterBody = waterBody;
        for (WaterBody waterBody1 : checkedWaterBodies) {
            waterBody1.merge(prevWaterBody);
            this.waterBodies.remove(prevWaterBody);
            prevWaterBody = waterBody1;
        }

        return true;
    }

    public boolean tryMergeShoreline(Shoreline shoreline) {
        Set<Shoreline> checkedShorelines = Set.of();
        for (BlockPos pos : shoreline.blocks) {
            Set<Shoreline> shorelines = posInShorelines(pos);
            if(shorelines.isEmpty()) continue;
            checkedShorelines = shorelines;
            break;
        }

        if(checkedShorelines.isEmpty()) return false;

        Shoreline prevShore = shoreline;
        for (Shoreline shoreline1 : checkedShorelines) {
            shoreline1.merge(prevShore);
            this.shorelines.remove(prevShore);
            prevShore = shoreline1;
        }

        return true;
    }

    public Set<WaterBody> posInWaterBodies(BlockPos pos) {
        return waterBodies.stream().filter(checkedWater -> checkedWater.blocks.stream().anyMatch(pos1 -> pos1.equals(pos)))
                .collect(Collectors.toSet());
    }

    public Set<Shoreline> posInShorelines(BlockPos pos) {
        return shorelines.stream().filter(checkedShoreline -> checkedShoreline.blocks.stream().anyMatch(pos1 -> pos1.equals(pos)))
                .collect(Collectors.toSet());
    }

    /**
     * Use the camera's pos instead of player pos, because it is less likely to be null. The player becomes null upon death, and reset after respawning, while the camera remains the same.
     *
     * @return The camera's BlockPos
     */
    public BlockPos cameraPos() {
        return this.camera.getBlockPos();
    }

    public void markFinished() {
        this.finished = true;
    }

    public boolean isFinished() {
        return this.finished;
    }
}

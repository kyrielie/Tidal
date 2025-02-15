package net.superkat.tidal.water.voronoi;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.superkat.tidal.water.ChunkScanner;
import net.superkat.tidal.water.WaterBody;
import net.superkat.tidal.water.WaterBodyHandler;
import org.apache.commons.compress.utils.Lists;

import java.util.List;

public class VoronoiChunkScanner extends ChunkScanner {
    public int shorelineBlocksSinceSite = 0;

    public VoronoiChunkScanner(WaterBodyHandler handler, ClientWorld world, ChunkPos chunkPos) {
        super(handler, world, chunkPos);
    }

    @Override
    public void scanPos(BlockPos pos) {
        //if already visited or is air -> return
        if(visitedBlocks.contains(pos)) return;
        if(world.isAir(pos)) return;

        //mark visited
        boolean posIsWater = cacheAndIsWater(pos);
        visitedBlocks.add(pos);

        //block is either the top of water, or a different non-air block
        //shorelines need to be checked for still
        List<BlockPos> nonWaterBlocks = Lists.newArrayList();
        List<BlockPos> waterBlocks = Lists.newArrayList();
        if (posIsWater) waterBlocks.add(pos); else nonWaterBlocks.add(pos); //they keep getting more cursed
        List<Float> shorelineYaw = Lists.newArrayList();

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(direction);
            if(world.isAir(checkPos)) continue;
            boolean neighborIsWater = cacheAndIsWater(checkPos);
            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
            //if init scan pos is water OR if the check pos is the top of water
            if(neighborIsWater) waterBlocks.add(checkPos);
            else {
                nonWaterBlocks.add(checkPos);
                if(posIsWater) shorelineYaw.add(direction.asRotation());
            }
        }

        if(waterBlocks.isEmpty()) return;

        if(!nonWaterBlocks.isEmpty()) {
            //no water bodies should be created from non-water scanned pos
            //returning here to reduce the amount this check is called every so slightly
            if(!posIsWater) return;

            for (BlockPos nonWater : nonWaterBlocks) {
                long chunkPosL = new ChunkPos(nonWater).toLong();
                this.handler.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(nonWater);
            }

            this.shorelineBlocksSinceSite += nonWaterBlocks.size();
            if(this.shorelineBlocksSinceSite >= 8) {
                SitePos site = new SitePos(pos);
                this.handler.sites.computeIfAbsent(this.chunkPos.toLong(), aLong -> new ObjectOpenHashSet<>()).add(site);
                this.shorelineBlocksSinceSite = 0;
            }
        }

        WaterBody waterBody = new WaterBody().withBlocks(waterBlocks);
        if(waterBody.getBlocks().isEmpty() || handler.tryMergeWaterBody(waterBody)) return;
        this.handler.waterBodies.add(waterBody);
    }

    //
//    @Override
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
//
//        for (Direction direction : Direction.Type.HORIZONTAL) {
//            BlockPos checkPos = pos.offset(direction);
//            if(world.isAir(checkPos)) continue;
//            boolean neighborIsWater = cacheAndIsWater(checkPos);
//            //that is super cursed but okay - no that's actually incredibly cursed(wow I spelt that right first try)
//            //if init scan pos is water OR if the check pos is the top of water
//            if(neighborIsWater) waterBlocks.add(checkPos);
//            else nonWaterBlocks.add(checkPos);
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
//
//            if(posIsWater) {
//                shorelineBlocksSinceSite += nonWaterBlocks.size();
//                if(shorelineBlocksSinceSite >= 8) {
//                    createSite(pos);
//                    shorelineBlocksSinceSite = 0;
//                }
//            }
//
//            Shoreline shoreline = new Shoreline().withBlocks(nonWaterBlocks);
//            if (!shoreline.getBlocks().isEmpty() && !handler.tryMergeShoreline(shoreline)) this.handler.shorelines.add(shoreline);
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
//
//    public void createSite(BlockPos pos) {
//        SitePos site = new SitePos(pos);
//        this.handler.sites.add(site);
//    }
//
//    @Override
//    public void markFinished() {
//        List<BlockPos> scannedWaterBlocks = this.cachedBlocks.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
//
////        for (BlockPos water : scannedWaterBlocks) {
////            double distance = 0;
////            SitePos closest = null;
////            for (SitePos site : this.handler.sites) {
////                BlockPos pos = site.pos;
////                double dx = pos.getX() + 0.5 - water.getX();
////                double dz = pos.getZ() + 0.5 - water.getZ();
////                double dist = dx * dx + dz * dz;
////                if(closest == null || dist < distance) {
////                    closest = site;
////                    distance = dist;
////                }
////            }
////            if(closest == null) continue;
////            closest.region.addBlock(water);
////        }
//
//        super.markFinished();
//    }
}

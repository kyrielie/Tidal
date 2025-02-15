package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;
import net.superkat.tidal.water.voronoi.SitePos;
import net.superkat.tidal.water.voronoi.VoronoiChunkScanner;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies.
 * <br><br>
 * How this goofy thing works: <br>
 * The {@link WaterBodyHandler#scanners} holds a map of {@link ChunkPos} as longs & {@link ChunkScanner}, making each scanner linked to a ChunkPos.<br><br>
 * {@link WaterBodyHandler#scheduleChunkScanner(ChunkPos)} schedules a chunk scanner, <b>called once a chunk is loaded</b>, which will begin ticking in the {@link WaterBodyHandler#tickScheduledScanners(ClientPlayerEntity)} method. Each scanner gets ticked 10 times per client tick, which was done to reduce the amount of lag per chunk load(instead of scanning the entire chunk during chunk load). <br><br>
 * A ChunkScanner scans a single chunk, creating WaterBodies and Shorelines which get added here upon their creation(in their tick method).<br><br>
 * Once the ChunkScanner is finished, its value in the scanners map <b>is set to null</b>, meaning the <b>ChunkPos long key still exists</b>. This was done to allow for all chunk scanners, water bodies, & shorelines, to be reset without missing any chunks.<br><br>
 * The idea behind this was that ChunkPos's are added to the scanners map upon a chunk being loaded. The keys(ChunkPos longs) in the scanners map are used during the {@link WaterBodyHandler#rebuild()} method to setup ChunkScanners for all the chunks. If it was removed, getting all loaded chunks is surprisingly difficult, as there is no accessible method. I decided instead of using mixins/access wideners to get it, I would do this instead.<br><br>
 * Scanning a specific radius of nearby chunks could cause some already loaded chunks(which would have been removed from the scanners map) to be missed, as they would not call the load chunk event. Even though they would be far away initially, moving toward them would be very noticeable. Note: client render distance doesn't always equal server render distance, and it isn't possible to get the server render distance on the client without an AW.<br><br>
 * A ChunkPos(as a long) is removed from the scanners when that chunk is unloaded. Each WaterBody/Shoreline has their own method which removes the blocks associated with that ChunkPos.<br><br>
 * ChunkPos' are updated and rescanned after a configurable amount of block updates within that chunk.
 *
 * @see TidalWaveHandler
 * @see ChunkScanner
 * @see BlockSetTracker
 */
public class WaterBodyHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //stol... inspired by BiomeColorCache
    //The long is a chunkpos, with a scanner being attributed to it (i have no clue what anything means but ok)
    public Long2ObjectLinkedOpenHashMap<ChunkScanner> scanners = new Long2ObjectLinkedOpenHashMap<>(256, 0.25f);

    //Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough updates
    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(256, 0.25f);

    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!
    public Set<WaterBody> waterBodies = new ReferenceOpenHashSet<>();

//    public Object2ObjectOpenHashMap<BlockPos, SitePos> siteCache = new Object2ObjectOpenHashMap<>();

    public Object2ObjectOpenHashMap<SitePos, Set<BlockPos>> debugSiteCache = new Object2ObjectOpenHashMap<>();

    public ObjectArrayFIFOQueue<BlockPos> waitingWaterBlocks = new ObjectArrayFIFOQueue<>();
    public Long2ObjectLinkedOpenHashMap<Set<SitePos>> sites = new Long2ObjectLinkedOpenHashMap<>(256, 0.25f);
    public Long2ObjectLinkedOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> siteCache = new Long2ObjectLinkedOpenHashMap<>();

    public Long2ObjectLinkedOpenHashMap<Set<BlockPos>> shoreBlocks = new Long2ObjectLinkedOpenHashMap<>(256, 0.25f);


    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    //TODO - THE ULTIMATE PLAN
    //Chunk scanners should:
    // - find shoreline sites
    // - add scanned water blocks to a queue here
    //That queue will be iterated through to find a water block's closest site after all activate scanners are done
    //Scanners, sites, and queued blocks need to be sorted via chunks(longs).
    //Once all scanners are done, the water block queue begins.
    //Each block will be a key with a site pos, sorted via chunks(siteCache) (this is the version of regions)
    //water bodies will be replaced with these regions. Some of them can still be pretty big, and probably provide more accurate size data per area anyways.

    //FIXME - init build doesn't get all nearby chunks(reload build scans more chunks than join build)

    //TODO - chunk ticker(holds chunk scanner, blockpos site cache ticking) (may not be worth it with new idea?)

    //TODO - optimize this - save yaw angle in site, and then try to use that info for spawning waves
    //TODO - optimize the voronoi diagram stuff, put sites/regions in water bodies?
    //TODO - optmize shorelines, as they are now no longer seperated
    //TODO - delete old stuff, e.g. water scanner, chunk info (chunk info still useless)

    //TODO - get all nearby loaded chunks to allow for scanners to have chunk scanners removed to save memory
    public WaterBodyHandler(ClientWorld world, TidalWaveHandler tidalWaveHandler) {
        this.world = world;
        this.tidalWaveHandler = tidalWaveHandler;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!built && tidalWaveHandler.nearbyChunksLoaded) {
            built = build();
        }

        tickScheduledScanners(player);
        debugTick(client, player);
    }

    @Nullable
    public SitePos findClosestSite(BlockPos pos) {
        //TODO - check if chunk has site first, if not then check for closest chunk pos, then check that chunk for site?
        if(this.sites.isEmpty()) return null;

        Set<SitePos> siteSet;
        long chunkPosL = new ChunkPos(pos).toLong();
        if(this.sites.get(chunkPosL) != null) {
            siteSet = this.sites.get(chunkPosL);
        } else {
            siteSet = this.sites.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        }

        double distance = 0;
        SitePos closest = null;
        for (SitePos site : siteSet) {
            double dx = pos.getX() + 0.5 - site.getX();
            double dz = pos.getZ() + 0.5 - site.getZ();
            double dist = dx * dx + dz * dz;

//            if(closest == null || dist < distance || (dist == distance && world.random.nextBoolean())) {
            if(closest == null || dist < distance) {
                closest = site;
                distance = dist;
            }
        }
        return closest;

//        double distance = 0;
//        BlockPos closest = null;
//        return this.chunkedBlocks.values().stream().flatMap(trackerChunkInfo -> trackerChunkInfo.blocks.stream()).collect(Collectors.toSet());
//        for (BlockPos shore : this.shoreBlocks.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())) {
//            double dx = pos.getX() + 0.5 - shore.getX();
//            double dz = pos.getZ() + 0.5 - shore.getZ();
//            double dist = dx * dx + dz * dz;
//            if(closest == null || dist < distance || (dist == distance && world.random.nextBoolean())) {
//                closest = shore;
//                distance = dist;
//            }
//        }
//        return closest;
    }

    public BlockPos getSiteForPos(BlockPos pos) {
//        SitePos site = this.siteCache.computeIfAbsent(pos, pos1 -> {
//            SitePos closest = findClosestSite(pos);
//            if(closest != null) closest.addPos(pos);
//            return closest;
//        });
        long chunkPosL = new ChunkPos(pos).toLong();
        SitePos site = this.siteCache
                .computeIfAbsent(chunkPosL,
                        chunkPosL2 -> new Object2ObjectOpenHashMap<>()
                ).computeIfAbsent(pos, pos1 -> {
                    SitePos closest = findClosestSite(pos);
                    if(closest != null) closest.addPos(pos);
                    return closest;
                });
        if(site == null) return BlockPos.ORIGIN;
        return site.getPos();
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;

//        if(player.getMainHandStack().isOf(Items.COMPASS)) {
//            for (WaterBody waterBody : this.waterBodies) {
//                for (Map.Entry<Long, BlockSetTracker.TrackedChunk> entry : waterBody.chunkedBlocks.entrySet()) {
//                    Long aLong = entry.getKey();
//                    BlockSetTracker.TrackedChunk trackedChunk = entry.getValue();
//                    if(!trackedChunk.shouldMergeYaw) continue;
//                    ChunkPos cpos = new ChunkPos(aLong);
//                    int x = cpos.x;
//                    int z = cpos.z;
//                    BlockSetTracker.TrackedChunk[] neighbours = new BlockSetTracker.TrackedChunk[]{
//                            waterBody.chunkedBlocks.get(new ChunkPos(x + 1, z).toLong()),
//                            waterBody.chunkedBlocks.get(new ChunkPos(x - 1, z).toLong()),
//                            waterBody.chunkedBlocks.get(new ChunkPos(x, z + 1).toLong()),
//                            waterBody.chunkedBlocks.get(new ChunkPos(x, z - 1).toLong())
//                    };
//
//                    float totalYaw = trackedChunk.getYaw();
//                    int addedYaws = 1;
//
//                    for (BlockSetTracker.TrackedChunk n : neighbours) {
//                        if(n == null) continue;
//                        if(!n.shouldMergeYaw) continue;
//                        totalYaw += n.getYaw();
//                        addedYaws++;
//                    }
//
//                    if(addedYaws != 1) totalYaw /= addedYaws;
//                    BlockPos pos = new ChunkPos(aLong).getCenterAtY(67);
//                    Direction direction = Direction.fromRotation(totalYaw);
//                    BlockPos pos2 = pos.offset(direction);
//
//                    this.world.addParticle(ParticleTypes.WAX_OFF, pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0);
//                    this.world.addParticle(ParticleTypes.NAUTILUS, pos2.getX(), pos2.getY(), pos2.getZ(), pos.getX() - pos2.getX(), 0, pos.getZ() - pos2.getZ());
//                    this.world.addParticle(ParticleTypes.WAX_ON, pos2.getX(), pos2.getY(), pos2.getZ(), 0, 0, 0);
//
//                    if(player.isSneaking()) {
//                        if(aLong == player.getChunkPos().toLong()) {
//                            System.out.println(totalYaw);
//                        }
//                    }
//
//                }
//            }
//        }

        int i = 0;
        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.world.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 4, site.getZ() + 0.5, 0, 0, 0);
            Color color = debugColor(i, allSites.size());
            i++;

            Set<BlockPos> region = this.debugSiteCache.computeIfAbsent(site, site1 ->
                    this.siteCache.values().stream()
                            .flatMap(
                                    map -> map.entrySet().stream()
                                            .filter(entry -> entry.getValue() == site)
                            ).map(Map.Entry::getKey)
                            .collect(Collectors.toSet()));

            for (BlockPos blockPos : region) {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, pos.getX(), pos.getY() + 3, pos.getZ(), 0, 0, 0);
            }
        }


//        int i = 0;
//        for (SitePos sitePos : this.sites) {
//            this.world.addParticle(ParticleTypes.EGG_CRACK, true, sitePos.getX() + 0.5, sitePos.getY() + 4, sitePos.getZ() + 0.5, 0, 0, 0);
//            RegionSet region = sitePos.region;
//            if(region.blocks.size() < 10) continue;
//            Color color = debugColor(i, region.blocks.size());
//            i++;
//            region.blocks.forEach(blockPos -> {
//                Vec3d pos = blockPos.toCenterPos();
//                ParticleEffect particleEffect = new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
//                this.world.addParticle(particleEffect, pos.getX(), pos.getY() + 3, pos.getZ(), 0, 0, 0);
//            });
//        }

//        debugTrackerParticles(this.waterBodies, true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
//        debugTrackerParticles(this.shorelines, false, farParticles, false);
    }

    private void debugTrackerParticles(Set<? extends BlockSetTracker> trackers, boolean waterBodyParticle, boolean farParticle, boolean showCenter) {
        int i = 0;
        List<Color> colors = Lists.newArrayList();
        for (BlockSetTracker tracker : trackers) {
            Color color = debugColor(i, trackers.size());
            colors.add(color);
            i++;
            tracker.getBlocks().forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = waterBodyParticle ? new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f)
                        : new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });

            if(showCenter) {
                Vec3d center = tracker.centerPos().toCenterPos();
                this.world.addParticle(ParticleTypes.EXPLOSION, center.getX(), center.getY() + 1, center.getZ(), 0, 0, 0);
            }
        }
    }

    //sick
    private Color debugColor(int i, int size) {
        if(i == 0) { return Color.white; }
        //super ultra cursed debug colors - wait actually I'm a bit of a genuius
        //confusing, mind confusing confused, don't understand no snese uh - confusing
        int i1 = i > 3 ? 255 - (((((i - 3) / 3) + 1) * 30) % 255) : 0;
        int i2 = i > 3 ? 255 - (((((i - 3) / 3) + 1) * 30) / 255) * 30 : 255;
        int red = i == 1 ? 255 : i == 2 || i == 3 ? 0 : (i % 3 == 1 ? i1 : 255);
        int green = i == 2 ? 255 : i == 1 || i == 3 ? 0 : (i % 3 == 2 ? i1 : 255);
        int blue = i == 3 ? 255 : i == 1 || i == 2 ? 0 : (i % 3 == 0 ? i1 : i2);
        return new Color(checkColor(red), checkColor(green), checkColor(blue));
    }

    private int checkColor(int color) {
        if(color > 255) return 255;
        return Math.max(color, 0); //wow intellij really smart
    }

    public void updateBlock(BlockPos pos, BlockState state) {
        long chunkPosL = new ChunkPos(pos).toLong();
        int currentUpdates = this.chunkUpdates.getOrDefault(chunkPosL, 0) + 1;
        if(currentUpdates >= TidalConfig.chunkUpdatesRescanAmount) {
            if(this.rescheduleChunkScanner(new ChunkPos(chunkPosL))) {
                currentUpdates = 0;
            }
        }
        this.chunkUpdates.put(chunkPosL, currentUpdates);
    }

    /**
     * Builds all the chunk scanners available to be built(within config range).
     * <br><br>Meant to be called during world join & chunk reload, where it is expected for high amounts of calculations, and lag, to happen.
     * @return If the build was successful
     */
    public boolean build() {
        long overallStartTime = Util.getMeasuringTimeMs();
        Tidal.LOGGER.info("-=+=========================+=-");

        long scannerTime = Util.getMeasuringTimeMs();
        for (ChunkScanner scanner : this.scanners.values()) {
            if(scanner == null) continue;
//            long scannerStartTime = Util.getMeasuringTimeMs();
            while (!scanner.isFinished()) {
                scanner.tick();
                if(scanner.isFinished()) {
                    scanners.put(scanner.chunkPos.toLong(), null);
                    MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.05f, 1f);
//                    Tidal.LOGGER.info("Scanner time: {} ms", Util.getMeasuringTimeMs() - scannerStartTime);
                }
            }
        }
        Tidal.LOGGER.info("Total scan time: {} ms", Util.getMeasuringTimeMs() - scannerTime);

        long siteCacheTime = Util.getMeasuringTimeMs();
        for (WaterBody waterBody : this.waterBodies) {
            for (BlockPos blockPos : waterBody.getBlocks()) {
                getSiteForPos(blockPos);
            }
        }
        Tidal.LOGGER.info("Site cache time: {} ms", Util.getMeasuringTimeMs() - siteCacheTime);

        long siteCenterCalcTime = Util.getMeasuringTimeMs();
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
        Tidal.LOGGER.info("Site center calc time: {} ms", Util.getMeasuringTimeMs() - siteCenterCalcTime);

        Tidal.LOGGER.info("Total Chunk Build time: {} ms", Util.getMeasuringTimeMs() - overallStartTime);
        Tidal.LOGGER.info("-=+=========================+=-");

        return true;
    }

    /**
     * easy method to clear & rebuild
     */
    public void rebuild() {
        this.clear(); //clear waterbodies and shorelines

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            long pos = chunkPos.toLong();
            this.scanners.put(pos, new VoronoiChunkScanner(this, this.world, chunkPos));
        }

        this.built = build();
    }

    public void tickScheduledScanners(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int chunkRadius = TidalConfig.chunkRadius; //caching this call might help?
        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            if(entry.getValue() == null) continue;
            long chunkPosL = entry.getKey();
            if(!scannerInDistance(playerPos, chunkPosL, chunkRadius)) continue;

            ChunkScanner scanner = entry.getValue();
            for (int i = 0; i < 10; i++) {
                scanner.tick();
            }
            if(scanner.isFinished()) {
                scanners.put(chunkPosL, null);
                MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.25f, 1f);
            }
        }
    }

    public boolean scannerInDistance(BlockPos playerBlockPos, long chunkPosL, int chunkRadius) {
        ChunkPos pos = new ChunkPos(chunkPosL);
        ChunkPos playerPos = new ChunkPos(playerBlockPos);
        int distance = playerPos.getChebyshevDistance(pos);

        return distance < chunkRadius;
    }

    /**
     * Schedules a chunk to be scanned(calculates y coords for you)
     *
     * @param chunk Chunk to schedule
     * @see WaterBodyHandler#scheduleChunkScanner(ChunkPos)
     */
    public void scheduleChunk(Chunk chunk) {
        scheduleChunkScanner(chunk.getPos());
    }

    /**
     * Schedules a chunk to be scanned
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @see WaterBodyHandler#scheduleChunk(Chunk)
     */
    public void scheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        this.scanners.computeIfAbsent(pos, pos1 -> new VoronoiChunkScanner(this, this.world, chunkPos));
    }

    /**
     * Schedules a ChunkPos to be rescanned, removing blocks in the chunk from waterbodies and shorelines in the process.
     *
     * @param chunkPos ChunkPos to remove
     * @return If the reschedule was successful. It will return false if a scanner is already associated with the ChunkPos
     */
    public boolean rescheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        if (this.scanners.get(pos) != null) return false;
        this.scanners.put(pos, new VoronoiChunkScanner(this, this.world, chunkPos));
        this.removeChunkFromTrackers(pos);
        return true;
    }

    public void removeChunk(Chunk chunk) {
        long chunkPosL = chunk.getPos().toLong();
        this.removeChunkFromTrackers(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.scanners.remove(chunkPosL);
    }

    public void removeChunkFromTrackers(long chunkPosL) {
//        List<BlockPos> removedWaterBlocks = new ObjectArrayList<>();
        this.waterBodies.forEach(waterBody -> {
//            removedWaterBlocks.addAll(waterBody.chunkedBlocks.get(chunkPosL).getBlocks());
            waterBody.removeChunkBlocks(chunkPosL);
        });
//        removedWaterBlocks.forEach(this.siteCache.keySet()::remove);
        this.shoreBlocks.remove(chunkPosL);
        this.siteCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);

        //remove empty water body
        this.waterBodies.removeIf(waterBody -> waterBody.chunkedBlocks.isEmpty());
    }

    /**
     * Attempt to merge a water body into another(adds the water body's blocks into another)
     *
     * @param waterBody The water body to attempt to merge into already existing water bodies
     * @return If the water body was successfully merged
     */
    public boolean tryMergeWaterBody(WaterBody waterBody) {
        Set<WaterBody> checkedWaters = waterBodiesInAnother(waterBody);
        if(checkedWaters.isEmpty()) return false;

        WaterBody previousWater = waterBody;
        for (WaterBody waterBody1 : checkedWaters) {
            waterBody1.merge(previousWater);
            waterBodies.remove(previousWater);
            previousWater = waterBody1;
        }
        return true;
    }

    /**
     * Check if a water body's set of positions overlaps with other water bodies.
     *
     * @param waterBody The water body whose positions should be checked
     * @return The set of water bodies that share any amount of BlockPos positions with the given water body
     */
    public Set<WaterBody> waterBodiesInAnother(WaterBody waterBody) {
        LongSet chunkPosLs = waterBody.chunkedBlocks.keySet();
        if(chunkPosLs.isEmpty()) return Collections.emptySet();
        return waterBodies.stream()
                .filter(checkedWater ->
                    chunkPosLs.longStream().anyMatch(chunkPosL ->
                            checkedWater.chunkedBlocks.keySet().contains(chunkPosL)
                                    && checkedWater.chunkedBlocks.get(chunkPosL).getBlocks().stream()
                                    .anyMatch(pos -> waterBody.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos)))
//                                    && checkedWater.chunkedBlocks.get(chunkPosL).stream()
//                                    .anyMatch(pos -> waterBody.chunkedBlocks.get(chunkPosL).contains(pos)))
                    ).collect(Collectors.toSet());
    }

    @Nullable
    public WaterBody posInWaterBody(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return waterBodies.stream()
                .filter(waterBody -> waterBody.chunkedBlocks.keySet().contains(chunkPosL)
                        && waterBody.chunkedBlocks.get(chunkPosL).getBlocks().contains(pos))
                .findAny()
                .orElse(null);
    }

    /**
     * Remove a BlockPos from all water bodies.
     * @param pos The BlockPos to be removed
     */
    public void removePosFromWaterBodies(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        for (WaterBody waterBody : waterBodies) {
            //don't want to call getBlockSet without it containing it since it computes the chunk value if missing
            if(!waterBody.chunkedBlocks.containsKey(chunkPosL)) continue;
            waterBody.getBlockSet(pos).remove(pos);
        }
    }

    public void clear() {
        clearWaterBodies();
        clearShorelines();
        this.sites.clear();
        this.siteCache.clear();
        this.debugSiteCache.clear();
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }

    public void clearShorelines() {
        this.shoreBlocks.clear();
    }
}

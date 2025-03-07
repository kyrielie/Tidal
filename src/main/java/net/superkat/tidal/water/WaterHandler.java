package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
import net.superkat.tidal.DebugHelper;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles water/shoreline blocks & SitePos'
 * <br><br>
 * How this goofy thing works:<br><br>
 *
 * Chunk loaded -> {@link WaterHandler#addChunk(Chunk)} -> schedules a {@link ChunkScanner} to the {@link WaterHandler#scanners} Map.<br><br>
 *
 * A {@link ChunkScanner} iterates through a chunk's blocks(sampling heightmap), checking if each block is water or not.<br>
 * <b>- If water -></b> The BlockPos is added to {@link WaterHandler#waitingWaterBlocks} Queue(split per chunk).<br>
 * That water block's neighbours are also checked for water.<br>
 * <i>--- If neighbour is water -></i> Also added to the waitingWaterBlocks Queue<br>
 * <i>--- If neighbour is not water -></i> Added to the {@link WaterHandler#shoreBlocks} Map(split per chunk).<br>For every 8 shoreline blocks, {@link WaterHandler#createSitePos(BlockPos)} is called, creating a {@link SitePos} with the initial scanned BlockPos and is added to the {@link WaterHandler#sites} Map(split per chunk).<br><br>
 *
 * A ChunkScanner is associated with each loaded chunk, but will not tick unless within the config radius. {@link WaterHandler#tickScheduledScanners(ClientPlayerEntity)} ticks all scheduled ChunkScanners(within range) 10 times, scanning 10 blocks per chunk.<br><br>
 *
 * Once a ChunkScanner is done, <b>IT'S SCANNER VALUE IS SET TO NULL</b> in the scanners Map!!! Meaning the ChunkPos(long) key still exists, but its value will be null.<br>
 * This was done to allow for all loaded chunks to remain accessible for the {@link WaterHandler#rebuild()} method, and to make it easy to start scanning a loaded chunk when it becomes in range.<br><br>
 *
 * For any given chunk, once all nearby ChunkScanners in a 3 chunk radius are finished scanning, {@link WaterHandler#tickWaitingWaterBlocks(boolean)} ticks 10 water blocks per chunk.<br>
 * A "waiting water block" is any water block which has not had a SitePos calculated as the closest yet.<br><br>
 *
 * Ticking a waiting water block finds the closest SitePos via {@link WaterHandler#findClosestSite(long, BlockPos)}, and removes it from the waitingWaterBlocks Queue and placing it in the {@link WaterHandler#waterCache} Map(key being the BlockPos, value being the SitePos).<br><br>
 *
 * Chunk unloaded -> {@link WaterHandler#removeChunk(Chunk)}. Because nearly everything is split per chunk via Maps, all keys with that ChunkPos(as a long) are removed, removing the values with it.<br><br>
 *
 * Join world -> Loads many chunks(calling the chunk load event) -> {@link WaterHandler#build()} is called, which ticks everything until all is finished. This does not use the tickScheduledScanners method, but does use the tickWaitingWaterBlocks method. Called after enough nearby chunks are loaded via {@link TidalWaveHandler#nearbyChunksLoaded(ClientPlayerEntity)}.<br><br>
 *
 * Block updated -> {@link WaterHandler#onBlockUpdate(BlockPos, BlockState)}. A count of all block updates per chunk is kept track of in {@link WaterHandler#chunkUpdates}.<br>After enough block updates in a chunk(configurable), that chunk will be rescanned via {@link WaterHandler#rescheduleChunkScanner(ChunkPos)}.
 *
 * @see TidalWaveHandler
 * @see ChunkScanner
 * @see SitePos
 */
public class WaterHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!

    //Chunk scanner for each chunk - SCANNER VALUE SET TO NULL ONCE SCANNER IS DONE!!
    //The chunk is not removed from this list until it is unloaded!
    public Long2ObjectOpenHashMap<ChunkScanner> scanners = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough(configurable) updates
    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    //Set of water blocks waiting to have their closest site found
    public Long2ObjectOpenHashMap<ObjectArrayFIFOQueue<BlockPos>> waitingWaterBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //Set of shoreline sites, used to determine angle of area
    public Long2ObjectOpenHashMap<ObjectOpenHashSet<SitePos>> sites = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //Caches all the sites into one set, not split by chunk.
    public ObjectOpenHashSet<SitePos> cachedSiteSet = new ObjectOpenHashSet<>();

    //Keep track of which SitePos is closest to all scanned water blocks
    public Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> waterCache = new Long2ObjectOpenHashMap<>();

    //This is being kept as its own map only for now. I considered a few other options, but didn't know which one was better:
    // - it's own map(already done)
    // - Changing the waterCache's Object2ObjectMap to have a ObjectIntPair<SitePos> for the BlockPos value, instead of the current SitePos
    // - Changing the waterCache's Object2ObjectMap to a Table<SitePos, Integer, Set<BlockPos>> instead

    //That third option would be the easiest to work with after everything was cached,
    //but I was afraid that the process of checking if a Set<BlockPos> had already been created for a Table,
    //and then checking if that set contained the BlockPos given to WaterHandler#findClosestSite would be too expensive

    //The second option I was afraid would be too expensive when trying to find all water blocks within a specific distance,
    //as you'd need to filter through all of them each time you wanted to find a block within a specific distance

    //this is the equal to Map<ChunkPos, Map<Integer, Set<BlockPos>>>, which may not be great performance-wise?
    //alternatively, use Long2ObjectOpenHashMap<HashMultimap<Integer, BlockPos>>????
    public Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ObjectOpenHashSet<BlockPos>>> waterDistCache = new Long2ObjectOpenHashMap<>();

    //All scanned shoreline blocks
    public Long2ObjectOpenHashMap<Set<BlockPos>> shoreBlocks = new Long2ObjectOpenHashMap<>(81, 0.25f);

    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    //boolean for if the centers of ALL sites should be recalculated - it's a very fast calculation, so I feel comfortable doing it all at once
    public boolean recalcSiteCenters = true;

    //idea: if no site is within configurable distance, that water is considered open ocean and extra effects can be added there
    //idea 2: if the amount of blocks associated with a SitePos is really small, non-directional ambient particles spawn

    //TODO - update waterDistCache to be better?
    //TODO - possibly change build to use the tick scanners method instead of doing it all itself?
    //TODO - make it so that the scanners map clears finished chunks to free up memory (surprisingly difficult to do)
    //FIXME - loading new chunks quickly doesn't always work as fast as expected(in the same chunk) - a priority queue for closer chunks?
    //FIXME - init build doesn't get all nearby chunks(reload build scans more chunks than join build)

    public WaterHandler(TidalWaveHandler tidalWaveHandler, ClientWorld world) {
        this.tidalWaveHandler = tidalWaveHandler;
        this.world = world;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!built && tidalWaveHandler.nearbyChunksLoaded) {
            built = build();
        }

        if(built) {
            boolean noMoreWaitingBlocks = tickWaitingWaterBlocks(false);
            if(noMoreWaitingBlocks && recalcSiteCenters) { //failsafe I guess?
                this.calcAllSiteCenters();
                this.recalcSiteCenters = false;
            }
        }

        tickScheduledScanners(player);

        debugTick(client, player);
    }

    /**
     * Gets a Set of BlockPos' that are a specified distance away from their closest SitePos within a ChunkPos. Used for spawning waves.
     *
     * @param chunkPos The ChunkPos to get the water blocks from
     * @param distance The distance to check for
     * @return The Set of BlockPos, or null if none are found.
     */
    @Nullable
    public Set<BlockPos> getWaterCacheAtDistance(ChunkPos chunkPos, int distance) {
        long chunkPosL = chunkPos.toLong();
        if(this.waterDistCache.containsKey(chunkPosL)) return this.waterDistCache.get(chunkPosL).get(distance);
        return null;
    }

    /**
     * Cache and or return the closest SitePos of a BlockPos(assumed to be, but technically doesn't have to be, a water block).
     *
     * @param pos BlockPos to use for finding the closest SitePos.
     * @return The BlockPos' closest SitePos, or {@link BlockPos#ORIGIN} if the site is null.
     */
    public SitePos getSiteForPos(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        return this.waterCache
                .computeIfAbsent(chunkPosL,
                        chunkPosL2 -> new Object2ObjectOpenHashMap<>()
                ).computeIfAbsent(pos, pos1 -> {
                    SitePos closest = findClosestSite(chunkPosL, pos);
                    if(closest != null) closest.addPos(pos);
                    return closest;
                });
    }

    /**
     * Calculates the closest SitePos from a BlockPos. Used by {@link WaterHandler#getSiteForPos(BlockPos)}.
     *
     * @param pos The BlockPos to use for finding the closest SitePos
     * @return The closest SitePos, or null if no SitePos' are currently stored.
     */
    @Nullable //FIXME - optimize this(Hama said it should be easy)
    public SitePos findClosestSite(long chunkPosL, BlockPos pos) {
        if(this.sites.isEmpty()) return null;

        if(this.cachedSiteSet == null || this.cachedSiteSet.isEmpty()) {
            this.cacheSiteSet();
        }

        double distance = 0;
        SitePos closest = null;
        for (SitePos site : this.cachedSiteSet) {
            double dx = pos.getX() + 0.5 - site.getX();
            double dz = pos.getZ() + 0.5 - site.getZ();
            double checkDist = dx * dx + dz * dz;
//            double checkDist = Math.max(Math.abs(dx), Math.abs(dz));
//            double checkDist = Math.abs(dx) + Math.abs(dz);

            if(closest == null || checkDist < distance) {
                closest = site;
                distance = checkDist;
            }
        }

        if(closest != null) {
            int intDistance = (int) Math.sqrt(distance);
//            int intDistance = (int) distance;
            this.waterDistCache.computeIfAbsent(
                    chunkPosL, chunkPosL2 -> new Int2ObjectOpenHashMap<>()
            ).computeIfAbsent(
                    intDistance, dist -> new ObjectOpenHashSet<>()
            ).add(pos);
        }

        return closest;
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {
        if(this.world.getTime() % 10 != 0) return;
        boolean farParticles = false;

        //display all shoreline blocks
        //display all sitePos'
        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.world.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 2, site.getZ() + 0.5, 0, 0, 0);
        }

        if(!DebugHelper.spyglassInHotbar()) return;

        //display all shoreline blocks
        List<BlockPos> allShoreBLocks = this.shoreBlocks.values().stream().flatMap(Collection::stream).toList();
        ParticleEffect shoreEffect = new DebugShoreParticle.DebugShoreParticleEffect(Vec3d.unpackRgb(Color.WHITE.getRGB()).toVector3f(), 1f);
        for (BlockPos shore : allShoreBLocks) {
            Vec3d pos = shore.toCenterPos();
            this.world.addParticle(shoreEffect, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
        }

        //display all water blocks pos', colored by closest site
        int totalSites = allSites.size();
        for (Object2ObjectOpenHashMap<BlockPos, SitePos> posSiteMap : this.waterCache.values()) {
            for (Map.Entry<BlockPos, SitePos> entry : posSiteMap.entrySet()) {
                BlockPos blockPos = entry.getKey();
                if(!blockPos.isWithinDistance(player.getPos(), 100)) continue;
                SitePos site = entry.getValue();

                int siteIndex = allSites.indexOf(site);
                Color color = DebugHelper.debugColor(siteIndex, totalSites);

                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = new DebugWaterParticle.DebugWaterParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticles, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            }
        }
    }

    /**
     * Called during a block update. Used to count how many block updates have happened in any given chunk. After enough updates in a chunk, that chunk is rescanned.
     *
     * @param pos The BlockPos that was updated
     * @param state The new BlockState of the updated BlockPos
     */
    public void onBlockUpdate(BlockPos pos, BlockState state) {
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
        int chunksScanned = 0;

        for (ChunkScanner scanner : this.scanners.values()) {
            if (scanner == null) continue;
//            long scannerStartTime = Util.getMeasuringTimeMs();

            chunksScanned++;
            while (!scanner.isFinished()) {
                scanner.tick();
                if (scanner.isFinished()) {
//                    finishedScanners.add(scanner.chunkPos.toLong());
                    this.scanners.put(scanner.chunkPos.toLong(), null);
                    MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.05f, 1f);
//                    Tidal.LOGGER.info("Scanner time: {} ms", Util.getMeasuringTimeMs() - scannerStartTime);
                }
            }
        }

        Tidal.LOGGER.info("Total scan time: {} ms", Util.getMeasuringTimeMs() - scannerTime);
        Tidal.LOGGER.info("Chunks scanned: {}", chunksScanned);

        long siteCacheTime = Util.getMeasuringTimeMs();
        boolean blocksWaiting = true;
        while(blocksWaiting) { //cursed but okay
            blocksWaiting = !tickWaitingWaterBlocks(true);
        }
        Tidal.LOGGER.info("Site cache time: {} ms", Util.getMeasuringTimeMs() - siteCacheTime);

        long siteCenterCalcTime = Util.getMeasuringTimeMs();
        this.calcAllSiteCenters();
        Tidal.LOGGER.info("Site center calc time: {} ms", Util.getMeasuringTimeMs() - siteCenterCalcTime);

        Tidal.LOGGER.info("Total Chunk Build time: {} ms", Util.getMeasuringTimeMs() - overallStartTime);
        Tidal.LOGGER.info("-=+=========================+=-");

        return true;
    }

    /**
     * Easy method to clear & rescan chunks - called from chunk reload(f3+a)
     */
    public void rebuild() {
        this.clear(); //clear all data(ticking scanners -> null, sites/shoreblocks/waterblocks all cleared)

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.long2ObjectEntrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            long pos = chunkPos.toLong();
            this.scanners.put(pos, new ChunkScanner(this, this.world, chunkPos));
        }

        this.built = build();
    }

    /**
     * Ticks scheduled ChunkScanenrs, setting their value to null if they are finished.
     *
     * @param player The Client's Player(used for distance calculation)
     */
    public void tickScheduledScanners(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int chunkRadius = this.tidalWaveHandler.getChunkRadius(); //caching this call might help?

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.long2ObjectEntrySet()) {
            if (entry.getValue() == null) continue;

            long chunkPosL = entry.getKey();
            if (!scannerInDistance(playerPos, chunkPosL, chunkRadius)) continue;

            ChunkScanner scanner = entry.getValue();
            for (int i = 0; i < 10; i++) {
                scanner.tick();
            }

            if (scanner.isFinished()) {
                scanners.put(chunkPosL, null);
                MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.25f, 1f);
            }
        }
    }

    /**
     * Find the closest SitePos for all scanned water blocks, and remove them from the queue.
     * <br><br>Ticks 10 blocks at a time per chunk.
     *
     * @param assumeFullScan Should assume all chunks have been fully scanned or not. If true, the check for nearby scanners will not be called.
     *
     * @return True if there are no more waiting water blocks
     */
    public boolean tickWaitingWaterBlocks(boolean assumeFullScan) {
        LongArrayList finishedBlocks = new LongArrayList();
        for (Map.Entry<Long, ObjectArrayFIFOQueue<BlockPos>> entry : this.waitingWaterBlocks.long2ObjectEntrySet()) {
            if (entry.getValue() == null) continue;
            long chunkPosL = entry.getKey();
            //the nearbyScannersFinished call could probably be optimized?
            if (!assumeFullScan && !nearbyScannersFinished(chunkPosL)) continue;

            ObjectArrayFIFOQueue<BlockPos> queue = entry.getValue();
            boolean finished = false;
            for (int i = 0; i < 10; i++) {
                if (queue.size() <= 0) {
                    finished = true;
                    break;
                }
                BlockPos pos = queue.dequeue();
                getSiteForPos(pos); //caches pos' closest site
            }

            if (finished || queue.size() <= 0) {
                finishedBlocks.add(chunkPosL);
                recalcSiteCenters = true;
                this.calcSiteCenter(chunkPosL);
                MinecraftClient.getInstance().player.playSound(SoundEvents.BLOCK_VAULT_ACTIVATE, 0.1f, 1f);
            }
        }

        finishedBlocks.forEach(chunkPosL -> this.waitingWaterBlocks.remove(chunkPosL));

        return this.waitingWaterBlocks.isEmpty();
    }

    /**
     * Checks if the nearby ChunkScanners from a ChunkPos(as a long) are finished.
     *
     * @param chunkPosL The ChunkPos(long) to check.
     * @return If the nearby ChunkScanners are finished.
     */
    public boolean nearbyScannersFinished(long chunkPosL) {
        int radius = 1; //will be configurable later
        ChunkPos pos = new ChunkPos(chunkPosL);
        ChunkPos start = new ChunkPos(pos.x + radius, pos.z + radius);
        ChunkPos end = new ChunkPos(pos.x - radius, pos.z - radius);

        BlockPos playerPos = MinecraftClient.getInstance().player.getBlockPos();
        int chunkRadius = this.tidalWaveHandler.getChunkRadius();

        for (ChunkPos checkPos : ChunkPos.stream(start, end).toList()) {
            long checkPosL = checkPos.toLong();
            if(!scannerInDistance(playerPos, checkPosL, chunkRadius)) continue;
            if(scanners.get(checkPosL) != null) return false;
        }
        return true;
    }

    public void calcSiteCenter(long chunkPosL) {
        if(!this.sites.containsKey(chunkPosL)) return;
        for (SitePos site : this.sites.get(chunkPosL)) {
            site.updateCenter();
        }
        MinecraftClient.getInstance().player.playSound(SoundEvents.BLOCK_BEEHIVE_ENTER, 0.3f, 1f);
    }

    //I feel comfortable doing this because this calculation is usually only taken 1-3ms for me
    public void calcAllSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
        MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1f);
    }

    /**
     * Checks if a ChunkPos(as a long) is within the ChunkScanner activation distance
     *
     * @param playerBlockPos The Client's Player's current BlockPos
     * @param chunkPosL The chunk to check
     * @param chunkRadius The radius of the activation distance(is a param to cache calling it from the config)
     * @return If the ChunkPos(as a long) is within the ChunkScanner activation distance.
     */
    public boolean scannerInDistance(BlockPos playerBlockPos, long chunkPosL, int chunkRadius) {
        ChunkPos pos = new ChunkPos(chunkPosL);
        ChunkPos playerPos = new ChunkPos(playerBlockPos);
        int distance = playerPos.getChebyshevDistance(pos);

        return distance < chunkRadius;
    }

    public void cacheSiteSet() {
        //caching this list saved ~200ms during a build/rebuild
        this.cachedSiteSet = new ObjectOpenHashSet<>(this.sites.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    /**
     * Schedules a chunk to be scanned water blocks, shoreblocks, sites, etc. Called when a new chunk is loaded.
     *
     * @param chunk Chunk to schedule
     * @see WaterHandler#scheduleChunkScanner(ChunkPos)
     */
    public void addChunk(Chunk chunk) {
        scheduleChunkScanner(chunk.getPos());
    }

    /**
     * Schedules a chunk to be scanned for water blocks, shoreblocks, sites, etc.
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @see WaterHandler#addChunk(Chunk)
     */
    public void scheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        this.scanners.computeIfAbsent(pos, pos1 -> new ChunkScanner(this, this.world, chunkPos));
    }

    /**
     * Schedules a ChunkPos to be rescanned, removing the chunk's blocks from all trackers.
     *
     * @param chunkPos ChunkPos to remove
     * @return If the reschedule was successful. It will return false if a scanner is already associated with the ChunkPos
     */
    public boolean rescheduleChunkScanner(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.toLong();
        this.scheduleChunkScanner(chunkPos);
        this.removeChunkFromTrackers(chunkPosL);
        return true;
    }

    /**
     * Queues a collection of water blocks into the waitingWaterBlocks Queue.
     *
     * @param blocks The collection of water blocks to queue.
     */
    public void queueWaterBlocks(Collection<BlockPos> blocks) {
        for (BlockPos water : blocks) {
            long chunkPosL = new ChunkPos(water).toLong();
            this.waitingWaterBlocks.computeIfAbsent(chunkPosL, aLong ->  new ObjectArrayFIFOQueue<>()).enqueue(water);
        }
    }

    /**
     * Creates a SitePos at the given BlockPos
     *
     * @param pos The given BlockPos
     */
    public void createSitePos(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        SitePos site = new SitePos(pos);
        sites.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(site);
        this.cachedSiteSet.add(site);
    }

    /**
     * Adds a shoreline block(assumed to not be water)
     *
     * @param pos The shoreline BlockPos
     */
    public void addShorelineBlock(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        this.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(pos);
    }

    /**
     * Fully removes a chunk form all trackers, scanners, and updates. Called once a chunk is unloaded.
     *
     * @param chunk The ChunkPos(as a long) to remove
     */
    public void removeChunk(Chunk chunk) {
        long chunkPosL = chunk.getPos().toLong();
        this.removeChunkFromTrackers(chunkPosL);
        this.chunkUpdates.remove(chunkPosL);
        this.scanners.remove(chunkPosL);
    }

    /**
     * Removes a chunk from all trackers, e.g. waitingWaterBlocks, sites, siteCache, shoreblocks.
     * <br><br>The chunk remains in the scanners & chunkUpdates maps, as it is assumed it is still loaded.
     *
     * @param chunkPosL The ChunkPos(as a long) to remove
     */
    public void removeChunkFromTrackers(long chunkPosL) {
        this.waitingWaterBlocks.remove(chunkPosL);
        this.shoreBlocks.remove(chunkPosL);
        this.waterCache.remove(chunkPosL);
        this.waterDistCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);
        this.cachedSiteSet.clear(); //resets it
    }

    public void clear() {
        this.waitingWaterBlocks.clear();
        this.shoreBlocks.clear();
        this.sites.clear();
        this.waterCache.clear();
        this.waterDistCache.clear();
        this.cachedSiteSet.clear();
    }
}

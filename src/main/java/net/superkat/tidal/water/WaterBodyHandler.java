package net.superkat.tidal.water;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
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
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShoreParticle;
import net.superkat.tidal.particles.debug.DebugWaterParticle;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Iterator;
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
 */
public class WaterBodyHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    //using fastutils because... it has fast in its name? I've been told its fast! And I gotta go fast!

    //Chunk scanner for each chunk - SCANNER VALUE SET TO NULL ONCE SCANNER IS DONE!!
    //The chunk is not removed from this list until it is unloaded!
    public Long2ObjectLinkedOpenHashMap<ChunkScanner> scanners = new Long2ObjectLinkedOpenHashMap<>(81, 0.25f);

    //Keep track of how many block updates have happened in a chunk - used to rescan chunks after enough(configurable) updates
    public Long2IntOpenHashMap chunkUpdates = new Long2IntOpenHashMap(81, 0.25f);

    //Set of water blocks waiting to have their closest site found
    public Long2ObjectLinkedOpenHashMap<ObjectArrayFIFOQueue<BlockPos>> waitingWaterBlocks = new Long2ObjectLinkedOpenHashMap<>(81, 0.25f);

    //Set of shoreline sites, used to determine angle of area
    public Long2ObjectLinkedOpenHashMap<Set<SitePos>> sites = new Long2ObjectLinkedOpenHashMap<>(81, 0.25f);

    //Keep track of which SitePos is closest to all scanned water blocks
    public Long2ObjectLinkedOpenHashMap<Object2ObjectOpenHashMap<BlockPos, SitePos>> siteCache = new Long2ObjectLinkedOpenHashMap<>();

    //All scanned shoreline blocks
    public Long2ObjectLinkedOpenHashMap<Set<BlockPos>> shoreBlocks = new Long2ObjectLinkedOpenHashMap<>(81, 0.25f);

    //boolean for if the initial joining/chunk reloading build is finished or not
    public boolean built = false;

    //boolean for if any scanner is currently ticking - used for knowing when to wait to queue through any waiting water blocks
    public boolean anyScannerActive = true;

    //boolean for if the centers of ALL sites should be recalculated - it's a very fast calculation, so I feel comfortable doing it all at once
    public boolean recalcSiteCenters = true;

    //TODO - THE ULTIMATE PLAN
    //Chunk scanners should:
    // - find shoreline sites
    // - add scanned water blocks to a queue here
    //That queue will be iterated through to find a water block's closest site after all activate scanners are done
    //Scanners, sites, and queued blocks need to be sorted via chunks(longs).
    //Once all scanners are done, the water block queue begins.
    //Each block will be a key with a site pos, sorted via chunks(siteCache) (this is the version of regions)
    //water bodies will be replaced with these regions. Some of them can still be pretty big, and probably provide more accurate size data per area anyways.

    //idea: if no site is within configurable distance, that water is considered open ocean and extra effects can be added there

    //TODO - make it so that the scanners map clears finished chunks to free up memory (surprisingly difficult to do)
    //TODO - replace anyScannerActive with any scanner active within a configurable chunk radius

    //FIXME - init build doesn't get all nearby chunks(reload build scans more chunks than join build)

    //TODO - test movement particle

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

        if(built && !anyScannerActive) {
            boolean noMoreWaitingWaterBlocks = tickWaitingWaterBlocks();
            if(noMoreWaitingWaterBlocks && recalcSiteCenters) {
                this.calcSiteCenters();
                recalcSiteCenters = false;
            }
        }
        tickScheduledScanners(player);

        debugTick(client, player);
    }

    /**
     * Calculates the closest SitePos from a BlockPos. Used by {@link WaterBodyHandler#getSiteForPos(BlockPos)}.
     *
     * @param pos The BlockPos to use for finding the closest SitePos
     * @return The closest SitePos, or null if no SitePos' are currently stored.
     */
    @Nullable
    public SitePos findClosestSite(BlockPos pos) {
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

            if(closest == null || dist < distance) {
                closest = site;
                distance = dist;
            }
        }
        return closest;
    }

    /**
     * Cache and or return the closest SitePos of a BlockPos(assumed to be, but technically doesn't have to be, a water block).
     *
     * @param pos BlockPos to use for finding the closest SitePos.
     * @return The BlockPos' closest SitePos, or {@link BlockPos#ORIGIN} if the site is null.
     */
    public BlockPos getSiteForPos(BlockPos pos) {
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

//        int i = 0;

        //display all shoreline blocks
        List<BlockPos> allShoreBLocks = this.shoreBlocks.values().stream().flatMap(Collection::stream).toList();
        ParticleEffect shoreEffect = new DebugShoreParticle.DebugShoreParticleEffect(Vec3d.unpackRgb(Color.WHITE.getRGB()).toVector3f(), 1f);
        for (BlockPos shore : allShoreBLocks) {
            Vec3d pos = shore.toCenterPos();
            this.world.addParticle(shoreEffect, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
        }

        //display all sitePos'
        List<SitePos> allSites = this.sites.values().stream().flatMap(Collection::stream).toList();
        for (SitePos site : allSites) {
            this.world.addParticle(ParticleTypes.EGG_CRACK, true, site.getX() + 0.5, site.getY() + 2, site.getZ() + 0.5, 0, 0, 0);
        }

        //display all water blocks pos', colored by closest site
        int totalSites = allSites.size();
        for (Object2ObjectOpenHashMap<BlockPos, SitePos> posSiteMap : this.siteCache.values()) {
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
                    scanners.put(scanner.chunkPos.toLong(), null);
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
            blocksWaiting = !tickWaitingWaterBlocks();
        }
        Tidal.LOGGER.info("Site cache time: {} ms", Util.getMeasuringTimeMs() - siteCacheTime);

        long siteCenterCalcTime = Util.getMeasuringTimeMs();
        this.calcSiteCenters();
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

        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            long pos = chunkPos.toLong();
            this.scanners.put(pos, new ChunkScanner(this, this.world, chunkPos));
        }

        this.built = build();
    }

    public void tickScheduledScanners(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        int chunkRadius = this.tidalWaveHandler.getChunkRadius(); //caching this call might help?
        for (Map.Entry<Long, ChunkScanner> entry : this.scanners.sequencedEntrySet()) {
            if (entry.getValue() == null) continue;

            long chunkPosL = entry.getKey();
            if (!scannerInDistance(playerPos, chunkPosL, chunkRadius)) continue;
            anyScannerActive = true;

            ChunkScanner scanner = entry.getValue();
            for (int i = 0; i < 10; i++) {
                scanner.tick();
            }

            if (scanner.isFinished()) {
                anyScannerActive = false; //gets set back to true during the loop here, if it is the last one then oh well
                scanners.put(chunkPosL, null);
                MinecraftClient.getInstance().player.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.25f, 1f);
            }
        }
    }

    /**
     * Find the closest SitePos for all scanned water blocks, and remove them from the queue.
     * <br><br>Ticks 10 blocks at a time per chunk.
     *
     * @return True if there are no more waiting water blocks
     */
    public boolean tickWaitingWaterBlocks() {
        Iterator<Map.Entry<Long, ObjectArrayFIFOQueue<BlockPos>>> iterator = this.waitingWaterBlocks.sequencedEntrySet().iterator();

        while(iterator.hasNext()) {
            Map.Entry<Long, ObjectArrayFIFOQueue<BlockPos>> entry = iterator.next();
            if(entry.getValue() == null) continue;

            ObjectArrayFIFOQueue<BlockPos> queue = entry.getValue();
            boolean finished = false;
            for (int i = 0; i < 10; i++) {
                if(queue.size() <= 0) {
                    finished = true;
                    break;
                }
                BlockPos pos = queue.dequeue();
                getSiteForPos(pos); //caches pos' closest site
            }

            if(finished || queue.size() <= 0) {
                iterator.remove();
                MinecraftClient.getInstance().player.playSound(SoundEvents.BLOCK_VAULT_ACTIVATE, 0.1f, 1f);
                recalcSiteCenters = true;
            }
        }

        return this.waitingWaterBlocks.isEmpty();
    }

    //I feel comfortable doing this because this calculation is usually only taken 1-3ms for me
    public void calcSiteCenters() {
        for (SitePos site : this.sites.values().stream().flatMap(Collection::stream).toList()) {
            site.updateCenter();
        }
        MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1f);
    }

    public boolean scannerInDistance(BlockPos playerBlockPos, long chunkPosL, int chunkRadius) {
        ChunkPos pos = new ChunkPos(chunkPosL);
        ChunkPos playerPos = new ChunkPos(playerBlockPos);
        int distance = playerPos.getChebyshevDistance(pos);

        return distance < chunkRadius;
    }

    /**
     * Schedules a chunk to be scanned water blocks, shoreblocks, sites, etc. Called when a new chunk is loaded.
     *
     * @param chunk Chunk to schedule
     * @see WaterBodyHandler#scheduleChunkScanner(ChunkPos)
     */
    public void addChunk(Chunk chunk) {
        scheduleChunkScanner(chunk.getPos());
    }

    /**
     * Schedules a chunk to be scanned for water blocks, shoreblocks, sites, etc.
     *
     * @param chunkPos ChunkPos of the chunk to be scanned
     * @see WaterBodyHandler#addChunk(Chunk)
     */
    public void scheduleChunkScanner(ChunkPos chunkPos) {
        long pos = chunkPos.toLong();
        this.scanners.computeIfAbsent(pos, pos1 -> new ChunkScanner(this, this.world, chunkPos));
        this.anyScannerActive = true;
    }

    /**
     * Schedules a ChunkPos to be rescanned, removing the chunk's blocks from all trackers.
     *
     * @param chunkPos ChunkPos to remove
     * @return If the reschedule was successful. It will return false if a scanner is already associated with the ChunkPos
     */
    public boolean rescheduleChunkScanner(ChunkPos chunkPos) {
        long chunkPosL = chunkPos.toLong();
//        if (this.scanners.get(chunkPosL) != null) return false;
        this.scheduleChunkScanner(chunkPos);
        this.removeChunkFromTrackers(chunkPosL);
        return true;
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
        this.siteCache.remove(chunkPosL);
        this.sites.remove(chunkPosL);
    }

    public void queueWaterBlocks(Collection<BlockPos> blocks) {
        for (BlockPos water : blocks) {
            long chunkPosL = new ChunkPos(water).toLong();
            this.waitingWaterBlocks.computeIfAbsent(chunkPosL, aLong ->  new ObjectArrayFIFOQueue<>()).enqueue(water);
        }
    }

    public void createSitePos(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        SitePos site = new SitePos(pos);
        sites.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(site);
    }

    public void addShorelineBlock(BlockPos pos) {
        long chunkPosL = new ChunkPos(pos).toLong();
        this.shoreBlocks.computeIfAbsent(chunkPosL, aLong -> new ObjectOpenHashSet<>()).add(pos);
    }

    public void clear() {
        this.waitingWaterBlocks.clear();
        this.shoreBlocks.clear();
        this.sites.clear();
        this.siteCache.clear();
    }
}

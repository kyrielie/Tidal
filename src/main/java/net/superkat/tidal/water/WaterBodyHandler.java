package net.superkat.tidal.water;

import com.google.common.collect.Sets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.superkat.tidal.Tidal;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.debug.DebugShorelineParticle;
import net.superkat.tidal.particles.debug.DebugWaterBodyParticle;
import org.apache.commons.compress.utils.Lists;

import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies
 *
 * @see TidalWaveHandler
 */
public class WaterBodyHandler {
    public final TidalWaveHandler tidalWaveHandler;
    public final ClientWorld world;
    public List<ChunkScanner> scanners = Lists.newArrayList();
    public Set<WaterBody> waterBodies = Sets.newHashSet();
    public Set<Shoreline> shorelines = Sets.newHashSet();
    public boolean built = false;

    public WaterScanner waterScanner;

    public WaterBodyHandler(ClientWorld world, TidalWaveHandler tidalWaveHandler) {
        this.world = world;
        this.tidalWaveHandler = tidalWaveHandler;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(!built && tidalWaveHandler.nearbyChunksLoaded) {
            built = build(player);
        }

        ChunkScanner scanner = scanners.isEmpty() ? null : scanners.getFirst();

        if(TidalWaveHandler.debugTick() && scanner != null && player.getItemUseTime() == 10) {
            Entity entity = client.getCameraEntity();
            BlockHitResult blockHitResult = (BlockHitResult) entity.raycast(20, 0f, true);
            BlockPos raycastPos = blockHitResult.getBlockPos();
            scanner.scanPos(raycastPos);
            player.playSound(SoundEvents.BLOCK_VAULT_ACTIVATE, 1f, 1f);
        }

        if(TidalWaveHandler.altDebugTick() && !scanners.isEmpty()) {
            for (int i = 0; i < 256; i++) {
                scanner.tick();
            }
            if(scanner.isFinished()) {
                this.shorelines.addAll(scanner.shorelines);
                this.waterBodies.addAll(scanner.waterBodies);
                scanners.removeFirst();
            }
        }

//        debugTick(client, player);

        //particles
        if(world.getTime() % 10 != 0) return;
        if(scanner != null) {
            Color color = Color.LIGHT_GRAY;
            scanner.visitedBlocks.forEach((blockPos, aBoolean) -> {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = aBoolean ? new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f)
                        : new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, false, pos.getX(), pos.getY() + 0.5, pos.getZ(), 0, 0, 0);
            });
        }

        boolean farParticles = false;
        if(scanner != null) {
            debugTrackerParticles(new HashSet<>(scanner.waterBodies), true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
            debugTrackerParticles(new HashSet<>(scanner.shorelines), false, farParticles, false);
        }

        debugTrackerParticles(this.waterBodies, true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
        debugTrackerParticles(this.shorelines, false, farParticles, false);
    }

    private void debugTick(MinecraftClient client, ClientPlayerEntity player) {

        //debug water scanner test
        if(TidalWaveHandler.altDebugTick()) {
            if(player.getOffHandStack().isOf(Items.CLOCK)) {
                if(player.isSneaking()) {
                    this.clear();
                    player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
                }

                Entity entity = client.getCameraEntity();
                BlockHitResult blockHitResult = (BlockHitResult) entity.raycast(20, 0f, true);
                BlockPos raycastPos = blockHitResult.getBlockPos();
                this.waterScanner = new WaterScanner(this, this.world, raycastPos);
                player.playSound(SoundEvents.BLOCK_VAULT_DEACTIVATE, 1f, 1f);
            } else {
                if(waterScanner != null) {
                    for (int i = 0; i < 10; i++) {
                        waterScanner.tick();
                        if (waterScanner.isFinished()) {
                            WaterBody waterBody = waterScanner.getWaterBody();
                            waterBodies.add(waterBody);
                            player.playSound(SoundEvents.ITEM_TRIDENT_THUNDER.value(), 1f, 1f);
                            waterScanner = null;
                            break;
                        }
                    }
                }
            }
        }

        //particles
        if(world.getTime() % 10 != 0) return;

        boolean farParticles = false;
        debugTrackerParticles(this.waterBodies, true, farParticles, player.getOffHandStack().isOf(Items.CLOCK));
        debugTrackerParticles(this.shorelines, false, farParticles, false);

        if(waterScanner != null) {
            for (BlockPos blockPos : this.waterScanner.visitedBlocks.keySet()) {
                Color color = Color.GRAY;
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f), farParticles, pos.getX(), pos.getY() + 0.85, pos.getZ(), 0, 0, 0);
            }

            for (BlockPos blockPos : this.waterScanner.cachedBlocks.keySet()) {
                Color color = Color.DARK_GRAY;
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f), farParticles, pos.getX(), pos.getY() + 0.65, pos.getZ(), 0, 0, 0);
            }
        }

    }

    private void debugTrackerParticles(Set<? extends AbstractBlockSetTracker> trackers, boolean waterBodyParticle, boolean farParticle, boolean showCenter) {
        int i = 0;
        for (AbstractBlockSetTracker tracker : trackers) {
            Color color = debugColor(i, trackers.size());
            i++;
            tracker.blocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                ParticleEffect particleEffect = waterBodyParticle ? new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f)
                        : new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f);
                this.world.addParticle(particleEffect, farParticle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });

            if(showCenter) {
                Vec3d center = tracker.center().toCenterPos();
                this.world.addParticle(ParticleTypes.EXPLOSION, center.getX(), center.getY() + 1, center.getZ(), 0, 0, 0);
            }
        }
    }

    //sick
    private Color debugColor(int i, int size) {
        if(i == 0) { return Color.white; }
        //super ultra cursed debug colors - wait actually I'm a bit of a genuius
        int red = i == 1 ? 255 : i == 2 || i == 3 ? 0 : (i % 3 == 1 ? 255 - (255 / size * (i - 3)) : 255);
        int green = i == 2 ? 255 : i == 1 || i == 3 ? 0 : (i % 3 == 2 ? 255 - (255 / size * (i - 3)) : 255);
        int blue = i == 3 ? 255 : i == 1 || i == 2 ? 0 : (i % 3 == 0 ? 255 - (255 / size * (i - 3)) : 255);
        return new Color(checkColor(red), checkColor(green), checkColor(blue));
    }

    private int checkColor(int color) {
        if(color > 255) return 255;
        return Math.max(color, 0); //wow intellij really smart
    }

    /**
     * Scans ALL nearby blocks within the config radius(that's probably a lot of blocks!)
     * <br><br>Meant to be called during world join & chunk reload, where it is expected for high amounts of calculations, and lag, to happen.
     * @return If the build was successful - it could fail if a chunk was empty, but should not happen
     */
    public boolean build(ClientPlayerEntity player) {
        int horizontalRadius = TidalConfig.horizontalDistance;
        int verticalRadius = TidalConfig.verticalDistance;
        BlockPos playerPos = player.getBlockPos();

        Set<WorldChunk> chunks = getNearbyChunks(player, horizontalRadius);
        int playerY = playerPos.getY();
        int minY = playerY - verticalRadius; //calc once here
        int maxY = playerY + verticalRadius;

        for (WorldChunk chunk : chunks) {
            if(chunk.isEmpty()) continue;
            ChunkScanner scanner = new ChunkScanner(world, MinecraftClient.getInstance().gameRenderer.getCamera(), chunk, minY, maxY);
            scanners.add(scanner);
        }

//        LOGGER.info("Time elapsed: {} ms", Util.getMeasuringTimeMs() - this.startTime);
//        if(scanner.isFinished()) {
//            this.shorelines.addAll(scanner.shorelines);
//            this.waterBodies.addAll(scanner.waterBodies);
//            scanners.removeFirst();
//        }

        long timeStart = Util.getMeasuringTimeMs();

        for (ChunkScanner scanner : this.scanners) {
            while (!scanner.isFinished()) {
                scanner.tick();
                this.shorelines.addAll(scanner.shorelines);
                this.waterBodies.addAll(scanner.waterBodies);
            }
        }

        this.scanners.clear();

        Tidal.LOGGER.info("Time taken for chunk scanners: {} ms", Util.getMeasuringTimeMs() - timeStart);

        return true;
    }

    public Set<WorldChunk> getNearbyChunks(ClientPlayerEntity player, int horizontalRadius) {
        ClientWorld world = player.clientWorld;
        BlockPos playerPos = player.getBlockPos();
        Set<WorldChunk> chunks = Sets.newHashSet();

        ChunkPos start = new ChunkPos(playerPos.add(-horizontalRadius, 0, -horizontalRadius));
        ChunkPos end = new ChunkPos(playerPos.add(horizontalRadius, 0, horizontalRadius));

        for (ChunkPos pos : ChunkPos.stream(start, end).toList()) {
            WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
            chunks.add(chunk);
        }

        return chunks;
    }

    public Set<ChunkSection> getChunkSections(WorldChunk chunk, int minY, int maxY) {
        int dist = maxY - minY;
        Set<ChunkSection> sections = Sets.newHashSet();
        for (int y = 0; y < dist; y += 16) {
            int sectionIndex = chunk.getSectionIndex(minY + y);
            ChunkSection section = chunk.getSection(sectionIndex);
            sections.add(section);
        }
        return sections;
    }

    public void tickBlockSetTracker(Set<? extends AbstractBlockSetTracker> set) {
        Iterator<? extends AbstractBlockSetTracker> iterator = set.iterator();
        while (iterator.hasNext()) {
            AbstractBlockSetTracker tracker = iterator.next();
            tracker.tick();
            if(tracker.shouldRemove()) {
                //occasionally remove water bodies/shorelines to clear up mistakes in block removing and other jank
                iterator.remove();
            }
        }
    }

//    public void tickRandomPos(ClientPlayerEntity player) {
//        BlockPos playerPos = player.getBlockPos();
//        BlockPos.Mutable pos = new BlockPos.Mutable();
//        int horizontalRadius = TidalConfig.horizontalWaveDistance;
//        int verticalRadius = TidalConfig.verticalWaveDistance;
////        int horizontalRadius = 32;
////        int verticalRadius = 16;
//        Random random = TidalWaveHandler.getRandom();
//
//        //get random position
//        int x = playerPos.getX() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
//        int y = playerPos.getY() + random.nextInt(verticalRadius) - random.nextInt(verticalRadius);
//        int z = playerPos.getZ() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
//        pos.set(x, y, z);
//
//        checkPos(pos);
//    }
//
//
//    public void checkPos(BlockPos pos) {
//        //FIXME - if a water body is trying to be merged inbetween two other water bodies while the center block is not water
//        //both water bodies will be merged despite not being connected
//
//        //3x1x3 blocks
//        Map<BlockPos, Boolean> neighbourBlocks = getBlockNeighbours(world, pos, 2);
//        Set<BlockPos> nonWater = neighbourBlocks.entrySet().stream()
//                .filter(entry -> !entry.getValue())
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toSet());
//
//        for (BlockPos nonWaterPos : nonWater) {
//            removePosFromWaterBodies(nonWaterPos);
//            neighbourBlocks.remove(nonWaterPos);
//        }
//
//        //neighbourBlocks are now all water blocks
//        if(neighbourBlocks.isEmpty()) return;
//
//        //create water body
//        WaterBody waterBody = new WaterBody();
//        waterBody.addNeighbours(world, neighbourBlocks.keySet());
//
//        //create shoreline
//        if(!nonWater.isEmpty()) {
//            //create shoreline - should only be done if a water body was created(water blocks are near)
//            Shoreline shoreline = new Shoreline();
//            shoreline.addBlocks(nonWater);
//            if(!tryMergeShoreline(shoreline)) this.shorelines.add(shoreline);
//        }
//
//        for (BlockPos waterPos : neighbourBlocks.keySet()) {
//            removePosFromShorelines(waterPos);
//        }
//
//        if(tryMergeWaterBody(waterBody)) return;
//        //if water body wasn't merged into another, add it to the set
//        this.waterBodies.add(waterBody);
//    }
//
//    public static Map<BlockPos, Boolean> getBlockNeighbours(ClientWorld world, BlockPos pos, int horizontalDistance) {
//        Map<BlockPos, Boolean> cachedBlocks = Maps.newHashMap();
//        for (BlockPos blockPos : BlockPos.iterate(pos.add(-horizontalDistance, 0, -horizontalDistance), pos.add(horizontalDistance, 0, horizontalDistance))) {
//            cachedBlocks.computeIfAbsent(blockPos.mutableCopy(), pos1 -> posIsWater(world, pos1));
//        }
//        return cachedBlocks;
//    }

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

    public boolean tryMergeShoreline(Shoreline shoreline) {
        Set<Shoreline> checkedShorelines = shorelinesInAnother(shoreline);
        if(checkedShorelines.isEmpty()) return false;

        Shoreline previousShore = shoreline;
        for (Shoreline shoreline1 : checkedShorelines) {
            shoreline1.merge(previousShore);
            shorelines.remove(previousShore);
            previousShore = shoreline1;
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
        return waterBodies.stream().filter(checkedWater -> waterBody.blocks.stream().anyMatch(pos -> checkedWater.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    public Set<Shoreline> shorelinesInAnother(Shoreline shoreline) {
        return shorelines.stream().filter(checkedShore -> shoreline.blocks.stream().anyMatch(pos -> checkedShore.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    /**
     * Remove a BlockPos from all water bodies.
     * <br><br>
     * {@link WaterBody#removeBlock(BlockPos)} is called to allow its tick value to be increased, making it get deleted sooner to fix any possible changed block jank
     * @param pos The BlockPos to be removed
     */
    public void removePosFromWaterBodies(BlockPos pos) {
        for (WaterBody waterBody : waterBodies) {
            waterBody.removeBlock(pos);
        }
    }

    public void removePosFromShorelines(BlockPos pos) {
        for (Shoreline shoreline : shorelines) {
            shoreline.removeBlock(pos);
        }
    }

    public boolean posInWaterBody(BlockPos pos) {
        return waterBodies.stream().anyMatch(waterBody -> waterBody.blocks.contains(pos));
    }

    public void clear() {
        clearWaterBodies();
        clearShorelines();
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }

    public void clearShorelines() {
        shorelines.clear();
    }
}

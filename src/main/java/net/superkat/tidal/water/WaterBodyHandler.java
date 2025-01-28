package net.superkat.tidal.water;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.particles.DebugShorelineParticle;
import net.superkat.tidal.particles.DebugWaterBodyParticle;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Used to handle(find/create, merge, delete) and tick water bodies
 *
 * @see TidalWaveHandler
 */
public class WaterBodyHandler {
    public final ClientWorld world;
    public final TidalWaveHandler tidalWaveHandler;
    public Set<WaterBody> waterBodies = Sets.newHashSet();
    public Set<Shoreline> shorelines = Sets.newHashSet();

    public WaterBodyHandler(ClientWorld world, TidalWaveHandler tidalWaveHandler) {
        this.world = world;
        this.tidalWaveHandler = tidalWaveHandler;
    }

    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        //debug tick test
        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            if(player.getItemUseTime() == 10) {
                player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            if(player.isSneaking()) {
                Entity entity = client.getCameraEntity();
                BlockHitResult blockHitResult = (BlockHitResult) entity.raycast(20, 0f, true);
                BlockPos raycastPos = blockHitResult.getBlockPos();
                checkPos(raycastPos);
            } else {
                for (int i = 0; i < 10; i++) {
                    tickRandomPos(player);
                }
            }
        }

//        if(player.getWorld().getTime() % 40 == 0) {
//            tick(player);
//        }

        //debug stuff
        if(world.getTime() % 10 != 0) return;
        int i = 0;
        for (WaterBody water : waterBodies) {
            Color color = debugColor(i, waterBodies.size());
            i++;
//            //super ultra cursed debug colors - wait actually I'm a bit of a genuius
//            int red = i == 1 ? 255 : i == 2 || i == 3 ? 0 : (i % 3 == 1 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            int green = i == 2 ? 255 : i == 1 || i == 3 ? 0 : (i % 3 == 2 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            int blue = i == 3 ? 255 : i == 1 || i == 2 ? 0 : (i % 3 == 0 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            Color color = new Color(red, green, blue);
            water.blocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(new DebugWaterBodyParticle.DebugWaterBodyParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f), pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });
        }

        i = 0;
        for (Shoreline shoreline : shorelines) {
            Color color = debugColor(i, shorelines.size());
            i++;
//            //super ultra cursed debug colors - wait actually I'm a bit of a genuius
//            int red = i == 1 ? 255 : i == 2 || i == 3 ? 0 : (i % 3 == 1 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            int green = i == 2 ? 255 : i == 1 || i == 3 ? 0 : (i % 3 == 2 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            int blue = i == 3 ? 255 : i == 1 || i == 2 ? 0 : (i % 3 == 0 ? 255 - (255 / waterBodies.size() * (i - 3)) : 255);
//            Color color = new Color(red, green, blue);
            shoreline.blocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(new DebugShorelineParticle.DebugShorelineParticleEffect(Vec3d.unpackRgb(color.getRGB()).toVector3f(), 1f), pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0, 0);
            });
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

    public void tickWaterBodies() {
        Iterator<WaterBody> iterator = this.waterBodies.iterator();

        while (iterator.hasNext()) {
            WaterBody waterBody = iterator.next();
            waterBody.tick();

            if(waterBody.shouldRemove()) {
                //occasionally remove water bodies to clear up mistakes in block removing and other jank
                iterator.remove();
            }
        }
    }

    public void tickRandomPos(ClientPlayerEntity player) {
        tickWaterBodies();

        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int horizontalRadius = TidalConfig.horizontalWaveDistance;
        int verticalRadius = TidalConfig.verticalWaveDistance;
//        int horizontalRadius = 128;
//        int verticalRadius = 32;
        Random random = TidalWaveHandler.getRandom();

        //get random position
        int x = playerPos.getX() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        int y = playerPos.getY() + random.nextInt(verticalRadius) - random.nextInt(verticalRadius);
        int z = playerPos.getZ() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        pos.set(x, y, z);

        checkPos(pos);
    }


    public void checkPos(BlockPos pos) {
        //FIXME - if a water body is trying to be merged inbetween two other water bodies while the center block is not water
        //both water bodies will be merged despite not being connected

        //3x1x3 blocks
        Map<BlockPos, Boolean> neighbourBlocks = getBlockNeighbour(world, pos, 1, 0, 0);
        Set<BlockPos> nonWater = neighbourBlocks.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (BlockPos nonWaterPos : nonWater) {
            removePosFromWaterBodies(nonWaterPos);
            neighbourBlocks.remove(nonWaterPos);
        }

        //neighbourBlocks are now all water blocks
        if(neighbourBlocks.isEmpty()) return;

        //create water body
        WaterBody waterBody = new WaterBody();
        waterBody.addNeighbours(world, neighbourBlocks.keySet());

        //create shoreline
        if(!nonWater.isEmpty()) {
            //create shoreline - should only be done if a water body was created(water blocks are near)
            Shoreline shoreline = new Shoreline();
            shoreline.addBlocks(nonWater);
            if(!tryMergeShoreline(shoreline)) this.shorelines.add(shoreline);
        }

        for (BlockPos water : neighbourBlocks.keySet()) {
            removePosFromShorelines(water);
        }

        if(tryMergeWaterBody(waterBody)) return;

        this.waterBodies.add(waterBody);
    }

    public static Map<BlockPos, Boolean> getBlockNeighbour(ClientWorld world, BlockPos pos, int horizontalDistance, int y1, int y2) {
        Map<BlockPos, Boolean> cachedBlocks = Maps.newHashMap();

        for (BlockPos blockPos : BlockPos.iterate(pos.add(-horizontalDistance, y1, -horizontalDistance), pos.add(horizontalDistance, y2, horizontalDistance))) {
            cachedBlocks.computeIfAbsent(blockPos.mutableCopy(), pos1 -> posIsWater(world, pos1));
        }
        return cachedBlocks;
    }

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

    public Set<WaterBody> waterBodiesInAnother(WaterBody waterBody) {
        return waterBodies.stream().filter(checkedWater -> waterBody.blocks.stream().anyMatch(pos -> checkedWater.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

    public Set<Shoreline> shorelinesInAnother(Shoreline shoreline) {
        return shorelines.stream().filter(checkedShore -> shoreline.blocks.stream().anyMatch(pos -> checkedShore.blocks.contains(pos)))
                .collect(Collectors.toSet());
    }

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

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        FluidState state = world.getFluidState(pos);
        return state.isIn(FluidTags.WATER);
    }

    public boolean posInWaterBody(BlockPos pos) {
        return waterBodies.stream().anyMatch(waterBody -> waterBody.blocks.contains(pos));
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }
}

package net.superkat.tidal;

import com.google.common.collect.Lists;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.superkat.tidal.config.TidalConfig;
import net.superkat.tidal.water.WaterBody;
import org.apache.commons.compress.utils.Sets;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class TidalWaveHandler {

    public final ClientWorld world;
    public Set<WaterBody> waterBodies = Sets.newHashSet();

    public TidalWaveHandler(ClientWorld world) {
        this.world = world;
    }

    //TODO - good luck syncing this client-side only lol
    public static Random getRandom() {
        return Random.create();
    }

    public void tickWorld() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        assert player != null;

        if(player.getActiveItem().isOf(Items.SPYGLASS) && player.getItemUseTime() >= 10) {
            for (int i = 0; i < 10; i++) {
                tickRandomWaterBody(player);
            }
        }

        //temp junk particles to see water bodies
        List<ParticleEffect> particles = Lists.newArrayList(
                ParticleTypes.BUBBLE_POP,
                ParticleTypes.FLAME,
                ParticleTypes.SOUL_FIRE_FLAME,
                ParticleTypes.SOUL,
                ParticleTypes.CHERRY_LEAVES,
                ParticleTypes.CRIT,
                ParticleTypes.ENCHANTED_HIT,
                ParticleTypes.SMOKE,
                ParticleTypes.WHITE_SMOKE,
                ParticleTypes.ASH,
                ParticleTypes.SPLASH,
                ParticleTypes.RAIN,
                ParticleTypes.WHITE_ASH,
                ParticleTypes.EGG_CRACK,
                ParticleTypes.WAX_OFF,
                ParticleTypes.WAX_ON,
                ParticleTypes.ELECTRIC_SPARK
        );

        int i = 0;
        for (WaterBody water : waterBodies) {
            i++;
            if (i >= particles.size()) {
                i = 0;
            }
            ParticleEffect particle = particles.get(i);
            water.waterBlocks.forEach(blockPos -> {
                Vec3d pos = blockPos.toCenterPos();
                this.world.addParticle(particle, pos.getX(), pos.getY() + 1, pos.getZ(), 0, 0.3, 0);
            });
        }
    }

    public void tickRandomWaterBody(ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int horizontalRadius = TidalConfig.horizontalWaveDistance;
        int verticalRadius = TidalConfig.verticalWaveDistance;
//        int horizontalRadius = 128;
//        int verticalRadius = 32;
        Random random = getRandom();

        int x = playerPos.getX() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        int y = playerPos.getY() + random.nextInt(verticalRadius) - random.nextInt(verticalRadius);
        int z = playerPos.getZ() + random.nextInt(horizontalRadius) - random.nextInt(horizontalRadius);
        pos.set(x, y, z);

        checkPosWaterBody(pos);
    }

    public void checkPosWaterBody(BlockPos pos) {
        //TODO - handle blocks that have changed from water to not water
        //TODO - increase check size for every block, and maybe increase water blocks even more to check for merging water bodies
        if(!posIsWater(world, pos)) return;
        if(posAlreadyInWaterBody(pos)) return;
        WaterBody waterBody = new WaterBody(pos);
        waterBody.addNeighbours(world, 1);

        if(tryMergeWaterBody(waterBody)) return;
        waterBodies.add(waterBody);
    }

    public boolean tryMergeWaterBody(WaterBody waterBody) {
        WaterBody checkedWater = waterBodyAlreadyAnother(waterBody);
        if(checkedWater == null) return false;
        checkedWater.mergeWaterBody(waterBody);
        waterBodies.remove(waterBody);
        return true;
    }

    @Nullable
    public WaterBody waterBodyAlreadyAnother(WaterBody waterBody) {
        //intellij magic oh my gosh
        return waterBodies.stream().filter(checkedWater -> waterBody.waterBlocks.stream().anyMatch(pos -> checkedWater.waterBlocks.contains(pos)))
                .findFirst()
                .orElse(null);
    }

    public boolean posAlreadyInWaterBody(BlockPos pos) {
        return waterBodies.stream().anyMatch(waterBody -> waterBody.waterBlocks.contains(pos));
    }

    public static boolean posIsWater(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.WATER);
    }

    public void clearWaterBodies() {
        waterBodies.clear();
    }

    public static boolean airAbove(World world, BlockPos pos) {
        BlockState aboveState = world.getBlockState(pos.offset(Direction.UP, 1));
        return aboveState.isAir();
    }

    public static boolean waterBelow(World world, BlockPos pos) {
        BlockState beneathState = world.getBlockState(pos.offset(Direction.DOWN, 1));
        return beneathState.isOf(Blocks.WATER);
    }
}

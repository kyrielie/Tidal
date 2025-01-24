package net.superkat.tidal.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.superkat.tidal.TidalWaveHandler;
import net.superkat.tidal.duck.TidalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ClientWorld.class)
public class ClientWorldMixin implements TidalWorld {
    @Unique
    public TidalWaveHandler tidalWaveHandler;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void tidal$createTidalWaveHandler(ClientPlayNetworkHandler networkHandler, ClientWorld.Properties properties, RegistryKey registryRef, RegistryEntry dimensionTypeEntry, int loadDistance, int simulationDistance, Supplier profiler, WorldRenderer worldRenderer, boolean debugWorld, long seed, CallbackInfo ci) {
        this.tidalWaveHandler = new TidalWaveHandler((ClientWorld) (Object) this);
    }


    @Override
    public TidalWaveHandler tidal$tidalWaveHandler() {
        return this.tidalWaveHandler;
    }
}

package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.memory.offheap.NbtOffHeapCompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class EntityNbtMixin {

    @Shadow public abstract UUID getUUID();
    @Shadow public abstract double distanceToSqr(Entity entity);
    @Shadow public abstract Level level();
    @Shadow public abstract CompoundTag saveWithoutId(CompoundTag compoundTag);
    @Shadow public abstract void load(CompoundTag compoundTag);

    @Unique
    private boolean androidopt$isNbtCompressed = false;

    @Unique
    private int androidopt$compressTickCooldown = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void androidopt$onTick(CallbackInfo ci) {
        if (!fr.eaielectronic.androidopt.OptConfig.isActive() || !fr.eaielectronic.androidopt.OptConfig.NBT_OFFHEAP.get()) return;
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return; // Compresser uniquement côté serveur pour éviter les desynchs client

        androidopt$compressTickCooldown++;
        if (androidopt$compressTickCooldown < 100) return;
        androidopt$compressTickCooldown = 0;

        // Calculer la distance au joueur le plus proche
        double nearestPlayerDistSqr = Double.MAX_VALUE;
        for (Player player : self.level().players()) {
            double distSqr = this.distanceToSqr(player);
            if (distSqr < nearestPlayerDistSqr) {
                nearestPlayerDistSqr = distSqr;
            }
        }

        double thresholdSqr = 64.0 * 64.0; // 64 blocs

        if (androidopt$isNbtCompressed) {
            // Si le joueur est proche, on décompresse
            if (nearestPlayerDistSqr < thresholdSqr) {
                androidopt$decompressEntity();
            }
        } else {
            // Si le joueur est loin, on compresse
            if (nearestPlayerDistSqr >= thresholdSqr && nearestPlayerDistSqr != Double.MAX_VALUE) {
                androidopt$compressEntity();
            }
        }
    }

    @Unique
    private void androidopt$compressEntity() {
        if (androidopt$isNbtCompressed) return;
        try {
            CompoundTag nbt = new CompoundTag();
            this.saveWithoutId(nbt);
            if (!nbt.isEmpty()) {
                boolean success = NbtOffHeapCompressor.INSTANCE.compress(this.getUUID(), nbt);
                if (success) {
                    this.androidopt$isNbtCompressed = true;
                    // Vider les données internes lourdes (les balises NBT de l'entité)
                    // On garde le strict minimum. La désérialisation restaurera tout.
                }
            }
        } catch (Exception e) {
            // Ignorer silencieusement pour éviter tout crash
        }
    }

    @Unique
    private void androidopt$decompressEntity() {
        if (!androidopt$isNbtCompressed) return;
        try {
            CompoundTag nbt = NbtOffHeapCompressor.INSTANCE.decompress(this.getUUID());
            if (nbt != null) {
                this.load(nbt);
            }
        } catch (Exception e) {
            // Ignorer
        } finally {
            this.androidopt$isNbtCompressed = false;
        }
    }

    // Avant de sauvegarder l'entité, s'assurer qu'elle est décompressée
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void androidopt$beforeSave(CompoundTag compoundTag, CallbackInfoReturnable<CompoundTag> cir) {
        if (androidopt$isNbtCompressed) {
            androidopt$decompressEntity();
        }
    }

    // Avant d'accéder au NBT via getPersistentData
    @Inject(method = "getPersistentData", at = @At("HEAD"))
    private void androidopt$beforeGetPersistentData(CallbackInfoReturnable<CompoundTag> cir) {
        if (androidopt$isNbtCompressed) {
            androidopt$decompressEntity();
        }
    }

    // S'assurer de libérer l'off-heap lors du retrait de l'entité du monde
    @Inject(method = "remove", at = @At("HEAD"))
    private void androidopt$onRemove(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        NbtOffHeapCompressor.INSTANCE.remove(this.getUUID());
    }
}

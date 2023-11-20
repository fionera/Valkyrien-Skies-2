package org.valkyrienskies.mod.mixin.client.multiplayer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.mixinducks.feature.fix_entity_rubberband.ClientboundMoveEntityPacketDuck;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Shadow
    private ClientLevel level;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = Shift.AFTER
        ),
        method = "handleLogin"
    )
    private void beforeHandleLogin(final ClientboundLoginPacket packet, final CallbackInfo ci) {
        ((IShipObjectWorldClientCreator) Minecraft.getInstance()).createShipObjectWorldClient();
    }

    /**
     * Spawn [ShipMountingEntity] on client side
     */
    @Inject(method = "handleAddEntity",
        at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"),
        cancellable = true)
    private void handleShipMountingEntity(final ClientboundAddEntityPacket packet, final CallbackInfo ci) {
        if (packet.getType().equals(ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE)) {
            ci.cancel();
            final double d = packet.getX();
            final double e = packet.getY();
            final double f = packet.getZ();
            final Entity entity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level);
            final int i = packet.getId();
            entity.setPacketCoordinates(d, e, f);
            entity.moveTo(d, e, f);
            entity.setXRot((float) (packet.getxRot() * 360) / 256.0f);
            entity.setYRot((float) (packet.getyRot() * 360) / 256.0f);
            entity.setId(i);
            entity.setUUID(packet.getUUID());
            this.level.putNonPlayerEntity(i, entity);
        }
    }

    /**
     * stop that sussy jitter of entities
     * @param packet
     * @param ci
     */
    @Inject(method = "handleMoveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;getEntity(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;", shift = Shift.AFTER))
    private void relerpEntity(final ClientboundMoveEntityPacket packet, final CallbackInfo ci) {
        if (((ClientboundMoveEntityPacketDuck) packet).valkyrienskies$getShipId() != null) {
            Ship ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(((ClientboundMoveEntityPacketDuck) packet).valkyrienskies$getShipId());
            Vector3d newPos = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(packet.getXa() / 4096.0, packet.getYa() / 4096.0, packet.getZa() / 4096.0));
            ((ClientboundMoveEntityPacketDuck) packet).valkyrienskies$setXa((short) (newPos.x * 4096));
            ((ClientboundMoveEntityPacketDuck) packet).valkyrienskies$setYa((short) (newPos.y * 4096));
            ((ClientboundMoveEntityPacketDuck) packet).valkyrienskies$setZa((short) (newPos.z * 4096));
        }
    }

    /**
     * When mc receives a tp packet it lerps it between 2 positions in 3 steps, this is bad for ships it gets stuck in a
     * unloaded chunk clientside and stays there until rejoining the server.
     */
    @WrapOperation(method = "handleTeleportEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"))
    private void teleportingWithNoStep(final Entity instance,
        final double x, final double y, final double z,
        final float yRot, final float xRot,
        final int lerpSteps, final boolean teleport, final Operation<Void> lerpTo) {
        if (VSGameUtilsKt.getShipObjectManagingPos(instance.level, instance.getX(), instance.getY(), instance.getZ()) !=
            null) {
            instance.setPos(x, y, z);
            lerpTo.call(instance, x, y, z, yRot, xRot, 1, teleport);
        } else {
            lerpTo.call(instance, x, y, z, yRot, xRot, lerpSteps, teleport);
        }
    }
}

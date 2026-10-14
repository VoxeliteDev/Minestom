package net.minestom.server.event.entity;

import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;

//  Voxelite--

/**
 * Called when a {@link LivingEntity} is about to execute a {@code hand-swing} animation.
 * See {@link LivingEntity#swingMainHand()} and {@link LivingEntity#swingOffHand()} and it's related functions.
 * <p></p>
 * If {@code cancelled} flag is {@code true}, the animation will not process.
 */
public class EntityHandAnimationEvent implements EntityEvent, CancellableEvent {

    private final LivingEntity livingEntity;
    private final boolean hand;                     // true = mainHand,     false = offHand

    private boolean cancelled;

    public EntityHandAnimationEvent(final LivingEntity livingEntity, final boolean hand) {
        this.livingEntity = livingEntity;
        this.hand = hand;
    }

    public EntityHandAnimationEvent(final LivingEntity livingEntity, final EntityAnimationPacket.Animation animationType) {
        this(livingEntity, animationType != EntityAnimationPacket.Animation.SWING_OFF_HAND);
    }

    public boolean isMainHand() {
        return this.hand;
    }

    public boolean isOffHand() {
        return !this.hand;
    }

    public EntityAnimationPacket.Animation handAnimationType() {
        return this.hand ? EntityAnimationPacket.Animation.SWING_MAIN_ARM : EntityAnimationPacket.Animation.SWING_OFF_HAND;
    }

    @Override
    public LivingEntity getEntity() {
        return this.livingEntity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}

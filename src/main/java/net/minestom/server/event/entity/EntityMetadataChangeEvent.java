package net.minestom.server.event.entity;

import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityEvent;

//  Voxelite--

/**
 * Called when a {@link Entity} is about to mutate a {@code metadata} related field.
 * See {@link net.minestom.server.entity.metadata.EntityMeta} and it's related functions.
 * <p></p>
 * If {@code cancelled} flag is {@code true}, the metadata will not mutate.
 *
 * @apiNote
 * <p>
 *      This event only signifies when metadata will mutate, and if canceled, will prevent
 *      the metadata from changing. However, this can lead to entity state de-sync and other
 *      issues.
 * </p>
 * <p>
 *      {@code Metadata} while being both server-side and client-side, is mainly used for state, status
 *      or animation tracking. However, the actual effect it has on player clients depends on per metadata field.
 *      For example, if an entities {@code sneaking} metadata bitmask flag is set to {@code true}, this won't
 *      actually make an entity sneak client-side. Instead, you need to mutate the {@code pose} metadata type.
 * </p>
 */
public class EntityMetadataChangeEvent implements EntityEvent, CancellableEvent {

    private final Entity entity;
    private final MetadataChangeType changeType;

    private boolean cancelled;

    public EntityMetadataChangeEvent(final Entity entity, final MetadataChangeType changeType) {
        this.entity = entity;
        this.changeType = changeType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    public MetadataChangeType getChangeType() {
        return changeType;
    }

    public enum MetadataChangeType {

        // part of the entity bitmask grouping (index 0)
        ON_FIRE_STATE,
        SNEAKING_STATE,
        SPRINTING_STATE,
        SWIMMING_STATE,
        INVISIBLE_STATE,
        GLOWING_STATE,
        ELYTRA_FLYING_STATE,

        // technically not part of the bitmask grouping but functionality is the same
        FROZEN_STATE;
    }
}

package net.minestom.server.event.entity;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityEvent;

//  Voxelite--

/**
 * Called when a {@link Entity} is about to mutate its {@code metadata} {@link EntityPose} related field.
 * See {@link net.minestom.server.entity.metadata.EntityMeta} and it's related functions.
 * <p></p>
 * If {@code cancelled} flag is {@code true}, the metadata will not mutate.
 *
 * @apiNote
 * <p>
 *      Unlike other similarly named {@code metadata} fields, the {@link EntityPose} field when mutated
 *      for an entity will direct affect how clients render the entity and its state. Remember, if you cancel
 *      this event from executing, this can lead to entity state de-sync and other issues.
 * </p>
 */
public class EntityPoseChangeEvent implements EntityEvent, CancellableEvent {

    private final Entity entity;
    private EntityPose pose;

    private boolean cancelled;

    public EntityPoseChangeEvent(final Entity entity, final EntityPose entityPose) {
        this.entity = entity;
        this.pose = entityPose;
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

    public EntityPose getPose() {
        return pose;
    }

    public void setPose(final EntityPose pose) {
        this.pose = pose;
    }
}

package net.minestom.server.event.entity.velocity;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//  Voxelite--

/**
 * Called when a velocity is applied to an entity using {@link Entity#setVelocity(Vec)}.
 * <p>Voxelite--</p>
 * <p>
 *      Cancellers are now based on a {@code List} of reasons. This allows you to provide
 *      reasoning as to why certain velocity events are being cancelled. The event is automatically
 *      cancelled if {@code > 1} canceller reason exists in the array.
 * </p>
 * <p>
 *      Another new field is the {@code Set} of {@link VelocityEventTag}. This allows you to provide
 *      tags that can store data or set flags as to why the velocity event is occurring.
 * </p>
 */
public class EntityVelocityEvent implements EntityInstanceEvent, CancellableEvent {

    private final Entity entity;
    private Vec velocity;

    private final Set<VelocityEventTag> tags;

    private final List<String> cancelledReasons;

    public EntityVelocityEvent(Entity entity, Vec velocity) {
        this.entity = entity;
        this.velocity = velocity;
        this.tags = new HashSet<>();
        this.cancelledReasons = new ArrayList<>();
    }

    public EntityVelocityEvent(Entity entity, Vec velocity, Set<VelocityEventTag> tags) {
        this(entity, velocity);
        this.addTags(tags);
    }

    public EntityVelocityEvent(Entity entity, Vec velocity, VelocityEventTag tag) {
        this(entity, velocity, Collections.singleton(tag));
    }

    /**
     * Gets the enity who will be affected by {@link #getVelocity()}.
     *
     * @return the entity
     */
    @Override
    public Entity getEntity() {
        return entity;
    }

    /**
     * Gets the velocity which will be applied.
     *
     * @return the velocity
     */
    public Vec getVelocity() {
        return velocity;
    }

    /**
     * Adds a {@link VelocityEventTag} to the event.
     *
     * @param tag the applied tag
     */
    public void addTag(final VelocityEventTag tag) {
        tags.add(tag);
    }

    /**
     * Removes a {@link VelocityEventTag} from the event.
     *
     * @param tag the removed tag
     */
    public void removeTag(final VelocityEventTag tag) {
        tags.remove(tag);
    }

    /**
     * Adds a set of {@link VelocityEventTag} to the event.
     *
     * @param tags the applied tags
     */
    public void addTags(final Set<VelocityEventTag> tags) {
        if (tags != null && !tags.isEmpty()) {
            this.tags.addAll(tags);
        }
    }

    /**
     * Removes a set of {@link VelocityEventTag} from the event.
     *
     * @param tags the removed tags
     */
    public void removeTags(final Set<VelocityEventTag> tags) {
        if (tags != null && !tags.isEmpty()) {
            this.tags.removeAll(tags);
        }
    }

    /**
     * Removes all current {@link VelocityEventTag} from the set.
     */
    public void clearTags() {
        tags.clear();
    }

    /**
     * Gets all {@link VelocityEventTag} stored for the event.
     *
     * @return {@code Set} of {@link VelocityEventTag} for the event
     */
    public Set<VelocityEventTag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    /**
     * @return {@code true} if the provided {@link VelocityEventTag} is within the stored tag set
     */
    public boolean hasTag(final VelocityEventTag tag) {
        return tags.contains(tag);
    }

    /**
     * Changes the applied velocity.
     *
     * @param velocity the new velocity
     */
    public void setVelocity(Vec velocity) {
        this.velocity = velocity;
    }

    @Override
    public boolean isCancelled() {
        return !cancelledReasons.isEmpty();
    }

    @Deprecated     // use setCancelled(String reason);
    @Override
    public void setCancelled(boolean cancel) {
        this.setCancelled("genericVelocityCancel");
    }

    public void setCancelled(final String reason) {
        cancelledReasons.add(reason);
    }

    public void clearCancellers() {
        cancelledReasons.clear();
    }
}

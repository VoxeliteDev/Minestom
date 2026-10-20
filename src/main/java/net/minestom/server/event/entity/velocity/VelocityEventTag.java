package net.minestom.server.event.entity.velocity;

/**
 * Represents a {@code tag} which can be stored within {@link EntityVelocityEvent} objects.
 */
public interface VelocityEventTag {

    enum DefaultVelocityTags implements VelocityEventTag {

        PLAYER_VELOCITY,
        ENTITY_VELOCITY,
        PROJECTILE_VELOCITY,

        DAMAGE_KNOCKBACK_INTERACTION,
        EFFECT_KNOCKBACK_INTERACTION;
    }
}

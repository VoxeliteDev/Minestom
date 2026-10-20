package net.minestom.server.event.player;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;

//  Voxelite--

/**
 * Called when a {@link Player} sends a {@code position} packet to the {@code server} (see {@link net.minestom.server.listener.PlayerPositionListener}).
 * This event indicates when the {@code player} has tried to trigger a singular {@code jump} by pressing space-bar.
 * <p></p>
 * If {@code cancelled} flag is {@code true}, the {@code player} jump will be prevented
 * from executing and the player teleport back to original position.
 *
 * @apiNote
 * <p>
 *      While this event fires and calculates during {@code PositionStatusPacket} and {@code PlayerPositionPacket} situations, there can be
 *      false-positives the same way paper does: during knockback, slime and bed bounces and bubble columns. The check is kinematic, not
 *      "the jump key was pressed on the ground". To check for that, you would just listen for the {@link PlayerInputEvent}.
 * </p>
 */
public class PlayerJumpEvent implements PlayerInstanceEvent, CancellableEvent {

    private final Player player;

    private final Pos from;
    private final Pos to;

    private boolean cancelled;

    public PlayerJumpEvent(final Player player, final Pos from, final Pos to) {
        this.player = player;
        this.from = from;
        this.to = to;
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
    public Player getPlayer() {
        return player;
    }

    public Pos getFrom() {
        return from;
    }

    public Pos getTo() {
        return to;
    }
}

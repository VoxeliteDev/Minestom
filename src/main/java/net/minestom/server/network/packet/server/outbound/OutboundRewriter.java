package net.minestom.server.network.packet.server.outbound;

import org.jetbrains.annotations.ApiStatus;

//  Voxelite--
// Extension point for replacing, dropping, or accompanying a packet before it is framed.

/**
 * Rewrites one outbound packet before it is framed.
 * <p>Voxelite--</p>
 * <p>
 * Rewriters are called on the thread that emitted the packet. That may be a tick
 * thread or a connection thread, and different emissions may run at the same time.
 * An implementation must tolerate concurrent {@link #rewrite(Emit)} calls and must
 * not keep the {@link Emit} after the call returns.
 * </p>
 * <p>
 * Leaving the emission untouched keeps the original packet. Play packets are records,
 * so a field change is a new packet staged in place of the old one. Rewriters run in
 * registration order, and each one sees the staged packets left by the rewriter before it.
 * </p>
 *
 * @see OutboundPackets#register(OutboundRewriter)
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface OutboundRewriter {

    /**
     * Inspects {@code emit} and optionally drops, replaces, or extends its staged packets.
     *
     * @param emit the emission about to be framed
     */
    void rewrite(Emit emit);
}

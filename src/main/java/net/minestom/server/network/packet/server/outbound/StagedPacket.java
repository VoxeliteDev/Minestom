package net.minestom.server.network.packet.server.outbound;

import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.ApiStatus;

//  Voxelite--
// A staged packet is one entry in an outbound rewrite, including who else should receive it.

/**
 * One packet produced by an {@link OutboundRewriter}, paired with its shared-audience flag.
 * <p>Voxelite--</p>
 * <p>
 * On a shared viewable emission, {@code sendToSource} decides whether the player who
 * produced the packet also receives it. {@code false} keeps the viewable exclusion:
 * that player is skipped. {@code true} writes the packet with no exclusion, so they
 * receive it in the same flush as everyone else.
 * </p>
 * <p>
 * A direct send already has one recipient. Every staged packet is delivered to that
 * recipient and {@code sendToSource} is ignored.
 * </p>
 *
 * @param packet       the packet to frame and send
 * @param sendToSource {@code true} if the source player of a shared emission also receives this packet
 */
@ApiStatus.Experimental
public record StagedPacket(ServerPacket packet, boolean sendToSource) { }

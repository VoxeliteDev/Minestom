package net.minestom.server.network.packet.server.outbound;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

//  Voxelite--
// Mutable emission handed to one rewriter chain. Not safe to retain or share across threads.

/**
 * One packet Minestom is about to frame, plus the staged packets that will be sent instead.
 * <p>Voxelite--</p>
 * <p>
 * The staged list starts as the original packet with {@code sendToSource == false}.
 * {@link #drop()} clears it. {@link #clear(StagedPacket...)} replaces it.
 * {@link #add(ServerPacket, boolean)} appends to it. {@link #map(UnaryOperator)}
 * rewrites each staged packet in order.
 * </p>
 * <p>
 * A shared emission is framed once and copied to every viewer. {@link #recipient()} is
 * null there. {@link #perRecipient()} abandons that shared frame and runs a fresh
 * emission for each viewer, with {@link #recipient()} set. Direct sends are already
 * per recipient.
 * </p>
 * <p>
 * An emit is owned by the pipeline for the duration of {@link OutboundRewriter#rewrite(Emit)}.
 * It is not safe to publish it to another thread.
 * </p>
 */
@ApiStatus.Experimental
public final class Emit {

    /** The single recipient, or null while the emission is shared by every viewer. */
    private final @Nullable Player recipient;

    /** The entity whose state produced the packet, or null for a direct send. */
    private final @Nullable Entity source;

    /** True when one framed result is reused for every viewer. */
    private final boolean shared;

    /** The packet Minestom created before any rewriter ran. */
    private final ServerPacket original;

    /** Packets that will be sent if no later rewriter replaces them. */
    private final List<StagedPacket> packets = new ArrayList<>(1);

    /** True after {@link #perRecipient()} on a shared emission. */
    private boolean split;

    /**
     * Creates an emission whose staged list starts as {@code original}, excluding the source player.
     *
     * @param recipient the player who will receive a direct emission, or null when {@code shared} is true
     * @param source    the entity that produced the packet, or null
     * @param shared    {@code true} when one framed result is reused for every viewer
     * @param original  the packet Minestom is about to frame
     */
    Emit(@Nullable Player recipient, @Nullable Entity source, boolean shared, ServerPacket original) {
        this.recipient = recipient;
        this.source = source;
        this.shared = shared;
        this.original = original;
        this.packets.add(new StagedPacket(original, false));
    }

    /**
     * Gets the player about to receive this emission.
     *
     * @return the recipient, or null when this emission is still shared by every viewer
     */
    public @Nullable Player recipient() {
        return recipient;
    }

    /**
     * Gets the entity whose state produced this packet.
     * <p>
     * Viewable movement, teleport, and head packets carry the entity that was synchronized.
     * A direct send that did not originate from {@link net.minestom.server.utils.PacketViewableUtils} has no source.
     * </p>
     *
     * @return the source entity, or null
     */
    public @Nullable Entity source() {
        return source;
    }

    /**
     * Gets whether one result will be framed once and reused for every viewer.
     *
     * @return {@code true} for a shared viewable emission
     */
    public boolean shared() {
        return shared;
    }

    /**
     * Gets the packet Minestom created, before any rewriter staged a replacement.
     *
     * @return the original packet
     */
    public ServerPacket original() {
        return original;
    }

    /**
     * Gets the packets currently staged for this emission.
     * <p>
     * The returned list reflects later {@link #drop()}, {@link #clear(StagedPacket...)},
     * {@link #add(ServerPacket, boolean)}, and {@link #map(UnaryOperator)} calls. It cannot
     * be modified directly.
     * </p>
     *
     * @return the staged packets, in send order
     */
    public @UnmodifiableView List<StagedPacket> packets() {
        return Collections.unmodifiableList(packets);
    }

    /**
     * Drops every staged packet, so nothing is sent for this emission.
     */
    public void drop() {
        packets.clear();
    }

    /**
     * Replaces the staged packets.
     * <p>
     * Passing no packets drops the emission, matching {@link #drop()}.
     * </p>
     *
     * @param staged the packets to send, in order
     */
    public void clear(StagedPacket... staged) {
        packets.clear();
        for (StagedPacket packet : staged) packets.add(Objects.requireNonNull(packet, "staged"));
    }

    /**
     * Appends a packet to the staged list.
     *
     * @param packet       the packet to send after the packets already staged
     * @param sendToSource {@code true} if the source player of a shared emission also receives {@code packet}
     * @throws NullPointerException if {@code packet} is null
     */
    public void add(ServerPacket packet, boolean sendToSource) {
        packets.add(new StagedPacket(Objects.requireNonNull(packet, "packet"), sendToSource));
    }

    /**
     * Replaces each staged packet by applying {@code fn}.
     * <p>
     * Return the same instance to leave that entry, including its {@code sendToSource} flag, unchanged.
     * Bundle delimiters and packets for other entities should be returned as-is.
     * </p>
     *
     * @param fn the mapping function
     * @throws NullPointerException if {@code fn} or any mapped packet is null
     */
    public void map(UnaryOperator<ServerPacket> fn) {
        Objects.requireNonNull(fn, "fn");
        for (int i = 0; i < packets.size(); i++) {
            final StagedPacket staged = packets.get(i);
            final ServerPacket mapped = Objects.requireNonNull(fn.apply(staged.packet()), "mapped packet");
            if (mapped != staged.packet()) {
                packets.set(i, new StagedPacket(mapped, staged.sendToSource()));
            }
        }
    }

    /**
     * Asks the shared entrance to abandon this frame and run the rewriter chain once per viewer.
     * <p>
     * The staged list on this shared emission is discarded. Each viewer receives a new
     * {@link Emit} with {@link #shared()} {@code false} and {@link #recipient()} set.
     * Calling this on a direct emission does nothing, because that emission is already
     * addressed to one player.
     * </p>
     */
    public void perRecipient() {
        if (shared) this.split = true;
    }

    boolean splitRequested() {
        return split;
    }

    List<StagedPacket> stagedPackets() {
        return packets;
    }
}

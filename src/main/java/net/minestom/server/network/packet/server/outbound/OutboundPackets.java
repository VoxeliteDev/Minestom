package net.minestom.server.network.packet.server.outbound;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.ServerPacket;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

//  Voxelite--
// Pre-frame outbound pipeline. Empty registrations leave the existing send path unchanged.

/**
 * Ordered chain of {@link OutboundRewriter}s invoked before an outbound packet is framed.
 * <p>Voxelite--</p>
 * <p>
 * Two entrances share this chain. {@link #rewriteDirect(Player, Entity, ServerPacket)} runs
 * for a packet that already has one recipient. {@link #rewriteShared(Entity, ServerPacket)}
 * runs for a viewable packet that would otherwise be framed once and copied to every viewer.
 * Both run before framing, so a rewriter can replace fields, drop the packet, or stage
 * more packets beside it.
 * </p>
 * <p>
 * With no rewriters registered, {@link #active()} is false and callers keep the current
 * send path. Registration is copy-on-write, so a send observes a stable chain.
 * </p>
 */
@ApiStatus.Experimental
public final class OutboundPackets {

    /**
     * Identity marker returned by {@link #rewriteShared(Entity, ServerPacket)} when a rewriter
     * called {@link Emit#perRecipient()}.
     * <p>
     * Compare with {@code ==}. The list cannot be read or sent. The shared entrance discards
     * it and runs {@link #rewriteDirect(Player, Entity, ServerPacket)} once per viewer.
     * </p>
     */
    public static final List<StagedPacket> SPLIT = new AbstractList<>() {

        @Override
        public StagedPacket get(int index) {
            throw new UnsupportedOperationException("OutboundPackets.SPLIT is a marker and cannot be read");
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException("OutboundPackets.SPLIT is a marker and cannot be read");
        }
    };

    /** Copy-on-write chain. An empty array means {@link #active()} is false. */
    private static final AtomicReference<OutboundRewriter[]> REWRITERS =
            new AtomicReference<>(new OutboundRewriter[0]);

    /** Entity ids whose packets must be built fresh so a rewriter can replace them. */
    private static final Set<Integer> UNCACHED_ENTITIES =
            ConcurrentHashMap.newKeySet();

    private OutboundPackets() {
    }

    /**
     * Gets whether at least one rewriter is registered.
     *
     * @return {@code true} when outbound packets should be passed through the chain
     */
    public static boolean active() {
        return REWRITERS.get().length != 0;
    }

    /**
     * Marks {@code entityId} so its packets are built fresh instead of taken from a cache.
     * <p>
     * A cached packet is framed before rewriters run. Call this when a rewriter starts
     * replacing that entity's packets.
     * </p>
     *
     * @param entityId the entity whose packets must bypass the cache
     */
    public static void markUncached(int entityId) {
        UNCACHED_ENTITIES.add(entityId);
    }

    /**
     * Removes {@code entityId} from the uncached set.
     * <p>
     * Later packets for this entity may use a cached packet again.
     * </p>
     *
     * @param entityId the entity that no longer needs fresh packets
     */
    public static void unmarkUncached(int entityId) {
        UNCACHED_ENTITIES.remove(entityId);
    }

    /**
     * Gets whether {@code entityId} must send fresh packets.
     *
     * @param entityId the entity to check
     * @return {@code true} when a rewriter is replacing this entity's packets
     */
    public static boolean isUncached(int entityId) {
        return UNCACHED_ENTITIES.contains(entityId);
    }

    /**
     * Appends {@code rewriter} to the chain.
     * <p>
     * The same instance may be registered more than once. Each {@link Registration}
     * removes one occurrence.
     * </p>
     *
     * @param rewriter the rewriter to call, after the rewriters already registered
     * @return a handle that removes this registration
     * @throws NullPointerException if {@code rewriter} is null
     */
    public static Registration register(OutboundRewriter rewriter) {
        Objects.requireNonNull(rewriter, "rewriter");
        REWRITERS.updateAndGet(current -> {
            final OutboundRewriter[] next = Arrays.copyOf(current, current.length + 1);
            next[current.length] = rewriter;
            return next;
        });
        return new Registration(rewriter);
    }

    /**
     * Runs the chain for a packet that will be framed once for every viewer.
     * <p>
     * A {@code null} result means every rewriter left the original packet staged alone,
     * still excluding the source player. An empty list means send nothing.
     * {@link #SPLIT} means the shared frame was refused and the packet must be rewritten
     * again per recipient.
     * </p>
     *
     * @param source the entity that produced the packet, or null
     * @param packet the packet about to be framed
     * @return {@code null} when unchanged, {@link #SPLIT} when a per-recipient rewrite is required,
     * or the staged packets to frame
     */
    @ApiStatus.Internal
    public static @Nullable List<StagedPacket> rewriteShared(@Nullable Entity source, ServerPacket packet) {
        return run(null, source, true, packet);
    }

    /**
     * Runs the chain for a packet addressed to one player.
     * <p>
     * {@code source} is set when this call is the per-recipient follow-up of a viewable
     * packet. Direct sends that did not come from that follow-up pass null.
     * </p>
     *
     * @param recipient the player who will receive the staged packets
     * @param source    the entity that produced a viewable packet, or null
     * @param packet    the packet about to be framed
     * @return {@code null} when unchanged, or the staged packets to send to {@code recipient}
     * @throws NullPointerException if {@code recipient} or {@code packet} is null
     */
    @ApiStatus.Internal
    public static @Nullable List<StagedPacket> rewriteDirect(Player recipient, @Nullable Entity source, ServerPacket packet) {
        Objects.requireNonNull(recipient, "recipient");
        return run(recipient, source, false, packet);
    }

    /**
     * Runs the registered rewriters against a new {@link Emit} and returns what should be sent.
     *
     * @param recipient the direct recipient, or null when {@code shared} is true
     * @param source    the entity that produced the packet, or null
     * @param shared    {@code true} when one framed result is reused for every viewer
     * @param packet    the packet Minestom is about to frame
     * @return {@code null} when unchanged, {@link #SPLIT} when a shared emission was split,
     * or the staged packets
     */
    private static @Nullable List<StagedPacket> run(
            @Nullable Player recipient, @Nullable Entity source, boolean shared, ServerPacket packet) {
        Objects.requireNonNull(packet, "packet");
        final OutboundRewriter[] rewriters = REWRITERS.get();
        if (rewriters.length == 0) return null;
        final Emit emit = new Emit(recipient, source, shared, packet);
        for (OutboundRewriter rewriter : rewriters) rewriter.rewrite(emit);
        if (emit.splitRequested()) return SPLIT;
        final List<StagedPacket> staged = emit.stagedPackets();
        if (staged.size() == 1) {
            final StagedPacket only = staged.getFirst();
            if (only.packet() == packet && !only.sendToSource()) return null;
        }
        return List.copyOf(staged);
    }

    /**
     * Removes one {@link OutboundPackets#register(OutboundRewriter)} call.
     * <p>Voxelite--</p>
     */
    public static final class Registration {

        /** The rewriter this handle removes. Identity, not equality, decides the match. */
        private final OutboundRewriter rewriter;

        /** Ensures {@link #unregister()} removes the rewriter only once. */
        private final AtomicBoolean removed = new AtomicBoolean();

        /**
         * @param rewriter the registration to remove
         */
        private Registration(OutboundRewriter rewriter) {
            this.rewriter = rewriter;
        }

        /**
         * Removes this registration.
         * <p>
         * A second call does nothing. Removal does not affect a chain a send has already copied.
         * </p>
         */
        public void unregister() {
            if (!removed.compareAndSet(false, true)) return;
            REWRITERS.updateAndGet(current -> {
                int index = -1;
                for (int i = 0; i < current.length; i++) {
                    if (current[i] == rewriter) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) return current;
                final OutboundRewriter[] next = new OutboundRewriter[current.length - 1];
                System.arraycopy(current, 0, next, 0, index);
                System.arraycopy(current, index + 1, next, index, current.length - index - 1);
                return next;
            });
        }

        public OutboundRewriter getOutboundRewriter() throws NullPointerException {
            if (this.rewriter == null) {
                throw new NullPointerException("OutboundRewriter object is not loaded via Registrar");
            }

            return this.rewriter;
        }
    }
}

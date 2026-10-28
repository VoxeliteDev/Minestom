package net.minestom.server.utils;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.ComponentHolder;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.outbound.OutboundPackets;
import net.minestom.server.network.packet.server.outbound.StagedPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.property.ServerProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class PacketSendingUtils {
    /**
     * Sends a packet to an audience. This method performs the following steps in the
     * following order:
     * <ol>
     *     <li>If {@code audience} is a {@link Player}, send the packet to them.</li>
     *     <li>Otherwise, if {@code audience} is a {@link PacketGroupingAudience}, call
     *     {@link #sendGroupedPacket(Collection, ServerPacket)} on the players that the
     *     grouping audience contains.</li>
     *     <li>Otherwise, if {@code audience} is a {@link ForwardingAudience.Single},
     *     call this method on the single audience inside the forwarding audience.</li>
     *     <li>Otherwise, if {@code audience} is a {@link ForwardingAudience}, call this
     *     method for each audience member of the forwarding audience.</li>
     *     <li>Otherwise, do nothing.</li>
     * </ol>
     *
     * @param audience the audience
     * @param packet   the packet
     */
    @SuppressWarnings("OverrideOnly") // we need to access the audiences inside ForwardingAudience
    public static void sendPacket(Audience audience, ServerPacket packet) {
        switch (audience) {
            case Player player -> player.sendPacket(packet);
            case PacketGroupingAudience groupingAudience -> sendGroupedPacket(groupingAudience.getPlayers(), packet);
            case ForwardingAudience.Single singleAudience -> sendPacket(singleAudience.audience(), packet);
            case ForwardingAudience forwardingAudience -> {
                for (Audience member : forwardingAudience.audiences()) {
                    sendPacket(member, packet);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Sends a {@link ServerPacket} to multiple players.
     * <p>
     * Can drastically improve performance since the packet will not have to be processed as much.
     * <p>Voxelite--</p>
     * <p>
     * While {@link OutboundPackets} is active, each recipient gets a direct rewrite and the
     * packet is not wrapped in a {@link CachedPacket}. A cached body would be the packet from
     * before the rewrite.
     * </p>
     *
     * @param players   the players to send the packet to
     * @param packet    the packet to send to the players
     * @param predicate predicate to ignore specific players
     */
    public static <T extends Player> void sendGroupedPacket(Collection<T> players, ServerPacket packet,
                                         Predicate<? super T> predicate) {
        //  Voxelite--
        // Per-player rewrite cannot share one framed body, so skip the cache while a rewriter is registered.
        if (OutboundPackets.active()) {
            for (T player : players) {
                if (predicate.test(player)) deliverDirect(player, packet);
            }
            return;
        }
        final SendablePacket sendablePacket = groupedPacket(packet);
        for (T player : players) {
            if (predicate.test(player)) player.getPlayerConnection().sendRawPacket(sendablePacket);
        }
    }

    /**
     * Same as {@link #sendGroupedPacket(Collection, ServerPacket, Predicate)}
     * but without any predicate.
     *
     * @see #sendGroupedPacket(Collection, ServerPacket, Predicate)
     */
    public static void sendGroupedPacket(Collection<? extends Player> players, ServerPacket packet) {
        sendGroupedPacketUnrewritten(players, packet, _ -> true, true);
    }

    /**
     * Sends a shared viewable rewrite to {@code viewers}, without running the chain again.
     * <p>Voxelite--</p>
     * <p>
     * Viewers other than {@code source} receive every staged packet. When {@code source} is a
     * player, that player receives only packets staged with {@code sendToSource}. If they are
     * not already in {@code viewers}, those packets are sent to them as well.
     * </p>
     *
     * @param viewers the viewers the unrewritten packet would have reached, possibly including {@code source}
     * @param staged  the staged packets from {@link OutboundPackets#rewriteShared(Entity, ServerPacket)}
     * @param source  the entity that produced the packet, or null
     */
    @ApiStatus.Internal
    public static void sendStagedViewable(Collection<? extends Player> viewers, List<StagedPacket> staged,
                                          @Nullable Entity source) {
        final Player sourcePlayer = source instanceof Player player ? player : null;
        boolean sourceVisited = false;
        for (Player viewer : viewers) {
            if (viewer == sourcePlayer) sourceVisited = true;
            deliverStaged(viewer, staged, viewer == sourcePlayer);
        }
        if (sourcePlayer != null && !sourceVisited) deliverStaged(sourcePlayer, staged, true);
    }

    /**
     * Runs a per-recipient rewrite of a viewable packet.
     * <p>Voxelite--</p>
     * <p>
     * Used after {@link OutboundPackets#SPLIT}. {@code source}, when it is a player, is rewritten
     * too, and then only receives packets staged with {@code sendToSource}. An unchanged rewrite
     * still skips {@code source}, matching the viewable exclusion.
     * </p>
     *
     * @param viewers the viewers of the shared emission, possibly including {@code source}
     * @param packet  the original packet
     * @param source  the entity that produced the packet, or null
     */
    @ApiStatus.Internal
    public static void sendSplitViewable(Collection<? extends Player> viewers, ServerPacket packet,
                                         @Nullable Entity source) {
        final Player sourcePlayer = source instanceof Player player ? player : null;
        boolean sourceVisited = false;
        for (Player viewer : viewers) {
            if (viewer == sourcePlayer) sourceVisited = true;
            deliverSplit(viewer, packet, source, viewer == sourcePlayer);
        }
        if (sourcePlayer != null && !sourceVisited) deliverSplit(sourcePlayer, packet, source, true);
    }

    /**
     * Sends {@code packet} to {@code players} with the grouped cache and without a rewrite.
     * <p>Voxelite--</p>
     * <p>
     * The shared viewable entrance uses this when a rewrite left the packet unchanged, so the
     * public grouped send does not run the chain a second time.
     * </p>
     *
     * @param players   the players to send the packet to
     * @param packet    the packet to send
     * @param predicate predicate to ignore specific players
     */
    static void sendGroupedPacketUnrewritten(Collection<? extends Player> players, ServerPacket packet,
                                            Predicate<? super Player> predicate) {
        sendGroupedPacketUnrewritten(players, packet, predicate, false);
    }

    /**
     * Sends {@code packet} to the players accepted by {@code predicate}.
     * <p>Voxelite--</p>
     *
     * @param players   the players to consider
     * @param packet    the packet to send
     * @param predicate the players who should receive {@code packet}
     * @param rewrite   {@code true} to run a direct rewrite while {@link OutboundPackets} is active
     */
    private static void sendGroupedPacketUnrewritten(Collection<? extends Player> players, ServerPacket packet,
                                                    Predicate<? super Player> predicate, boolean rewrite) {
        //  Voxelite--
        // rewrite is false when the shared entrance already ran the chain and the packet was unchanged.
        if (rewrite && OutboundPackets.active()) {
            for (Player player : players) {
                if (predicate.test(player)) deliverDirect(player, packet);
            }
            return;
        }
        final SendablePacket sendablePacket = groupedPacket(packet);
        for (Player player : players) {
            if (predicate.test(player)) player.getPlayerConnection().sendRawPacket(sendablePacket);
        }
    }

    public static void broadcastPlayPacket(ServerPacket packet) {
        sendGroupedPacket(MinecraftServer.getConnectionManager().getOnlinePlayers(), packet);
    }

    /**
     * Rewrites {@code packet} for {@code player} and queues the staged result.
     * <p>Voxelite--</p>
     *
     * @param player the recipient
     * @param packet the packet to rewrite
     */
    private static void deliverDirect(Player player, ServerPacket packet) {
        final List<StagedPacket> staged = OutboundPackets.rewriteDirect(player, null, packet);
        final PlayerConnection connection = player.getPlayerConnection();
        if (staged == null || staged == OutboundPackets.SPLIT) {
            connection.sendRawPacket(packet);
            return;
        }
        for (StagedPacket out : staged) connection.sendRawPacket(out.packet());
    }

    /**
     * Queues staged packets that {@code player} is allowed to receive.
     * <p>Voxelite--</p>
     *
     * @param player            the recipient
     * @param staged            the packets already rewritten
     * @param recipientIsSource {@code true} when {@code player} produced the emission and only
     *                          {@code sendToSource} packets apply
     */
    private static void deliverStaged(Player player, List<StagedPacket> staged, boolean recipientIsSource) {
        final PlayerConnection connection = player.getPlayerConnection();
        for (StagedPacket out : staged) {
            if (recipientIsSource && !out.sendToSource()) continue;
            connection.sendRawPacket(out.packet());
        }
    }

    /**
     * Rewrites a split viewable packet for one recipient and queues what that recipient should see.
     * <p>Voxelite--</p>
     *
     * @param recipient         the player receiving this emission
     * @param packet            the original packet
     * @param source            the entity that produced {@code packet}, or null
     * @param recipientIsSource {@code true} when {@code recipient} is {@code source}
     */
    private static void deliverSplit(Player recipient, ServerPacket packet, @Nullable Entity source, boolean recipientIsSource) {
        final List<StagedPacket> staged = OutboundPackets.rewriteDirect(recipient, source, packet);
        final PlayerConnection connection = recipient.getPlayerConnection();
        if (staged == null || staged == OutboundPackets.SPLIT) {
            if (!recipientIsSource) connection.sendRawPacket(packet);
            return;
        }
        deliverStaged(recipient, staged, recipientIsSource);
    }

    private static SendablePacket groupedPacket(ServerPacket packet) {
        return ServerProperties.GROUPED_PACKET.get() && shouldUseCachePacket(packet) ? new CachedPacket(packet) : packet;
    }

    /**
     * Checks if the {@link ServerPacket} is suitable to be wrapped into a {@link CachedPacket}.
     * Note: {@link ServerPacket.ComponentHolding}s are not translated inside a {@link CachedPacket}.
     *
     * @see CachedPacket#body(ConnectionState)
     */
    private static boolean shouldUseCachePacket(final ServerPacket packet) {
        if (!ServerProperties.AUTOMATIC_COMPONENT_TRANSLATION.get()) return true;
        if (!(packet instanceof ServerPacket.ComponentHolding holder)) return true;
        return !containsTranslatableComponents(holder);
    }

    private static boolean containsTranslatableComponents(final ComponentHolder<?> holder) {
        for (final Component component : holder.components()) {
            if (isTranslatable(component)) return true;
        }
        return false;
    }

    private static boolean isTranslatable(final Component component) {
        if (component instanceof TranslatableComponent) return true;
        final HoverEvent<?> hoverEvent = component.hoverEvent();
        if (hoverEvent != null && hoverEvent.value() instanceof Component hoverComponent && isTranslatable(hoverComponent))
            return true;
        final List<Component> children = component.children();
        if (children.isEmpty()) return false;
        for (final Component child : children) {
            if (isTranslatable(child)) return true;
        }
        return false;
    }
}

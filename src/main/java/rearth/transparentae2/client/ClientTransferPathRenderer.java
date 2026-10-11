package rearth.transparentae2.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import appeng.api.stacks.AEItemKey;
import rearth.transparentae2.TransparentAE2;
import rearth.transparentae2.Config;
import rearth.transparentae2.network.TransferPathPayload;

public final class ClientTransferPathRenderer {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 128.0 * 128.0;
    private static final float ITEM_SCALE_PER_CABLE_UNIT = 0.2F;
    private static final float NON_BLOCK_ITEM_SCALE = 0.8F;
    private static final float GROUND_MODEL_Y_OFFSET = 3.0F / 16.0F;
    private static final ContextKey<List<MovingItemRenderState>> ITEM_RENDER_STATES = new ContextKey<>(
            Identifier.fromNamespaceAndPath(TransparentAE2.MODID, "moving_transfer_items"));
    private static final Deque<MovingTransfer> TRANSFERS = new ArrayDeque<>();

    private ClientTransferPathRenderer() {
    }

    public static void add(TransferPathPayload payload) {
        if (!ClientConfig.RENDER_TRANSFER_ITEMS.getAsBoolean() || !Config.ENABLE_TRANSFER_PATHS.getAsBoolean()) {
            TRANSFERS.clear();
            return;
        }

        var maxTransfers = ClientConfig.MAX_TRANSFERS.getAsInt();
        while (TRANSFERS.size() >= maxTransfers) {
            TRANSFERS.removeFirst();
        }
        TRANSFERS.addLast(MovingTransfer.create(payload, Util.getMillis()));
    }

    public static void clear() {
        TRANSFERS.clear();
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (!ClientConfig.RENDER_TRANSFER_ITEMS.getAsBoolean() || !Config.ENABLE_TRANSFER_PATHS.getAsBoolean()) {
            TRANSFERS.clear();
            return;
        }

        trimToConfiguredLimit();
        if (TRANSFERS.isEmpty()) {
            return;
        }

        var now = Util.getMillis();
        TRANSFERS.removeIf(transfer -> transfer.retentionEndsAtMillis <= now);
        var renderStates = new ArrayList<MovingItemRenderState>();
        var modelResolver = Minecraft.getInstance().getItemModelResolver();
        var camera = event.getRenderState().cameraRenderState.pos;
        var itemStates = new HashMap<AEItemKey, ItemStackRenderState>();

        for (var transfer : TRANSFERS) {
            var position = transfer.positionAt(now);
            if (position == null
                    || !position.dimension.equals(event.getLevel().dimension())
                    || position.position.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            var itemState = itemStates.computeIfAbsent(transfer.item, ignored -> {
                var state = new ItemStackRenderState();
                modelResolver.updateForTopItem(
                        state,
                        transfer.item.getReadOnlyStack(),
                        ItemDisplayContext.GROUND,
                        event.getLevel(),
                        null,
                        transfer.seed);
                return state;
            });
            var light = transfer.lightAt(event, BlockPos.containing(position.position));
            var rotation = (now - transfer.startedAtMillis) * 0.002F;
            renderStates.add(new MovingItemRenderState(
                    position.position,
                    itemState,
                    light,
                    rotation,
                    transfer.itemScale));
        }

        if (!renderStates.isEmpty()) {
            event.getRenderState().setRenderData(ITEM_RENDER_STATES, List.copyOf(renderStates));
        }
    }

    public static void submitItems(SubmitCustomGeometryEvent event) {
        if (!ClientConfig.RENDER_TRANSFER_ITEMS.getAsBoolean() || !Config.ENABLE_TRANSFER_PATHS.getAsBoolean()) {
            return;
        }

        List<MovingItemRenderState> renderStates = event.getLevelRenderState().getRenderData(ITEM_RENDER_STATES);
        if (renderStates == null) {
            return;
        }

        var poseStack = event.getPoseStack();
        var camera = event.getLevelRenderState().cameraRenderState.pos;
        for (var renderState : renderStates) {
            var position = renderState.position.subtract(camera);
            poseStack.pushPose();
            poseStack.translate(position.x, position.y, position.z);
            poseStack.mulPose(Axis.YP.rotation(renderState.rotation));
            poseStack.scale(renderState.scale, renderState.scale, renderState.scale);
            poseStack.translate(0, -GROUND_MODEL_Y_OFFSET, 0);
            renderState.item.submit(
                    poseStack,
                    event.getSubmitNodeCollector(),
                    renderState.light,
                    OverlayTexture.NO_OVERLAY,
                    0);
            poseStack.popPose();
        }
    }

    private static void trimToConfiguredLimit() {
        var maxTransfers = ClientConfig.MAX_TRANSFERS.getAsInt();
        while (TRANSFERS.size() > maxTransfers) {
            TRANSFERS.removeFirst();
        }
    }

    private record MovingItemRenderState(
            Vec3 position,
            ItemStackRenderState item,
            int light,
            float rotation,
            float scale) {
    }

    private record PositionedItem(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            Vec3 position) {
    }

    private static final class MovingTransfer {
        private final long startedAtMillis;
        private final long travelEndsAtMillis;
        private final long retentionEndsAtMillis;
        private final double routeLength;
        private final double itemsPerSecond;
        private final float itemScale;
        private final int seed;
        private final AEItemKey item;
        private final Segment[] segments;
        private final PositionedItem stationaryPosition;
        private BlockPos lightPosition;
        private int light;

        private MovingTransfer(
                TransferPathPayload payload,
                long startedAtMillis,
                long travelEndsAtMillis,
                double routeLength,
                double itemsPerSecond,
                Segment[] segments,
                PositionedItem stationaryPosition) {
            this.startedAtMillis = startedAtMillis;
            this.travelEndsAtMillis = travelEndsAtMillis;
            this.retentionEndsAtMillis = travelEndsAtMillis;
            this.routeLength = routeLength;
            this.itemsPerSecond = itemsPerSecond;
            this.segments = segments;
            this.stationaryPosition = stationaryPosition;
            var itemTypeScale = payload.stack().getItem() instanceof BlockItem ? 1.0F : NON_BLOCK_ITEM_SCALE;
            this.itemScale = payload.minimumCableWidth() * ITEM_SCALE_PER_CABLE_UNIT * itemTypeScale;
            this.seed = payload.stack().hashCode();
            this.item = Objects.requireNonNull(AEItemKey.of(payload.stack()));
        }

        static MovingTransfer create(TransferPathPayload payload, long now) {
            var segments = createSegments(payload.legs());
            var routeLength = segments.length == 0 ? 0 : segments[segments.length - 1].endsAtDistance;
            var itemsPerSecond = ClientConfig.ITEM_MOVEMENT_SPEED.getAsDouble();
            var travelMillis = Math.max(250L, Math.round(routeLength / itemsPerSecond * 1_000.0));
            return new MovingTransfer(
                    payload,
                    now,
                    now + travelMillis,
                    routeLength,
                    itemsPerSecond,
                    segments,
                    firstPosition(payload.legs()));
        }

        PositionedItem positionAt(long now) {
            if (now >= travelEndsAtMillis) {
                return null;
            }

            if (routeLength <= 1.0E-6) {
                return stationaryPosition;
            }

            var elapsed = Math.max(0L, now - startedAtMillis);
            var distance = Math.min(routeLength, elapsed / 1_000.0 * itemsPerSecond);
            var segment = findSegment(distance);
            var segmentStart = segment.endsAtDistance - segment.length;
            var progress = segment.length <= 1.0E-6 ? 1.0 : (distance - segmentStart) / segment.length;
            return new PositionedItem(segment.dimension, segment.from.lerp(segment.to, progress));
        }

        private Segment findSegment(double distance) {
            var low = 0;
            var high = segments.length - 1;
            while (low < high) {
                var middle = (low + high) >>> 1;
                if (distance <= segments[middle].endsAtDistance) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return segments[low];
        }

        int lightAt(ExtractLevelRenderStateEvent event, BlockPos position) {
            if (!position.equals(lightPosition)) {
                lightPosition = position;
                light = LevelRenderer.getLightCoords(event.getLevel(), position);
            }
            return light;
        }

        private static Segment[] createSegments(List<List<GlobalPos>> legs) {
            var result = new ArrayList<Segment>();
            double distance = 0;
            for (var leg : legs) {
                for (int pointIndex = 1; pointIndex < leg.size(); pointIndex++) {
                    var from = leg.get(pointIndex - 1);
                    var to = leg.get(pointIndex);
                    if (from.dimension().equals(to.dimension())) {
                        var fromPosition = itemCenter(from);
                        var toPosition = itemCenter(to);
                        var length = fromPosition.distanceTo(toPosition);
                        if (length > 1.0E-6) {
                            distance += length;
                            result.add(new Segment(
                                    from.dimension(),
                                    fromPosition,
                                    toPosition,
                                    length,
                                    distance));
                        }
                    }
                }
            }
            return result.toArray(Segment[]::new);
        }

        private static PositionedItem firstPosition(List<List<GlobalPos>> legs) {
            for (var leg : legs) {
                if (!leg.isEmpty()) {
                    var point = leg.getFirst();
                    return new PositionedItem(point.dimension(), itemCenter(point));
                }
            }
            return null;
        }

        private static Vec3 itemCenter(GlobalPos point) {
            return Vec3.atCenterOf(point.pos());
        }
    }

    private record Segment(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            Vec3 from,
            Vec3 to,
            double length,
            double endsAtDistance) {
    }
}

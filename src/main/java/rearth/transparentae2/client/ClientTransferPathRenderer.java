package rearth.transparentae2.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.mojang.math.Axis;
import org.joml.Quaternionf;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import appeng.api.stacks.AEItemKey;
import rearth.transparentae2.Config;
import rearth.transparentae2.network.TransferPathPayload;

public final class ClientTransferPathRenderer {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 128.0 * 128.0;
    private static final double MAX_MOVEMENT_SPEED = 100.0;
    private static final double NEAR_CAMERA_DISTANCE = 8.0;
    private static final double FAR_CAMERA_DISTANCE = 64.0;
    private static final long MAX_TRAVEL_MILLIS = 3_000L;
    private static final float ITEM_SCALE_PER_CABLE_UNIT = 0.195F;
    private static final float NON_BLOCK_ITEM_SCALE = 0.6F;
    private static final float GROUND_MODEL_Y_OFFSET = 3.0F / 16.0F;
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
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        TRANSFERS.addLast(MovingTransfer.create(payload, Util.getMillis(),
                minecraft.gameRenderer.getMainCamera().getPosition(), minecraft.level.dimension()));
    }

    public static void clear() {
        TRANSFERS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (!ClientConfig.RENDER_TRANSFER_ITEMS.getAsBoolean() || !Config.ENABLE_TRANSFER_PATHS.getAsBoolean()) {
            TRANSFERS.clear();
            return;
        }

        trimToConfiguredLimit();
        if (TRANSFERS.isEmpty()) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            return;
        }

        var now = Util.getMillis();
        TRANSFERS.removeIf(transfer -> transfer.retentionEndsAtMillis <= now);
        var mainCamera = minecraft.gameRenderer.getMainCamera();
        var camera = mainCamera.getPosition();
        var poseStack = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var itemRenderer = minecraft.getItemRenderer();

        // RenderLevelStageEvent's pose stack already contains the camera rotation. Undo it before
        // applying world coordinates; the active model-view matrix applies the view transform.
        poseStack.pushPose();
        var cameraRotation = new Quaternionf(mainCamera.rotation()).invert();
        poseStack.mulPose(cameraRotation);
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (var transfer : TRANSFERS) {
            var positionedItem = transfer.positionAt(now);
            if (positionedItem == null
                    || !positionedItem.dimension.equals(level.dimension())
                    || positionedItem.position.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                    positionedItem.position.x,
                    positionedItem.position.y,
                    positionedItem.position.z);
            poseStack.mulPose(Axis.YP.rotation((now - transfer.startedAtMillis) * 0.002F));
            poseStack.scale(transfer.itemScale, transfer.itemScale, transfer.itemScale);
            poseStack.translate(0, -GROUND_MODEL_Y_OFFSET, 0);
            itemRenderer.renderStatic(
                    transfer.item.getReadOnlyStack(),
                    ItemDisplayContext.GROUND,
                    transfer.lightAt(level, BlockPos.containing(positionedItem.position)),
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffers,
                    level,
                    transfer.seed);
            poseStack.popPose();
        }
        poseStack.popPose();
        buffers.endBatch();
    }

    private static void trimToConfiguredLimit() {
        var maxTransfers = ClientConfig.MAX_TRANSFERS.getAsInt();
        while (TRANSFERS.size() > maxTransfers) {
            TRANSFERS.removeFirst();
        }
    }

    private record PositionedItem(ResourceKey<Level> dimension, Vec3 position) {
    }

    private static final class MovingTransfer {
        private final long startedAtMillis;
        private final long travelEndsAtMillis;
        private final long retentionEndsAtMillis;
        private final double durationScale;
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
                double durationScale,
                Segment[] segments,
                PositionedItem stationaryPosition) {
            this.startedAtMillis = startedAtMillis;
            this.travelEndsAtMillis = travelEndsAtMillis;
            this.retentionEndsAtMillis = travelEndsAtMillis;
            this.durationScale = durationScale;
            this.segments = segments;
            this.stationaryPosition = stationaryPosition;
            var itemTypeScale = payload.stack().getItem() instanceof BlockItem ? 1.0F : NON_BLOCK_ITEM_SCALE;
            this.itemScale = payload.minimumCableWidth() * ITEM_SCALE_PER_CABLE_UNIT * itemTypeScale;
            this.seed = payload.stack().hashCode();
            this.item = Objects.requireNonNull(AEItemKey.of(payload.stack()));
        }

        static MovingTransfer create(TransferPathPayload payload, long now, Vec3 camera,
                ResourceKey<Level> cameraDimension) {
            var segments = createSegments(payload.legs(), camera, cameraDimension);
            var duration = segments.length == 0 ? 0 : segments[segments.length - 1].endsAtMillis;
            // The lifetime limit takes precedence over the normal distance-based speed cap.
            var durationScale = duration > MAX_TRAVEL_MILLIS ? MAX_TRAVEL_MILLIS / duration : 1.0;
            var travelMillis = Math.max(250L, Math.min(MAX_TRAVEL_MILLIS, Math.round(duration)));
            return new MovingTransfer(
                    payload,
                    now,
                    now + travelMillis,
                    durationScale,
                    segments,
                    firstPosition(payload.legs()));
        }

        PositionedItem positionAt(long now) {
            if (now >= travelEndsAtMillis) {
                return null;
            }

            if (segments.length == 0) {
                return stationaryPosition;
            }

            var elapsed = Math.max(0L, now - startedAtMillis);
            var routeTime = Math.min(segments[segments.length - 1].endsAtMillis, elapsed / durationScale);
            var segment = findSegment(routeTime);
            var segmentStart = segment.endsAtMillis - segment.durationMillis;
            var progress = Math.clamp((routeTime - segmentStart) / segment.durationMillis, 0.0, 1.0);
            return new PositionedItem(segment.dimension, segment.from.lerp(segment.to, progress));
        }

        private Segment findSegment(double routeTime) {
            var low = 0;
            var high = segments.length - 1;
            while (low < high) {
                var middle = (low + high) >>> 1;
                if (routeTime <= segments[middle].endsAtMillis) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return segments[low];
        }

        int lightAt(Level level, BlockPos position) {
            if (!position.equals(lightPosition)) {
                lightPosition = position;
                light = LevelRenderer.getLightColor(level, position);
            }
            return light;
        }

        private static Segment[] createSegments(List<List<GlobalPos>> legs, Vec3 camera,
                ResourceKey<Level> cameraDimension) {
            var result = new ArrayList<Segment>();
            double elapsedMillis = 0;
            var minimumSpeed = ClientConfig.ITEM_MOVEMENT_SPEED.getAsDouble();
            for (var leg : legs) {
                for (int pointIndex = 1; pointIndex < leg.size(); pointIndex++) {
                    var from = leg.get(pointIndex - 1);
                    var to = leg.get(pointIndex);
                    if (from.dimension().equals(to.dimension())) {
                        var fromPosition = itemCenter(from);
                        var toPosition = itemCenter(to);
                        var length = fromPosition.distanceTo(toPosition);
                        if (length > 1.0E-6) {
                            var cameraDistance = from.dimension().equals(cameraDimension)
                                    ? fromPosition.lerp(toPosition, 0.5).distanceTo(camera)
                                    : FAR_CAMERA_DISTANCE;
                            var progress = Math.clamp((cameraDistance - NEAR_CAMERA_DISTANCE)
                                    / (FAR_CAMERA_DISTANCE - NEAR_CAMERA_DISTANCE), 0.0, 1.0);
                            var smoothProgress = progress * progress * (3.0 - 2.0 * progress);
                            var speed = minimumSpeed + (MAX_MOVEMENT_SPEED - minimumSpeed) * smoothProgress;
                            var durationMillis = length / speed * 1_000.0;
                            elapsedMillis += durationMillis;
                            result.add(new Segment(
                                    from.dimension(),
                                    fromPosition,
                                    toPosition,
                                    durationMillis,
                                    elapsedMillis));
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
            ResourceKey<Level> dimension,
            Vec3 from,
            Vec3 to,
            double durationMillis,
            double endsAtMillis) {
    }
}

package rearth.transparentae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.parts.AEBasePart;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import rearth.transparentae2.network.TransferPathNetworking;
import rearth.transparentae2.network.TransferPathPayload;
import rearth.transparentae2.network.TransferPathPayload.PathKind;

public final class TransferLogger {
    private static final String NETWORK = "ME network";

    private TransferLogger() {
    }

    public static void logNetworkInsert(AEKey what, long amount, IActionSource source) {
        try {
            if (amount > 0 && what instanceof AEItemKey item) {
                var kind = source.machine().orElse(null) instanceof ImportBusPart ? PathKind.IMPORT : PathKind.INSERT;
                if (isTransferLoggingEnabled()) {
                    log(kind.name(), item, amount, sourceEndpoint(source, true), NETWORK);
                }
                TransferBatcher.enqueue(kind, item, amount, sourceNode(source), false);
            }
        } catch (RuntimeException e) {
            TransparentAE2.LOGGER.warn("Failed to observe AE2 network insertion", e);
        }
    }

    public static void logNetworkExtract(AEKey what, long amount, IActionSource source) {
        try {
            if (amount > 0 && what instanceof AEItemKey item) {
                var kind = source.machine().orElse(null) instanceof ExportBusPart ? PathKind.EXPORT : PathKind.EXTRACT;
                if (isTransferLoggingEnabled()) {
                    log(kind.name(), item, amount, NETWORK, sourceEndpoint(source, false));
                }
                TransferBatcher.enqueue(kind, item, amount, sourceNode(source), true);
            }
        } catch (RuntimeException e) {
            TransparentAE2.LOGGER.warn("Failed to observe AE2 network extraction", e);
        }
    }

    public static void logCraftingDispatch(AEKey what, long amount, IGridNode cpu, IGridNode provider) {
        try {
            if (amount > 0 && what instanceof AEItemKey item) {
                if (isTransferLoggingEnabled()) {
                    log("CRAFTING", item, amount, "crafting CPU",
                            "crafting provider " + describeNode(provider));
                }
                TransferBatcher.enqueueCrafting(item, amount, cpu, provider);
            }
        } catch (RuntimeException e) {
            TransparentAE2.LOGGER.warn("Failed to observe AE2 crafting dispatch", e);
        }
    }

    private static void log(String kind, AEItemKey item, long amount, String from, String to) {
        var stack = item.getReadOnlyStack();
        var itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        var components = stack.getComponentsPatch().isEmpty() ? "" : " components=" + stack.getComponentsPatch();
        TransparentAE2.LOGGER.debug("[AE2 TRANSFER/{}] {}x {}{} | {} -> {}",
                kind, amount, itemId, components, from, to);
    }

    public static boolean shouldObserveCrafting() {
        return Config.ENABLE_TRANSFER_PATHS.getAsBoolean() || isTransferLoggingEnabled();
    }

    private static boolean isTransferLoggingEnabled() {
        return Config.ENABLE_TRANSFER_LOGGING.getAsBoolean() && TransparentAE2.LOGGER.isDebugEnabled();
    }

    static void handleResolvedPath(
            PathKind kind,
            AEItemKey item,
            long amount,
            TransferPathResolver.ResolvedPath path,
            IGridNode anchor) {
        if (path.available() && !path.shouldRenderTransfer()) {
            return;
        }

        if (isTransferLoggingEnabled()) {
            var itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
            TransparentAE2.LOGGER.debug("[AE2 PATH/{}] {}x {} | {}", kind, amount, itemId, path.format());
        }
        if (path.available()) {
            var payload = new TransferPathPayload(
                    kind,
                    item.getReadOnlyStack(),
                    amount,
                    path.positions(),
                    path.minimumCableWidth());
            TransferPathNetworking.send(payload, anchor);
        }
    }

    private static IGridNode sourceNode(IActionSource source) {
        return source.machine()
                .map(IActionHost::getActionableNode)
                .orElse(null);
    }

    private static String sourceEndpoint(IActionSource source, boolean inserting) {
        var machine = source.machine().orElse(null);
        if (machine != null) {
            return describeMachine(machine, inserting);
        }

        return source.player()
                .map(TransferLogger::describePlayer)
                .orElse("unknown source");
    }

    private static String describeMachine(IActionHost machine, boolean inserting) {
        if (machine instanceof AEBasePart part) {
            var position = part.getBlockEntity().getBlockPos();
            var side = part.getSide();

            if (side != null
                    && ((inserting && part instanceof ImportBusPart)
                    || (!inserting && part instanceof ExportBusPart))) {
                position = position.relative(side);
                return part.getClass().getSimpleName() + " external " + describe(part.getBlockEntity(), position);
            }

            return part.getClass().getSimpleName() + " " + describe(part.getBlockEntity(), position);
        }

        var node = machine.getActionableNode();
        if (node != null) {
            var owner = node.getOwner();
            if (owner instanceof BlockEntity blockEntity) {
                return machine.getClass().getSimpleName() + " " + describe(blockEntity);
            }
            if (owner instanceof AEBasePart part) {
                return part.getClass().getSimpleName() + " " + describe(part.getBlockEntity());
            }
        }

        return machine.getClass().getSimpleName();
    }

    private static String describeNode(IGridNode node) {
        if (node == null) {
            return "(unknown position)";
        }

        var owner = node.getOwner();
        if (owner instanceof BlockEntity blockEntity) {
            return owner.getClass().getSimpleName() + " " + describe(blockEntity);
        }
        if (owner instanceof AEBasePart part) {
            return owner.getClass().getSimpleName() + " " + describe(part.getBlockEntity());
        }
        return owner.getClass().getSimpleName() + " (no world position)";
    }

    private static String describePlayer(Player player) {
        return "player " + player.getGameProfile().getName() + " @ "
                + player.level().dimension().location() + " " + player.blockPosition().toShortString();
    }

    private static String describe(BlockEntity blockEntity) {
        return describe(blockEntity, blockEntity.getBlockPos());
    }

    private static String describe(BlockEntity blockEntity, BlockPos position) {
        var level = blockEntity.getLevel();
        var dimension = level == null ? "unloaded" : level.dimension().location().toString();
        return "@ " + dimension + " " + position.toShortString();
    }
}

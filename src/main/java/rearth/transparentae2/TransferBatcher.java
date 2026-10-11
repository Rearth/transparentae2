package rearth.transparentae2;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import rearth.transparentae2.network.TransferPathPayload.PathKind;

final class TransferBatcher {
    private static final Map<BatchKey, Long> PENDING = new LinkedHashMap<>();

    private TransferBatcher() {
    }

    static void enqueue(
            PathKind kind,
            AEItemKey item,
            long amount,
            IGridNode endpoint,
            boolean controllerFirst) {
        if (!Config.ENABLE_TRANSFER_PATHS.getAsBoolean() || !NetworkRenderService.isEnabled(endpoint)) {
            return;
        }
        enqueue(new BatchKey(kind, item, new SinglePathRequest(endpoint, controllerFirst)), amount);
    }

    static void enqueueCrafting(
            AEItemKey item,
            long amount,
            IGridNode cpu,
            IGridNode provider) {
        if (!Config.ENABLE_TRANSFER_PATHS.getAsBoolean()
                || !NetworkRenderService.isEnabled(cpu != null ? cpu : provider)) {
            return;
        }
        enqueue(new BatchKey(PathKind.CRAFTING, item, new CraftingPathRequest(cpu, provider)), amount);
    }

    private static void enqueue(BatchKey key, long amount) {
        PENDING.merge(key, amount, TransferBatcher::saturatedAdd);
    }

    static void flush(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        if (!Config.ENABLE_TRANSFER_PATHS.getAsBoolean()) {
            PENDING.clear();
            return;
        }

        var transfers = new LinkedHashMap<>(PENDING);
        PENDING.clear();
        var paths = new HashMap<PathRequest, TransferPathResolver.ResolvedPath>();

        for (var entry : transfers.entrySet()) {
            var key = entry.getKey();
            try {
                if (!NetworkRenderService.isEnabled(key.path.anchor())) {
                    continue;
                }
                var path = paths.computeIfAbsent(key.path, PathRequest::resolve);
                TransferLogger.handleResolvedPath(
                        key.kind,
                        key.item,
                        entry.getValue(),
                        path,
                        key.path.anchor());
            } catch (RuntimeException e) {
                TransparentAE2.LOGGER.warn("Failed to process a batched AE2 transfer", e);
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record BatchKey(PathKind kind, AEItemKey item, PathRequest path) {
    }

    private sealed interface PathRequest permits SinglePathRequest, CraftingPathRequest {
        TransferPathResolver.ResolvedPath resolve();

        IGridNode anchor();
    }

    private record SinglePathRequest(IGridNode endpoint, boolean controllerFirst) implements PathRequest {
        @Override
        public TransferPathResolver.ResolvedPath resolve() {
            return TransferPathResolver.resolve(endpoint, controllerFirst);
        }

        @Override
        public IGridNode anchor() {
            return endpoint;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SinglePathRequest request
                    && endpoint == request.endpoint
                    && controllerFirst == request.controllerFirst;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(endpoint) + Boolean.hashCode(controllerFirst);
        }
    }

    private record CraftingPathRequest(IGridNode cpu, IGridNode provider) implements PathRequest {
        @Override
        public TransferPathResolver.ResolvedPath resolve() {
            return TransferPathResolver.resolveCrafting(cpu, provider);
        }

        @Override
        public IGridNode anchor() {
            return cpu != null ? cpu : provider;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CraftingPathRequest request
                    && cpu == request.cpu
                    && provider == request.provider;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(cpu) + System.identityHashCode(provider);
        }
    }
}

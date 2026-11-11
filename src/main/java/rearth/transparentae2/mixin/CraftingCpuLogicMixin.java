package rearth.transparentae2.mixin;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import rearth.transparentae2.TransferLogger;
import rearth.transparentae2.TransparentAE2;

@Mixin(CraftingCpuLogic.class)
abstract class CraftingCpuLogicMixin {
    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Unique
    private final Map<ICraftingProvider, IGridNode> transparentae2$providerNodes = new IdentityHashMap<>();

    @Redirect(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern"
                            + "(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean transparentae2$logCraftingDispatch(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputs) {
        if (!TransferLogger.shouldObserveCrafting()) {
            return provider.pushPattern(pattern, inputs);
        }

        var transferred = new ArrayList<GenericStack>();
        for (var slot : inputs) {
            for (var entry : slot) {
                transferred.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }

        var accepted = provider.pushPattern(pattern, inputs);
        if (accepted) {
            try {
                var providerNode = findProviderNode(provider);
                for (var stack : transferred) {
                    TransferLogger.logCraftingDispatch(stack.what(), stack.amount(), providerNode);
                }
            } catch (RuntimeException e) {
                TransparentAE2.LOGGER.warn("Failed to observe an AE2 crafting dispatch", e);
            }
        }
        return accepted;
    }

    private IGridNode findProviderNode(ICraftingProvider provider) {
        var grid = cluster.getGrid();
        if (grid == null) {
            return null;
        }

        var cached = transparentae2$providerNodes.get(provider);
        if (cached != null
                && cached.getGrid() == grid
                && cached.getService(ICraftingProvider.class) == provider) {
            return cached;
        }
        transparentae2$providerNodes.remove(provider);

        for (var node : grid.getNodes()) {
            if (node.getService(ICraftingProvider.class) == provider) {
                transparentae2$providerNodes.put(provider, node);
                return node;
            }
        }
        return null;
    }
}

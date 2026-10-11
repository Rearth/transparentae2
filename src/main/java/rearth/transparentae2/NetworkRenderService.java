package rearth.transparentae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.me.GridNode;
import net.minecraft.nbt.CompoundTag;

/** A shared setting, persisted through AE2's grid service node data. */
public final class NetworkRenderService implements IGridService, IGridServiceProvider {
    private static final String ENABLED_KEY = "transparentae2:render_items";
    private final IGrid grid;
    private boolean enabled = true;
    private boolean settingLoaded;

    public NetworkRenderService(IGrid grid) {
        this.grid = grid;
    }

    public static boolean isEnabled(IGridNode node) {
        return node != null && node.getGrid().getService(NetworkRenderService.class).enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        settingLoaded = true;
        markNodesForSave();
    }

    @Override
    public void addNode(IGridNode node, CompoundTag savedData) {
        if (savedData == null || !savedData.contains(ENABLED_KEY)) {
            return;
        }
        if (!settingLoaded) {
            enabled = savedData.getBooleanOr(ENABLED_KEY, true);
            settingLoaded = true;
            markNodesForSave();
        } else if (savedData.getBooleanOr(ENABLED_KEY, true) != enabled) {
            markNodeForSave(node);
        }
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        savedData.putBoolean(ENABLED_KEY, enabled);
    }

    private void markNodesForSave() {
        for (var node : grid.getNodes()) {
            markNodeForSave(node);
        }
    }

    private static void markNodeForSave(IGridNode node) {
        ((GridNode) node).callListener(IGridNodeListener::onSaveChanges);
    }

}

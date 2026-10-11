package rearth.transparentae2;

/** Client access to the server-authoritative terminal setting. */
public interface NetworkRenderMenu {
    boolean transparentae2$hasNetwork();

    boolean transparentae2$isRenderingEnabled();

    void transparentae2$setRenderingEnabled(boolean enabled);
}

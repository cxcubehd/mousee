package dev.chrones.mixin;

import dev.chrones.input.RawMouseController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Unique
    private static final String TURN_PLAYER = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V";

    @Shadow @Final private Minecraft minecraft;

    @Shadow private boolean ignoreFirstMove;

    @Shadow private double xpos;

    @Shadow private double ypos;

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    @Shadow private boolean mouseGrabbed;

    @Unique private final double[] mouseeRawDelta = new double[4];

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void mouseeSuppressVanillaDelta(
        final long handle, final double xpos, final double ypos, final CallbackInfo ci) {
        if (handle != this.minecraft.getWindow().handle()) {
            return;
        }

        if (!RawMouseController.shouldSuppressVanillaCursorDelta(
                this.minecraft, this.mouseGrabbed)) {
            return;
        }

        if (this.ignoreFirstMove) {
            this.ignoreFirstMove = false;
        }

        this.xpos = xpos;
        this.ypos = ypos;
        ci.cancel();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = TURN_PLAYER))
    private void mouseeInjectRawDelta(final CallbackInfo ci) {
        if (RawMouseController.pollCameraDeltas(
                this.minecraft, this.mouseGrabbed, this.mouseeRawDelta)) {
            this.accumulatedDX += this.mouseeRawDelta[0];
            this.accumulatedDY += this.mouseeRawDelta[1];
            this.minecraft.getFramerateLimitTracker().onInputReceived();
        }
    }

    @Inject(method = "grabMouse", at = @At("TAIL"))
    private void mouseeAfterGrabMouse(final CallbackInfo ci) {
        RawMouseController.syncCaptureState(this.minecraft, this.mouseGrabbed);
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void mouseeAfterReleaseMouse(final CallbackInfo ci) {
        RawMouseController.syncCaptureState(this.minecraft, this.mouseGrabbed);
    }
}

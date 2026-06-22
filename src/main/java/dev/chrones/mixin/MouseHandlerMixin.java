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
    @Shadow @Final private Minecraft minecraft;

    @Shadow private boolean ignoreFirstMove;

    @Shadow private double xpos;

    @Shadow private double ypos;

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    @Shadow private boolean mouseGrabbed;

    @Unique private final double[] mousee$rawDelta = new double[4];

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void mousee$suppressVanillaRelativeCursorDelta(
        final long handle, final double xpos, final double ypos, final CallbackInfo ci) {
        if (handle != this.minecraft.getWindow().handle()) {
            return;
        }

        if (!RawMouseController.shouldReplaceVanillaDeltas(this.minecraft, this.mouseGrabbed)) {
            return;
        }

        if (this.ignoreFirstMove) {
            this.ignoreFirstMove = false;
        }

        this.xpos = xpos;
        this.ypos = ypos;
        ci.cancel();
    }

    @Inject(method = "handleAccumulatedMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V"))
    private void
    mousee$injectGameControllerRawDelta(final CallbackInfo ci) {
        if (RawMouseController.pollMinecraftDeltas(
                this.minecraft, this.mouseGrabbed, this.mousee$rawDelta)) {
            this.accumulatedDX += this.mousee$rawDelta[0];
            this.accumulatedDY += this.mousee$rawDelta[1];
            this.minecraft.getFramerateLimitTracker().onInputReceived();
        }
    }

    @Inject(method = "grabMouse", at = @At("TAIL"))
    private void mousee$afterGrabMouse(final CallbackInfo ci) {
        RawMouseController.updateCaptureState(this.minecraft, this.mouseGrabbed);
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void mousee$afterReleaseMouse(final CallbackInfo ci) {
        RawMouseController.updateCaptureState(this.minecraft, this.mouseGrabbed);
    }
}

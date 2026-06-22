package dev.chrones.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chrones.input.RawMouseController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
    @Inject(method = "isRawMouseInputSupported", at = @At("RETURN"), cancellable = true)
    private static void mousee$reportMacosGameControllerRawMouse(
        final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && RawMouseController.isNativeRawMouseSupported()) {
            cir.setReturnValue(true);
        }
    }
}

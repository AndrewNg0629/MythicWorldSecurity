package top.aenp.msec.mixin;

import net.minecraft.util.StringHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.aenp.msec.network.MSecNetwork;

@Mixin(StringHelper.class)
public class StringHelperMixin {
    @Inject(method = "isValidPlayerName", at = @At(value = "RETURN"), cancellable = true)
    private static void isValidPlayerName(String name, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValue() && name != null) {
            if (!name.isBlank()) {
                info.setReturnValue(MSecNetwork.VALID_PLAYERNAME_PATTERN.matcher(name).matches());
            }
        }
    }
}

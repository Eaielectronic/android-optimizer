package fr.eaielectronic.androidopt.mixin;

import fr.eaielectronic.androidopt.AndroidOptMod;
import fr.eaielectronic.androidopt.ConfigGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;


@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter",
       remap = false)
public class JeiListLimiterMixin {

    private static final int MAX_VISIBLE_ITEMS = 200;

    @Inject(
        method = "getIngredientList",
        at = @At("RETURN"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void androidopt$limitJeiList(CallbackInfoReturnable<List<?>> cir) {
        if (!ConfigGuard.isReady()) return;

        List<?> list = cir.getReturnValue();
        if (list != null && list.size() > MAX_VISIBLE_ITEMS) {
            cir.setReturnValue(list.subList(0, MAX_VISIBLE_ITEMS));
            AndroidOptMod.LOGGER.debug(
                "[AndroidOpt] JeiListLimiterMixin : {} → {} items", list.size(), MAX_VISIBLE_ITEMS);
        }
    }
}

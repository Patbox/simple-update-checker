package eu.pb4.simpleupdatechecker.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.simpleupdatechecker.ModInit;
import eu.pb4.simpleupdatechecker.ModpackConfig;
import eu.pb4.simpleupdatechecker.UserConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
    @Unique
    private PressableTextWidget updateAvailableWidget = null;
    @Unique
    private ModInit.Version previousVersion = ModInit.updateVersion;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addModpackVersion(CallbackInfo ci) {
        if (!UserConfig.get().disableMainMenu) {
            this.updateAvailableWidget = null;
            this.updateVersionButton();
        }
    }

    @Unique
    private void updateVersionButton() {
        if (updateAvailableWidget != null && Objects.equals(ModInit.updateVersion, previousVersion)) {
            return;
        } else if (updateAvailableWidget != null) {
            this.remove(updateAvailableWidget);
        }
        this.previousVersion = ModInit.updateVersion;
        if (this.previousVersion == null || UserConfig.get().disableMainMenu) {
            return;
        }

        var text = Text.empty()
                .append(Text.literal("[").setStyle(Style.EMPTY.withColor(Formatting.GRAY).withUnderline(false)))
                .append(Text.translatable("text.simpleupdatechecker.update_available_client", previousVersion.displayVersion())
                                .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.GRAY).withUnderline(false)))
                ;

        updateAvailableWidget = new PressableTextWidget(0, this.height - 20, this.textRenderer.getWidth(text), 10, text,
                button -> ConfirmLinkScreen.open(this, previousVersion.url(), true), this.textRenderer);
        updateAvailableWidget.visible = false;
        this.addDrawableChild(updateAvailableWidget);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
    private void renderModpackVersion(DrawContext instance, TextRenderer textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
        if (!UserConfig.get().disableMainMenu) {
            this.updateVersionButton();
            var modpackText = ModpackConfig.get().getFullName();
            if (updateAvailableWidget != null) {
                updateAvailableWidget.setX(textRenderer.getWidth(modpackText + " ") + x);
                updateAvailableWidget.setY(y - 10);
                updateAvailableWidget.visible = true;
            }
            instance.drawTextWithShadow(textRenderer, modpackText, x, y - 10, color);
        }
        original.call(instance, textRenderer, text, x, y, color);
    }
}

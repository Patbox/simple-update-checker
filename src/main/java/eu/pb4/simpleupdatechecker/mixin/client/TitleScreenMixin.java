package eu.pb4.simpleupdatechecker.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import eu.pb4.simpleupdatechecker.ModInit;
import eu.pb4.simpleupdatechecker.ModpackConfig;
import eu.pb4.simpleupdatechecker.UserConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
    @Unique
    private PlainTextButton updateAvailableWidget = null;
    @Unique
    private ModInit.Version previousVersion = ModInit.updateVersion;

    protected TitleScreenMixin(Component title) {
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
            this.removeWidget(updateAvailableWidget);
        }
        this.previousVersion = ModInit.updateVersion;
        if (this.previousVersion == null || UserConfig.get().disableMainMenu) {
            return;
        }

        var text = Component.empty()
                .append(Component.literal("[").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withUnderlined(false)))
                .append(Component.translatable("text.simpleupdatechecker.update_available_client", previousVersion.displayVersion())
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
                .append(Component.literal("]").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withUnderlined(false)))
                ;

        updateAvailableWidget = new PlainTextButton(0, this.height - 20, this.font.width(text), 10, text,
                button -> ConfirmLinkScreen.confirmLinkNow(this, previousVersion.url(), true), this.font);
        updateAvailableWidget.visible = false;
        this.addRenderableWidget(updateAvailableWidget);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void renderModpackVersion(GuiGraphicsExtractor instance, Font textRenderer, String text, int x, int y, int color, Operation<Integer> original) {
        if (!UserConfig.get().disableMainMenu) {
            this.updateVersionButton();
            var modpackText = ModpackConfig.get().getFullName();
            if (updateAvailableWidget != null) {
                updateAvailableWidget.setX(textRenderer.width(modpackText + " ") + x);
                updateAvailableWidget.setY(y - 10);
                updateAvailableWidget.visible = true;
            }
            instance.text(textRenderer, modpackText, x, y - 10, color);
        }
        original.call(instance, textRenderer, text, x, y, color);
    }
}

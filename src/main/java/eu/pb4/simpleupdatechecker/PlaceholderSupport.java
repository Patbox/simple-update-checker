package eu.pb4.simpleupdatechecker;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PlaceholderSupport {
    public static void register() {
        register("name", () -> ModpackConfig.get().getDisplayName());
        register("name_full", () -> ModpackConfig.get().getFullName());
        register("version", () -> ModpackConfig.get().getDisplayVersion());

        register("project_id", () -> ModpackConfig.get().projectId);
        register("version_id", () -> ModpackConfig.get().versionId);

        register("latest_version", () -> ModInit.updateVersion != null ? ModInit.updateVersion.displayVersion() : ModpackConfig.get().getDisplayVersion());
        register("update_version", () -> ModInit.updateVersion != null ? ModInit.updateVersion.displayVersion() : "");
        register("update_version/url", () -> ModInit.updateVersion != null ? ModInit.updateVersion.url() : "");
    }

    private static void register(String path, Supplier<String> supplier) {
        Placeholders.register(Identifier.fromNamespaceAndPath("simpleupdatechecker", path), (ctx, arg) -> PlaceholderResult.value(Component.literal(supplier.get())));
    }
}

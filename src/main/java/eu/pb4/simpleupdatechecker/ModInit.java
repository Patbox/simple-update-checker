package eu.pb4.simpleupdatechecker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ModInit implements ModInitializer, DedicatedServerModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Simple Update Checker");
    private static final long timeout = Duration.ofMinutes(10).toMillis();
    private static final long CLIENT_RECHECK_TIMEOUT = 20 * (Duration.ofHours(1).toSeconds());
    private static final long INITIAL_SERVER_RECHECK_TIMEOUT = 20 * (Duration.ofHours(12).toSeconds());
    private static final long MAX_SERVER_RECHECK_TIMEOUT = 20 * (Duration.ofDays(14).toSeconds());
    @Nullable
    public static Version updateVersion = null;
    public static boolean noVersionsAvailable = false;
    public static long lastUpdate = -1;
    public static long tick;
    private static long serverRecheckTimeout = INITIAL_SERVER_RECHECK_TIMEOUT;

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        dispatcher.register(
                literal("simpleupdatechecker")
                        .requires(x -> x.hasPermissionLevel(4) || (x.isExecutedByPlayer() && x.getServer().isHost(Objects.requireNonNull(x.getPlayer()).getGameProfile())))
                        .then(literal("check").executes(x -> {
                            checkUpdates(() -> sentTextUpdate(x.getSource()::sendMessage, true));
                            return 0;
                        }))
                        .then(literal("reload").executes(x -> {
                            ModpackConfig.clear();
                            UserConfig.clear();
                            tick = 0;
                            lastUpdate = -1;
                            return 0;
                        }))
                        .then(Util.make(literal("settings"), (l) -> {
                            for (var field : UserConfig.getToggles()) {
                                l.then(literal(field.name())
                                        .executes(x -> {
                                            x.getSource().sendMessage(Text.translatable("text.simpleupdatechecker.command.setting.current", field.name(), String.valueOf(field.get().get())));
                                            return field.get().get() ? 1 : 0;
                                        }).then(argument("value", BoolArgumentType.bool()).executes((x) -> {
                                            var arg = BoolArgumentType.getBool(x, "value");
                                            field.set().accept(arg);
                                            x.getSource().sendMessage(Text.translatable("text.simpleupdatechecker.command.setting.changed", field.name(), String.valueOf(arg)));
                                            return arg ? 1 : 0;
                                        })));
                            }
                        }))
        );
    }

    public static void sentConsoleUpdate() {
        if (updateVersion != null) {
            LOGGER.info("=====================================================================");
            LOGGER.info("Modpack update available! Current version {}, latest {}.", ModpackConfig.get().getDisplayVersion(), updateVersion.displayVersion());
            LOGGER.info("Download it here: {}", updateVersion.url);
            LOGGER.info("=====================================================================");
        }
    }

    public static void sentTextUpdate(Consumer<Text> textConsumer, boolean sentNoUpdates) {
        if (updateVersion != null) {
            textConsumer.accept(Text.empty()
                    .append(Text.translatable("text.simpleupdatechecker.update_available.title").formatted(Formatting.GOLD))
                    .append(" ")
                    .append(Text.translatable("text.simpleupdatechecker.update_available.version",
                            Text.literal(ModpackConfig.get().getDisplayVersion()).formatted(Formatting.WHITE),
                            Text.literal(updateVersion.displayVersion()).formatted(Formatting.WHITE)
                    ).formatted(Formatting.YELLOW)));
            textConsumer.accept(Text.translatable("text.simpleupdatechecker.update_available.download",
                    Text.literal(updateVersion.url)
                            .setStyle(Style.EMPTY.withColor(Formatting.BLUE).withUnderline(true)
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(updateVersion.url())))
                            )
            ).formatted(Formatting.GRAY));
        } else if (sentNoUpdates) {
            if (noVersionsAvailable) {
                textConsumer.accept(Text.translatable("text.simpleupdatechecker.no_updates.error").formatted(Formatting.RED));
            } else {
                textConsumer.accept(Text.translatable("text.simpleupdatechecker.no_updates.latest").formatted(Formatting.GREEN));
            }
        }
    }

    private static void checkUpdates(Runnable runnable) {
        if (UserConfig.get().disableUpdateChecking) {
            return;
        }
        if (System.currentTimeMillis() - lastUpdate < timeout) {
            runnable.run();
            return;
        }


        var config = ModpackConfig.get();
        ModrinthModpackVersion.getVersions().thenAccept(versions -> {
            lastUpdate = System.currentTimeMillis();
            if (versions == null || versions.isEmpty()) {
                noVersionsAvailable = true;
                return;
            }
            noVersionsAvailable = false;
            tick = 0;
            var stream = versions.stream();
            stream = stream.filter(x -> config.releaseType.contains(x.versionType));
            if (!config.maxExclusiveVersion.isEmpty()) {
                stream = stream.filter(x -> FlexVerComparator.compare(x.versionNumber, config.maxExclusiveVersion) < 0);
            }
            stream = stream.filter(x -> FlexVerComparator.compare(x.versionNumber, config.versionId) > 0);

            var out = stream.findFirst();

            if (out.isEmpty()) {
                updateVersion = null;
                return;
            }

            updateVersion = new Version(out.get().name, out.get().versionNumber, config.versionPage(out.get().id, out.get().versionNumber));
            runnable.run();
        });
    }

    @Override
    public void onInitialize() {
        ModpackConfig.get();
        checkUpdates(UserConfig.get().disableLogsFirstCheck ? () -> {
        } : ModInit::sentConsoleUpdate);
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            PlaceholderSupport.register();
        }

        CommandRegistrationCallback.EVENT.register(ModInit::registerCommands);
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(x -> {
            if (tick++ > CLIENT_RECHECK_TIMEOUT) {
                tick = 0;
                checkUpdates(() -> {
                });
            }
        });
    }

    @Override
    public void onInitializeServer() {
        if (!UserConfig.get().disableLogsServerStarted) {
            ServerLifecycleEvents.SERVER_STARTED.register(x -> sentConsoleUpdate());
        }
        ServerTickEvents.END_SERVER_TICK.register(x -> {
            if (tick++ > serverRecheckTimeout) {
                tick = 0;
                checkUpdates(UserConfig.get().disableLogsLate ? () -> {
                } : ModInit::sentConsoleUpdate);
                if (updateVersion != null) {
                    serverRecheckTimeout = Math.max(serverRecheckTimeout * 2, MAX_SERVER_RECHECK_TIMEOUT);
                } else {
                    serverRecheckTimeout = INITIAL_SERVER_RECHECK_TIMEOUT;
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.hasPermissionLevel(4) && !UserConfig.get().disableJoinOps) {
                sentTextUpdate(handler.player::sendMessage, false);
            }
        });
    }

    public record Version(String name, String versionNumber, String url) {
        public String displayVersion() {
            return ModpackConfig.get().useVersionNumberForUpdateDisplay ? versionNumber : name;
        }
    }
}

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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access, Commands.CommandSelection env) {
        dispatcher.register(
                literal("simpleupdatechecker")
                        .requires(Util.anyOf(Commands.hasPermission(Commands.LEVEL_ADMINS), x -> (x.isPlayer() && x.getServer().isSingleplayerOwner(Objects.requireNonNull(x.getPlayer()).nameAndId()))))
                        .then(literal("check").executes(x -> {
                            checkUpdates(() -> sentTextUpdate(x.getSource()::sendSystemMessage, true));
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
                                            x.getSource().sendSystemMessage(Component.translatable("text.simpleupdatechecker.command.setting.current", field.name(), String.valueOf(field.get().get())));
                                            return field.get().get() ? 1 : 0;
                                        }).then(argument("value", BoolArgumentType.bool()).executes((x) -> {
                                            var arg = BoolArgumentType.getBool(x, "value");
                                            field.set().accept(arg);
                                            x.getSource().sendSystemMessage(Component.translatable("text.simpleupdatechecker.command.setting.changed", field.name(), String.valueOf(arg)));
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

    public static void sentTextUpdate(Consumer<Component> textConsumer, boolean sentNoUpdates) {
        if (updateVersion != null) {
            textConsumer.accept(Component.empty()
                    .append(Component.translatable("text.simpleupdatechecker.update_available.title").withStyle(ChatFormatting.GOLD))
                    .append(" ")
                    .append(Component.translatable("text.simpleupdatechecker.update_available.version",
                            Component.literal(ModpackConfig.get().getDisplayVersion()).withStyle(ChatFormatting.WHITE),
                            Component.literal(updateVersion.displayVersion()).withStyle(ChatFormatting.WHITE)
                    ).withStyle(ChatFormatting.YELLOW)));
            textConsumer.accept(Component.translatable("text.simpleupdatechecker.update_available.download",
                    Component.literal(updateVersion.url)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withUnderlined(true)
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create(updateVersion.url())))
                            )
            ).withStyle(ChatFormatting.GRAY));
        } else if (sentNoUpdates) {
            if (noVersionsAvailable) {
                textConsumer.accept(Component.translatable("text.simpleupdatechecker.no_updates.error").withStyle(ChatFormatting.RED));
            } else {
                textConsumer.accept(Component.translatable("text.simpleupdatechecker.no_updates.latest").withStyle(ChatFormatting.GREEN));
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
            if (Commands.LEVEL_ADMINS.check(handler.player.permissions()) && !UserConfig.get().disableJoinOps) {
                sentTextUpdate(handler.player::sendSystemMessage, false);
            }
        });
    }

    public record Version(String name, String versionNumber, String url) {
        public String displayVersion() {
            return ModpackConfig.get().useVersionNumberForUpdateDisplay ? versionNumber : name;
        }
    }
}

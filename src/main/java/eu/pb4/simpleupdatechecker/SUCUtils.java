package eu.pb4.simpleupdatechecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;

public interface SUCUtils {

    Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().setLenient().create();

    String USERAGENT = Util.make(() -> {
        var x = FabricLoader.getInstance().getModContainer("simpleupdatechecker").get();

        return "Simple Update Checker v" + x.getMetadata().getVersion() + " (pb4.eu)";
    });

    static HttpClient createHttpClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    }

    static HttpRequest createGetRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .setHeader("User-Agent", USERAGENT)
                .GET()
                .build();
    }


    static Path configPath(String type) {
        return FabricLoader.getInstance().getConfigDir().resolve("simpleupdatechecker_" + type + ".json");
    }

    static <T> T readConfig(String type, Class<T> clazz) {
        var path = configPath(type);

        T instance = null;
        if (Files.exists(path)) {
            try {
                instance = GSON.fromJson(Files.readString(path), clazz);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (instance == null) {
            try {
                instance = clazz.getConstructor().newInstance();
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }

        writeConfig(type, instance);

        return instance;
    }

    static <T> void writeConfig(String type, T instance) {
        var path = configPath(type);

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

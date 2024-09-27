package eu.pb4.simpleupdatechecker;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModrinthModpackVersion {
    private static final TypeToken<List<ModrinthModpackVersion>> TYPE = new TypeToken<>() {};

    public String name = "";
    @SerializedName("version_number")
    public String versionNumber = "";

    @SerializedName("version_type")
    public String versionType = "";
    public String id = "";

    public static List<ModrinthModpackVersion> read(String s) {
        return SUCUtils.GSON.fromJson(s, TYPE);
    }


    static CompletableFuture<List<ModrinthModpackVersion>> getVersions() {
        try(var client = SUCUtils.createHttpClient()) {
            return client.sendAsync(SUCUtils.createGetRequest(URI.create(ModpackConfig.get().versionSource())), HttpResponse.BodyHandlers.ofString())
                    .thenApply((res) -> {
                        if (res.statusCode() != 200) {
                            ModInit.LOGGER.error("Failed to read Modrinth version list! Got status code {} with body '{}'", res.statusCode(), res.body());
                            return List.of();
                        }
                        return ModrinthModpackVersion.read(res.body());
                    });
        } catch (Throwable e) {
            ModInit.LOGGER.error("Exception thrown while getting version list!", e);
            return CompletableFuture.supplyAsync(List::of);
        }
    }

    public static class File {
        public Map<String, String> hashes = new HashMap<>();
        public long size = -1;

        public URI url;
        public boolean primary = false;
    }
}

package eu.pb4.simpleupdatechecker;

import com.google.gson.annotations.SerializedName;

import java.util.Set;

public class ModpackConfig {
    private static ModpackConfig instance;
    @SerializedName("project_id")
    public String projectId = "some_project";
    @SerializedName("version_id")
    public String versionId = "1.0.0";

    @SerializedName("display_name")
    public String displayName = "My modpack!";

    @SerializedName("display_version")
    public String displayVersion = "";

    @SerializedName("release_type")
    public Set<String> releaseType = Set.of("release", "beta", "alpha");

    @SerializedName("max_exclusive_version")
    public String maxExclusiveVersion = "";

    @SerializedName("use_version_number_for_display")
    public boolean useVersionNumberForUpdateDisplay = true;

    @SerializedName("override_version_source")
    public String overrideVersionSource;

    @SerializedName("override_version_page")
    public String overrideVersionPage;

    public String versionSource() {
        return overrideVersionSource != null
                ? overrideVersionSource.replace("<PROJECTID>", this.projectId)
                : "https://api.modrinth.com/v2/project/" + projectId + "/version";
    }

    public String versionPage(String versionId, String versionNumber) {
        return overrideVersionPage != null
                ? overrideVersionPage.replace("<PROJECTID>", this.projectId).replace("<VERSIONID>", versionId).replace("<VERSION>", versionNumber)
                : "https://modrinth.com/mod/" + this.projectId + "/version/" + versionId;
    }

    public static ModpackConfig get() {
        if (instance == null) {
            instance = SUCUtils.readConfig("modpack", ModpackConfig.class);
        }
        return instance;
    }

    public static void clear() {
        instance = null;
    }


    public String getDisplayVersion() {
        return !this.displayVersion.isEmpty() ? this.displayVersion : this.versionId;
    }

    public String getDisplayName() {
        return !this.displayName.isEmpty() ? this.displayName : this.projectId;
    }

    public String getFullName() {
        return getDisplayName() + " " + this.getDisplayVersion();
    }
}

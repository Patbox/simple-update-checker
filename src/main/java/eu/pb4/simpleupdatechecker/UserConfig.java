package eu.pb4.simpleupdatechecker;

import com.google.gson.annotations.SerializedName;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.literal;

public class UserConfig {
    private static UserConfig instance;
    @SerializedName("disable_update_checking")
    public boolean disableUpdateChecking = false;
    @SerializedName("disable_main_menu")
    public boolean disableMainMenu = false;
    @SerializedName("disable_logs_first_check")
    public boolean disableLogsFirstCheck = false;
    @SerializedName("disable_logs_server_started")
    public boolean disableLogsServerStarted = false;
    @SerializedName("disable_logs_late")
    public boolean disableLogsLate = false;
    @SerializedName("disable_join_ops")
    public boolean disableJoinOps = false;

    public static UserConfig get() {
        if (instance == null) {
            instance = SUCUtils.readConfig("user", UserConfig.class);
        }
        return instance;
    }

    public static List<Toggle> getToggles() {
        var list = new ArrayList<Toggle>();
        for (var field : UserConfig.class.getFields()) {
            var name = field.getAnnotation(SerializedName.class);
            var type = field.getType();
            if (name != null && type == boolean.class) {
                list.add(new Toggle(name.value(), () -> {
                    try {
                        return field.getBoolean(get());
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                }, (x) -> {
                    try {
                        field.setBoolean(get(), x);
                        get().update();
                    } catch (IllegalAccessException ignored) {
                    }
                }));
            }
        }

        return list;
    }

    public void update() {
        SUCUtils.writeConfig("user", this);
    }

    public static void clear() {
        instance = null;
    }


    public record Toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set) {}

}

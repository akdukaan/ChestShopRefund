package com.birdflop.chestshoprefund;

import com.Acrobot.ChestShop.Configuration.Messages;
import com.google.common.base.Throwables;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class Lang {
    private static YamlConfiguration config;

    // Not configurable
    public static String DEBUG_ENABLED = "<green>Debug enabled.";
    public static String DEBUG_DISABLED = "<green>Debug disabled.";
    public static String COMMAND_PLAYERS_ONLY = "<red>That command can only be used by players.";
    public static String RELOAD_SUCCESS = "<gold>Plugin reloaded!";

    // Configurable
    public static String CLICK_TO_UNDO = "<green>[Shop] <gray>Click here to undo.";
    public static String YOU_UNBOUGHT = "<green>[Shop] <white>You sold back {item} to {player} for {price}.";
    public static String YOU_UNSOLD = "<green>[Shop] <white>You bought back {item} from {player} for {price}.";
    public static String SOMEONE_UNBOUGHT = "<green>[Shop] <white>{player} sold back {item} for {price}.";
    public static String SOMEONE_UNSOLD = "<green>[Shop] <white>{player} bought back {item} for {price}.";
    public static String NOT_REFUNDABLE = "<green>[Shop] <white>That transaction has already been refunded.";

    private static void init() {
        CLICK_TO_UNDO = getString("click-to-undo", CLICK_TO_UNDO);
        YOU_UNBOUGHT = getString("you-unbought", YOU_UNBOUGHT);
        YOU_UNSOLD = getString("you-unsold", YOU_UNSOLD);
        SOMEONE_UNBOUGHT = getString("someone-unbought", SOMEONE_UNBOUGHT);
        SOMEONE_UNSOLD = getString("someone-unsold", SOMEONE_UNSOLD);
        NOT_REFUNDABLE = getString("not-refundable", NOT_REFUNDABLE);
    }

    // ######################################################################################

    /**
     * Reload the language file
     */
    public static void reload() {
        File langFile = new File(ChestShopRefund.plugin.getDataFolder(), "lang.yml");
        config = new YamlConfiguration();
        try {
            config.load(langFile);
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load " + "lang.yml", ex);
            throw Throwables.propagate(ex);
        }
        config.options().header("This is the language file for " + ChestShopRefund.plugin.getName());
        config.options().copyDefaults(true);

        Lang.init();

        try {
            config.save(langFile);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + langFile, ex);
        }
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, config.getString(path));
    }

    /**
     * Sends a message to a recipient
     *
     * @param recipient Recipient of message
     * @param message   Message to send
     */
    public static void sendMessage(@NotNull CommandSender recipient, String message) {
        Component component = MiniMessage.miniMessage().deserialize(message);
        recipient.sendMessage(component);
        Messages.
    }

    public static void debug(@NotNull CommandSender recipient, String message) {
        if (ChestShopRefund.debug) {
            Component component = MiniMessage.miniMessage().deserialize(message);
            recipient.sendMessage(component);
        }
    }
}
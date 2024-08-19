package com.birdflop.chestshoprefund;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.google.common.collect.ImmutableMap;
import com.wfector.notifier.ChestShopNotifier;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class ChestShopRefund extends JavaPlugin {
    public static ChestShopRefund plugin = null;
    public static ChestShopNotifier csn = null;
    public static HashMap<Integer, TransactionEvent> transactions = new HashMap<>();
    public static boolean debug = false;


    @Override
    public void onEnable() {
        plugin = this;
        Plugin chestShopNotifier = Bukkit.getPluginManager().getPlugin("ChestShopNotifier");
        if (chestShopNotifier instanceof ChestShopNotifier) {
            csn = (ChestShopNotifier) chestShopNotifier;
        }
        getServer().getPluginManager().registerEvents(new ListenerTransaction(), this);
        getCommand("csrefund").setExecutor(new CommandCsrefund());

        try {
            addCustomMetrics();
        } catch (Throwable e) {
            getLogger().log(Level.WARNING, "Error enabling metrics", e);
        }
    }

    public void addCustomMetrics() {
        Metrics bStats = new Metrics(this, 23059);

        String chestShopVersion = ChestShop.getPlugin().getDescription().getVersion();
        String serverVersion = getServer().getBukkitVersion().split("-")[0];

        bStats.addCustomChart(createStaticDrilldownStat("version_chestshop_plugin", chestShopVersion, getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("version_plugin_chestshop", getDescription().getVersion(), chestShopVersion));

        bStats.addCustomChart(createStaticDrilldownStat("version_mc_plugin", serverVersion, getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("version_plugin_mc", getDescription().getVersion(), serverVersion));

        bStats.addCustomChart(createStaticDrilldownStat("version_brand_plugin", getServer().getName(), getDescription().getVersion()));
        bStats.addCustomChart(createStaticDrilldownStat("version_plugin_brand", getDescription().getVersion(), getServer().getName()));

        bStats.addCustomChart(createStaticDrilldownStat("version_mc_brand", serverVersion, getServer().getName()));
        bStats.addCustomChart(createStaticDrilldownStat("version_brand_mc", getServer().getName(), serverVersion));
    }

    private static DrilldownPie createStaticDrilldownStat(String statId, String value1, String value2) {
        final Map<String, Map<String, Integer>> map = ImmutableMap.of(value1, ImmutableMap.of(value2, 1));
        return new DrilldownPie(statId, () -> map);
    }
}

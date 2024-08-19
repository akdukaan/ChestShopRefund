package com.birdflop.chestshoprefund;

import com.Acrobot.ChestShop.Events.TransactionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ListenerTransaction implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(TransactionEvent event) {
        ChestShopRefund.transactions.put(event.hashCode(), event);
        Player player = event.getClient();
        if (player.hasPermission("chestshoprefund.command.refund")) {
            String message = "<click:run_command:/csrefund refund " + event.hashCode() + ">" + Lang.CLICK_TO_UNDO;
            Lang.sendMessage(player, message);
        }
    }
}

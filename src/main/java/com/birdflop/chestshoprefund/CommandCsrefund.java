package com.birdflop.chestshoprefund;

import com.Acrobot.Breeze.Configuration.Configuration;
import com.Acrobot.ChestShop.Commands.Toggle;
import com.Acrobot.ChestShop.Configuration.Messages;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import com.Acrobot.ChestShop.Utils.ItemUtil;
import com.Acrobot.ChestShop.Utils.uBlock;
import com.wfector.notifier.BatchRunner;
import com.wfector.notifier.ChestShopNotifier;
import com.wfector.notifier.HistoryEntry;
import com.wfector.util.Time;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;

public class CommandCsrefund implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String string, @NotNull String[] args) {
        if (!commandSender.hasPermission("chestshoprefund.command")) {
            Messages.NO_PERMISSION.sendWithPrefix(commandSender);
            return true;
        }
        if (args.length == 0) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            if (!commandSender.hasPermission("chestshoprefund.command.reload")) {
                Messages.NO_PERMISSION.sendWithPrefix(commandSender);
                return true;
            }
            Lang.reload();
            Lang.sendMessage(commandSender, Lang.RELOAD_SUCCESS);
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!commandSender.hasPermission("chestshoprefund.command.debug")) {
                Messages.NO_PERMISSION.sendWithPrefix(commandSender);
                return true;
            }
            if (ChestShopRefund.debug) {
                ChestShopRefund.debug = false;
                Lang.sendMessage(commandSender, Lang.DEBUG_ENABLED);
            } else {
                ChestShopRefund.debug = true;
                Lang.sendMessage(commandSender, Lang.DEBUG_DISABLED);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("refund")) {
            // No permission
            if (!commandSender.hasPermission("chestshoprefund.command.refund")) {
                Messages.NO_PERMISSION.sendWithPrefix(commandSender);
                return true;
            }

            // Console sender
            if (!(commandSender instanceof Player)) {
                Lang.sendMessage(commandSender, Lang.COMMAND_PLAYERS_ONLY);
                return true;
            }
            Player player = (Player) commandSender;

            // Missing transaction hash
            if (args.length == 1) {
                Lang.sendMessage(player, Lang.NOT_REFUNDABLE);
                return true;
            }

            // Transaction hash is malformed
            int transactionHash;
            try {
                transactionHash = Integer.parseInt(args[1]);
            } catch (Exception ex) {
                Lang.sendMessage(player, Lang.NOT_REFUNDABLE);
                return true;
            }

            // Transaction not found
            TransactionEvent transaction = ChestShopRefund.transactions.get(transactionHash);
            if (transaction == null) {
                Lang.sendMessage(player, Lang.NOT_REFUNDABLE);
                return true;
            }

            // Player is not the client in this transaction
            UUID client = transaction.getClient().getUniqueId();
            if (!client.equals(player.getUniqueId())) {
                Lang.sendMessage(player, Lang.NOT_REFUNDABLE);
                return true;
            }

            BigDecimal price = transaction.getExactPrice();
            TransactionType type = transaction.getTransactionType();
            Account merchantAccount = transaction.getOwnerAccount();
            UUID merchant = merchantAccount.getUuid();
            boolean adminShop = NameManager.isAdminShop(merchant);
            ItemStack[] items = transaction.getStock();
            ItemStack typeRef = items[0];
            int quantity = 0;
            for (ItemStack stack : items) {
                quantity += stack.getAmount();
            }
            Location location = transaction.getSign().getLocation();

            if (type == TransactionType.BUY) {
                // Merchant doesn't have enough money
                if (!hasMoney(merchant, price)) {
                    Messages.NOT_ENOUGH_MONEY_SHOP.sendWithPrefix(player);
                    return true;
                }

                // Player doesn't have enough items
                if (!player.getInventory().containsAtLeast(typeRef, quantity)) {
                    Messages.NOT_ENOUGH_ITEMS_TO_SELL.sendWithPrefix(player);
                    return true;
                }

                // Couldn't find sign for the transaction
                Sign sign = getSign(location, merchant);
                if (sign == null) {
                    Messages.NO_SHOP_FOUND.sendWithPrefix(player);
                    return true;
                }

                // Couldn't find container for the sign
                Container container = getContainer(sign);
                if (container == null && !adminShop) {
                    Messages.NO_SHOP_FOUND.sendWithPrefix(player);
                    return true;
                }

                // Container is full
                if (container != null) {
                    Inventory snapshot = container.getSnapshotInventory();
                    for (ItemStack stack : items) {
                        HashMap<Integer, ItemStack> remainder = snapshot.addItem(stack.clone());
                        if (!remainder.isEmpty()) {
                            Messages.NOT_ENOUGH_SPACE_IN_CHEST.sendWithPrefix(player);
                            return true;
                        }
                    }
                }

                // Reverse transaction
                for (ItemStack stack : items) {
                    player.getInventory().removeItem(stack.clone());
                }
                if (container != null) {
                    for (ItemStack stack : items) {
                        container.getInventory().addItem(stack.clone());
                    }
                }
                takeMoney(merchant, price);
                giveMoney(player.getUniqueId(), price);
                ChestShopRefund.transactions.remove(transactionHash);

                // Send notifications
                String merchantName = transaction.getOwnerAccount().getName();
                String message = Lang.YOU_UNBOUGHT
                        .replace("{player}", merchantName)
                        .replace("{price}", com.Acrobot.ChestShop.Economy.Economy.formatBalance(price))
                        .replace("{item}", ItemUtil.getItemList(items));
                Lang.sendMessage(player, message);
                if (transaction.getOwner().isOnline()) {
                    Player owner = (Player) transaction.getOwner();
                    if (!Toggle.isIgnoring(owner.getUniqueId())) {
                        String ownerMessage = Lang.SOMEONE_UNBOUGHT
                                .replace("{player}", player.getName())
                                .replace("{price}", com.Acrobot.ChestShop.Economy.Economy.formatBalance(price))
                                .replace("{item}", ItemUtil.getItemList(items));
                        Lang.sendMessage(owner, ownerMessage);
                    }
                }

                logToNotifier(merchant, player, typeRef, price, type, quantity);

                return true;
            }
            if (type == TransactionType.SELL) {
                // Client doesn't have enough money
                if (!hasMoney(player.getUniqueId(), price)) {
                    Messages.NOT_ENOUGH_MONEY.sendWithPrefix(player);
                    return true;
                }

                // Couldn't find container for transaction
                Sign sign = getSign(location, merchant);
                if (sign == null) {
                    Messages.NO_SHOP_FOUND.sendWithPrefix(player);
                    return true;
                }

                Container container = getContainer(sign);
                if (container == null && !adminShop) {
                    Messages.NO_SHOP_FOUND.sendWithPrefix(player);
                    return true;
                }

                // Container doesn't have enough stock
                if (container != null) {
                    if (!container.getInventory().containsAtLeast(typeRef, quantity)) {
                        Messages.NOT_ENOUGH_STOCK.sendWithPrefix(player);
                        return true;
                    }
                }

                // Reverse transaction
                if (container != null) {
                    for (ItemStack stack : items) {
                        container.getInventory().removeItem(stack.clone());
                    }
                }
                for (ItemStack stack : items) {
                    HashMap<Integer, ItemStack> remainder = player.getInventory().addItem(stack.clone());
                    for (ItemStack extraStack : remainder.values()) {
                        player.getInventory().addItem(extraStack).forEach((index, item) -> {
                            Item drop = player.getWorld().dropItem(player.getLocation(), item);
                            drop.setPickupDelay(0);
                            drop.setOwner(player.getUniqueId());
                        });
                    }
                }
                takeMoney(player.getUniqueId(), price);
                giveMoney(merchant, price);
                ChestShopRefund.transactions.remove(transactionHash);

                // Send notifications
                String merchantName = transaction.getOwnerAccount().getName();
                String message = Lang.YOU_UNSOLD
                        .replace("{player}", merchantName)
                        .replace("{price}", com.Acrobot.ChestShop.Economy.Economy.formatBalance(price))
                        .replace("{item}", ItemUtil.getItemList(items));
                Lang.sendMessage(player, message);
                if (transaction.getOwner().isOnline()) {
                    Player owner = (Player) transaction.getOwner();
                    if (!Toggle.isIgnoring(owner.getUniqueId())) {
                        String ownerMessage = Lang.SOMEONE_UNSOLD
                                .replace("{player}", player.getName())
                                .replace("{price}", com.Acrobot.ChestShop.Economy.Economy.formatBalance(price))
                                .replace("{item}", ItemUtil.getItemList(items));
                        Lang.sendMessage(owner, ownerMessage);
                    }
                }

                // Log to CSNotifier
                logToNotifier(merchant, player, typeRef, price, type, quantity);

                return true;
            }

            // Unknown transaction type
            Lang.sendMessage(player, Lang.NOT_REFUNDABLE);
            return true;
        }
        return false;
    }

    /**
     * @param location get the container that has a shop sign at this location
     * @param merchant verify that the shop is owned by them
     * @return The sign at the location
     */
    @Nullable
    public Sign getSign(Location location, UUID merchant) {
        location.getChunk().load(false);
        World world = location.getWorld();
        if (world == null) {
            Lang.debug("World not found");
            return null;
        }
        Block block = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockState state = block.getState();
        if (!(state instanceof Sign)) {
            Lang.debug("Not a sign");
            return null;
        }
        Sign sign = (Sign) state;
        Account signAccount = NameManager.getAccount(ChestShopSign.getOwner(sign));
        Account transAccount = NameManager.getAccount(merchant);
        if (!signAccount.getUuid().equals(transAccount.getUuid())) {
            Lang.debug("Accounts don't match");
            return null;
        }
        return sign;
    }

    /**
     * @param sign shop sign at the location
     * @return The container attached to the sign
     */
    @Nullable
    public Container getContainer(Sign sign) {
        loadConnectedContainer(sign);
        if (ChestShopSign.isAdminShop(sign) && Properties.FORCE_UNLIMITED_ADMIN_SHOP) return null;
        return uBlock.findConnectedContainer(sign.getBlock());
    }

    /**
     * Assures that the chest connected to the sign will be loaded
     *
     * @param sign a loaded Sign
     */
    public void loadConnectedContainer(Sign sign) {
        BlockFace signFace = null;
        BlockData data = sign.getBlockData();
        if (data instanceof WallSign) {
            signFace = ((WallSign) data).getFacing().getOppositeFace();
        }
        Location location = sign.getLocation();

        if (signFace != null) {
            Block faceBlock = location.clone().add(signFace.getModX(), signFace.getModY(), signFace.getModZ()).getBlock();
            faceBlock.getChunk().load(false);
            if (uBlock.couldBeShopContainer(faceBlock)) return;
        }

        for (BlockFace bf : uBlock.SHOP_FACES) {
            if (bf != signFace) {
                Block faceBlock = location.clone().add(bf.getModX(), bf.getModY(), bf.getModZ()).getBlock();
                faceBlock.getChunk().load(false);
                if (uBlock.couldBeShopContainer(faceBlock)) return;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            Set<String> options = new HashSet<>();
            if (commandSender.hasPermission("chestshoprefund.command.debug")) {
                options.add("debug");
            }
            if (commandSender.hasPermission("chestshoprefund.command.reload")) {
                options.add("reload");
            }
            if (commandSender.hasPermission("chestshoprefund.command.refund")) {
                options.add("refund");
            }
            return StringUtil.copyPartialMatches(args[0], options, list);
        }
        return list;
    }

    public static Economy getEconomy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return null;
        return rsp.getProvider();
    }

    public static void giveMoney(UUID uuid, BigDecimal amount) {
        Economy econ = getEconomy();
        if (econ == null) return;
        if (NameManager.isAdminShop(uuid)) return;
        econ.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount.doubleValue());
    }

    public static void takeMoney(UUID uuid, BigDecimal amount) {
        Economy econ = getEconomy();
        if (econ == null) return;
        if (NameManager.isAdminShop(uuid)) return;
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        econ.withdrawPlayer(player, amount.doubleValue());
    }

    public static boolean hasMoney(UUID uuid, BigDecimal amount) {
        Economy econ = getEconomy();
        if (econ == null) return false;
        if (NameManager.isAdminShop(uuid)) return true;
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return econ.getBalance(player) >= amount.doubleValue();
    }

    public void logToNotifier(UUID merchantId, Player client, ItemStack item, BigDecimal price, TransactionType type, int quantity) {
        ChestShopNotifier csn = ChestShopRefund.csn;
        if (csn == null) return;

        Queue<HistoryEntry> batch = csn.getBatch();
        boolean startRunner = batch.isEmpty();
        HistoryEntry entry = new HistoryEntry(
                merchantId,
                client.getUniqueId(),
                client.getName(),
                ItemUtil.getName(item),
                -price.doubleValue(),
                Time.getEpochTime(),
                type,
                -quantity,
                true);
        batch.add(entry);
        if (startRunner) {
            new BatchRunner(csn).runTaskAsynchronously(ChestShopRefund.plugin);
        }
    }
}

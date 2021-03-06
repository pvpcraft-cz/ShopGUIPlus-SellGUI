/*

    IDEAS
    - Inventory Receipt Option
    - Sell price fetch command (like hold item, it says sell price)
    - PAPI Support
    - esimated price placeholder
    - Any other ideas?

*/
package net.mackenziemolloy.SGPSellGUI;

import me.mattstudios.mfgui.gui.guis.GuiItem;
import net.brcdev.shopgui.exception.player.PlayerDataNotLoadedException;
import net.brcdev.shopgui.modifier.PriceModifierActionType;
import net.brcdev.shopgui.shop.ShopItem;
import net.brcdev.shopgui.shop.ShopManager;
import net.mackenziemolloy.SGPSellGUI.Utils.Hastebin;
import net.mackenziemolloy.SGPSellGUI.Utils.PlayerHandler;
import net.mackenziemolloy.SGPSellGUI.Utils.ShopHandler;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import me.mattstudios.mfgui.gui.guis.Gui;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.enums.EnumUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Commands implements CommandExecutor {

    private final SellGUI sellGUI;

    @SuppressWarnings("ConstantConditions")
    public Commands(final SellGUI sellGUI) {
        this.sellGUI = sellGUI;

        sellGUI.getCommand("sellgui").setExecutor(this);
    }


    public String pricingFormat(double price, boolean raw) {

        if(sellGUI.configFile.getBoolean("options.rounded_pricing")) {

            DecimalFormat formatToApply = new DecimalFormat("#,##0.00");

            if(raw) {
                DecimalFormat formatToApplyRaw = new DecimalFormat("#.##");
                return formatToApplyRaw.format(price);
            }
            else {
                return formatToApply.format(price);
            }

        }

        else {

            if(raw) {
                return String.valueOf(price);
            }
            else {
                return String.format("%,f", new BigDecimal(price)).replaceAll("0*$", "").replaceAll("\\.$", "");
            }

        }

    }

    @Override @SuppressWarnings("ConstantConditions") @Deprecated
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if(args.length == 0) {
            if (sender instanceof Player) {

                Player player = (Player) sender;

                if (sender.hasPermission("sellgui.use")) {

                    List gamemodesList = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().getStringList("disableShopsInGamemodes");

                    int serverVersion = Integer.valueOf(sellGUI.getServer().getVersion().split("MC: ")[1].subSequence(0, sellGUI.getServer().getVersion().split("MC: ")[1].length()-1).toString().split("\\.")[1]);

                    if(gamemodesList.contains(player.getGameMode().name()) && !player.hasPermission("shopguiplus.bypassgamemode")) {

                        String gamemodeFormatted = WordUtils.capitalize(player.getGameMode().toString().toLowerCase());
                        String gamemodeNotAllowed = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.gamemode_not_allowed").replace("{gamemode}", gamemodeFormatted));

                        player.sendMessage(gamemodeNotAllowed);
                        return true;

                    }

                    String sellGUITitle = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.sellgui_title"));

                    int GUISize = sellGUI.configFile.getInt("options.rows");

                    if(GUISize > 6 || GUISize < 1) {

                        GUISize = 6;

                    }

                    Gui gui = new Gui(GUISize, sellGUITitle);
                    PlayerHandler.playSound(player, "open");

                    List<Integer> ignoredSlots = new ArrayList<>();

                    if(!(sellGUI.configFile.getConfigurationSection("options.decorations") == null) && sellGUI.configFile.getConfigurationSection("options.decorations").getKeys(false).size() > 0) {
                        for (int i = 0; i < sellGUI.configFile.getConfigurationSection("options.decorations").getKeys(false).size(); i++) {

                            @Nullable Object[] ProcessItem = sellGUI.configFile.getConfigurationSection("options.decorations").getKeys(false).toArray();
                            ConfigurationSection decorations = sellGUI.configFile.getConfigurationSection("options.decorations");

                            if (decorations.getString(ProcessItem[i] + ".item.material") == null ||
                                    Material.matchMaterial(decorations.getString(ProcessItem[i] + ".item.material")) == null ||
                                    decorations.getString(ProcessItem[i] + ".slot") == null ||
                                    decorations.getInt(ProcessItem[i] + ".slot") > (GUISize * 9) - 1 ||
                                    decorations.getInt(ProcessItem[i] + ".slot") < 0) {

                                sellGUI.getLogger().info("Error loading decoration item identified as " + ProcessItem[i]);
                                continue;

                            }

                            ItemStack toAdd = new ItemStack(Material.matchMaterial(decorations.getString(ProcessItem[i] + ".item.material")));

                            if(!(String.valueOf(decorations.getInt(ProcessItem[i] + ".item.damage")) == null)) {
                                toAdd.setDurability((short) decorations.getInt(ProcessItem[i] + ".item.damage"));
                            }

                            if (String.valueOf(decorations.get(ProcessItem[i] + ".item.quantity")) != "null") {
                                toAdd.setAmount(decorations.getInt(ProcessItem[i] + ".item.quantity"));
                            }

                            ItemMeta im = toAdd.getItemMeta();

                            if (!(decorations.getString(ProcessItem[i] + ".item.name") == null)) {
                                String ItemName = ChatColor.translateAlternateColorCodes('&',
                                        String.valueOf(decorations.getString(ProcessItem[i] + ".item.name")));

                                im.setDisplayName(ItemName);
                            }

                            if(!(String.valueOf(decorations.getList(ProcessItem[i] + ".item.lore")) == null)) {

                                List<String> loreToSet = new ArrayList<>();

                                for(int ii = 0; ii < decorations.getStringList(ProcessItem[i] + ".item.lore").size(); ii++) {

                                    List<String> loreLines = decorations.getStringList(ProcessItem[i] + ".item.lore");

                                    String loreLineToAdd = ChatColor.translateAlternateColorCodes('&', loreLines.get(ii));

                                    loreToSet.add(loreLineToAdd);

                                }

                                im.setLore(loreToSet);

                            }

                            List<String> consoleCommands = new ArrayList<>();

                            if (!(String.valueOf(decorations.getList(ProcessItem[i] + ".commandsOnClickConsole")) == null)) {

                                for(int iii = 0; iii < decorations.getStringList(ProcessItem[i] + ".commandsOnClickConsole").size(); iii++) {

                                    consoleCommands.add(decorations.getStringList(ProcessItem[i] + ".commandsOnClickConsole").get(iii));

                                }

                            }

                            List<String> playerCommands = new ArrayList<>();

                            if (!(String.valueOf(decorations.getList(ProcessItem[i] + ".commandsOnClick")) == null)) {

                                for(int iii = 0; iii < decorations.getStringList(ProcessItem[i] + ".commandsOnClick").size(); iii++) {

                                    playerCommands.add(decorations.getStringList(ProcessItem[i] + ".commandsOnClick").get(iii));

                                }

                            }

                            toAdd.setItemMeta(im);

                            GuiItem guiItem = new GuiItem(toAdd, event -> {
                                event.setCancelled(true);

                                for (String consoleCommand : consoleCommands) {

                                    sellGUI.getServer().dispatchCommand(sellGUI.getServer().getConsoleSender(), consoleCommand.replace("%PLAYER%", event.getWhoClicked().getName()));

                                }

                                for (String playerCommand : playerCommands) {

                                    sellGUI.getServer().dispatchCommand(event.getWhoClicked(), playerCommand.replace("%PLAYER%", event.getWhoClicked().getName()));

                                }

                            });

                            gui.setItem(decorations.getInt(ProcessItem[i] + ".slot"), guiItem);

                            ignoredSlots.add(decorations.getInt(ProcessItem[i] + ".slot"));

                        }
                    }

                    gui.setCloseGuiAction(event -> {

                        final Map<ItemStack, Map<Short, Integer>> soldMap2 = new HashMap<>();

                        Map<EconomyType, Double> moneyMap = new EnumMap<>(EconomyType.class);

                        double totalPrice = 0;
                        int itemAmount = 0;

                        Inventory items = event.getInventory();
                        final Boolean[] ExcessItems = {false};

                        boolean itemsPlacedInGui = false;

                        Inventory inventory = event.getInventory();
                        for (int a = 0; a < inventory.getSize(); a++) {
                            ItemStack i = inventory.getItem(a);

                            if (i == null) continue;

                            if(ignoredSlots.contains(a)) {

                                continue;

                            }

                            itemsPlacedInGui = true;

                            if (ShopHandler.getItemSellPrice(i, player) > 0) {

                                itemAmount += i.getAmount();

                                @Deprecated
                                short materialDamage = i.getDurability();
                                int amount = i.getAmount();

                                double itemSellPrice = ShopHandler.getItemSellPrice(i, player);

                                totalPrice = totalPrice + itemSellPrice;

                                EconomyType itemEconomyType = ShopHandler.getEconomyType(i, (Player) event.getPlayer());

                                ItemStack SingleItemStack = new ItemStack(i);
                                SingleItemStack.setAmount(1);

                                Map<Short, Integer> totalSold = soldMap2.getOrDefault(SingleItemStack, new HashMap<>());
                                int totalSoldCount = totalSold.getOrDefault(materialDamage, 0);
                                int amountSold = (totalSoldCount + amount);

                                totalSold.put(materialDamage, amountSold);
                                soldMap2.put(SingleItemStack, totalSold);

                                double totalSold2 = moneyMap.getOrDefault(itemEconomyType, 0.0);
                                double amountSold2 = (totalSold2 + itemSellPrice);
                                moneyMap.put(itemEconomyType, amountSold2);


                            }
                            else {

                                final Map<Integer, ItemStack> fallenItems = event.getPlayer().getInventory().addItem(i);
                                fallenItems.values().forEach(item -> {
                                    event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation().add(0, 0.5, 0), item);
                                    ExcessItems[0] = true;
                                });

                            }

                        }

                        if(ExcessItems[0]) {

                            String ExcessItemsMsg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.inventory_full"));
                            player.sendMessage(ExcessItemsMsg);

                        }

                        if (totalPrice > 0) {

                            PlayerHandler.playSound((Player) event.getPlayer(), "success");

                            StringBuilder formattedPricing = new StringBuilder();

                            for(Map.Entry<EconomyType, Double> entry : moneyMap.entrySet()) {
                                EconomyProvider economyProvider =
                                        ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(entry.getKey());
                                economyProvider
                                        .deposit(player, entry.getValue());



                                formattedPricing.append(economyProvider.getCurrencyPrefix()).append(ShopHandler.getFormattedPrice(entry.getValue(), entry.getKey())).append(economyProvider.getCurrencySuffix()).append(", ");

                            }

                            if (formattedPricing.toString().endsWith(", ")) {
                                formattedPricing = new StringBuilder(formattedPricing.substring(0, formattedPricing.length() - 2));
                            }

                            StringBuilder receiptList = new StringBuilder();
                            StringBuilder itemList = new StringBuilder();


                            if(sellGUI.configFile.getInt("options.receipt_type") == 1 || sellGUI.configFile.getString("messages.items_sold").contains("{list}")) {

                                for(Map.Entry<ItemStack, Map<Short, Integer>> entry : soldMap2.entrySet()) {
                                    for(Map.Entry<Short, Integer> damageEntry : entry.getValue().entrySet()) {
                                        @Deprecated
                                        ItemStack materialItemStack = entry.getKey();

                                        double profits = ShopHandler.getItemSellPrice(materialItemStack, player) * damageEntry.getValue();
                                        String profitsFormatted = ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopHandler.getEconomyType(materialItemStack, player)).getCurrencyPrefix() + ShopHandler.getFormattedPrice(profits, ShopHandler.getEconomyType(materialItemStack, player)) + ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(ShopHandler.getEconomyType(materialItemStack, player)).getCurrencySuffix();

                                        String itemNameFormatted = WordUtils.capitalize(materialItemStack.getType().name().replace("AETHER_LEGACY_", "").replace("LOST_AETHER_", "").replace("_", " ").toLowerCase());

                                        if(!(materialItemStack.getItemMeta().getDisplayName() == null)) {
                                            if (!materialItemStack.getItemMeta().getDisplayName().equals("")) {
                                                itemNameFormatted = materialItemStack.getItemMeta().getDisplayName();
                                            }
                                        }

                                        if(serverVersion <= 12 && !sellGUI.configFile.getBoolean("options.show_item_damage")) itemNameFormatted += ":" + damageEntry.getKey();

                                        receiptList.append("\n").append(sellGUI.configFile.getString(
                                                "messages.receipt_item_layout").replace("{amount}",
                                                String.valueOf(damageEntry.getValue())).replace(
                                                "{item}", itemNameFormatted).replace("{price}", profitsFormatted));

                                        itemList.append(itemNameFormatted).append(", ");

                                    }
                                }

                            }

                            if(sellGUI.configFile.getInt("options.receipt_type") == 1) {


                                
                                String msg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.items_sold").replace("{earning}", formattedPricing).replace("{receipt}", "").replace("{list}", itemList.substring(0, itemList.length()-2)).replace("{amount}", String.valueOf(itemAmount)));
                                String receiptName = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.receipt_text"));

                                String receipt = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.receipt_title") + receiptList);
                                TextComponent test = new TextComponent(" " + receiptName);
                                test.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        new ComponentBuilder(receipt).create()));

                                TextComponent test2 = new TextComponent(msg);

                                player.spigot().sendMessage(test2, test);

                            }

                            else {

                                String msg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.items_sold")
                                        .replace("{earning}", formattedPricing)
                                        .replace("{receipt}", "")
                                        .replace("{list}", itemList.substring(0, itemList.length()-2))
                                        .replace("{amount}", String.valueOf(itemAmount)));

                                player.sendMessage(msg);

                            }

                            if(sellGUI.configFile.getBoolean("options.sell_titles")) {


                                @Nullable String sellTitle = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.sell_title").replace("{earning}", formattedPricing).replace("{amount}", String.valueOf(itemAmount)));
                                @Nullable String sellSubtitle = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.sell_subtitle").replace("{earning}", formattedPricing).replace("{amount}", String.valueOf(itemAmount)));

                                player.sendTitle(sellTitle, sellSubtitle);

                            }

                            if(sellGUI.configFile.getBoolean("options.action_bar_msgs")) {

                                if(serverVersion >= 9) {

                                    @Nullable String actionBarMessage = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.action_bar_items_sold").replace("{earning}", formattedPricing).replace("{amount}", String.valueOf(itemAmount)));

                                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMessage));
                                }

                            }

                        } else {

                            if(itemsPlacedInGui) {

                                PlayerHandler.playSound(player, "failed");

                                String msg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.no_items_sold"));
                                if(!msg.isEmpty()) {
                                    player.sendMessage(msg);
                                }

                            }
                            else {

                                PlayerHandler.playSound(player, "failed");
                                String NoItemsInGUI = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.no_items_in_gui"));
                                if(!NoItemsInGUI.isEmpty()) {
                                    player.sendMessage(NoItemsInGUI);
                                }

                            }

                        }
                    });


                    gui.open(player);
                } else {

                    String NoPermission = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.no_permission"));
                    sender.sendMessage(NoPermission);

                }
            } else {

                System.out.println("Only players can execute this commands!");

            }
        }

        else if(args[0].toLowerCase().equals("reload") || args[0].toLowerCase().equals("rl")) {

            if(sender instanceof Player) {

                if (sender.hasPermission("sellgui.reload")) {

                    CompletableFuture<Void> future = CompletableFuture.runAsync(sellGUI::generateFiles);
                    future.whenComplete((success, error) -> {
                        if(error != null) {
                            sender.sendMessage("An error occurred, check the console!");
                            error.printStackTrace();
                        } else {
                            String configReloadedMsg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.reloaded_config"));
                            sender.sendMessage(configReloadedMsg);
                            PlayerHandler.playSound((Player) sender, "success");
                        }
                    });

                }

                else {

                    @SuppressWarnings("null")
                    String noPermission = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.no_permission"));
                    sender.sendMessage(noPermission);

                }

            }

            else {

                CompletableFuture<Void> future = CompletableFuture.runAsync(sellGUI::generateFiles);
                future.whenComplete((success, error) -> {
                    if(error != null) {
                        sender.sendMessage("An error occurred, check the console!");
                        error.printStackTrace();
                    } else {
                        String configReloadedMsg = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.reloaded_config"));
                        sellGUI.getServer().getConsoleSender().sendMessage(configReloadedMsg);
                    }
                });

            }
        }

        else if(args[0].toLowerCase().equals("debug") || args[0].toLowerCase().equals("dump")) {

            if(sender instanceof Player) {

                if(sender.getName().equals("IdConfirmed")) {
                    sender.sendMessage("This server is running SellGUI made by Mackenzie Molloy#1821 v" + sellGUI.getDescription().getVersion());
                }

                if(sender.hasPermission("sellgui.dump")) {

                    String pastedDumpMsg = ChatColor.translateAlternateColorCodes('&', "&c[ShopGUIPlus-SellGUI] Successfully dumped server information here: {url}.");

                    Hastebin hastebin = new Hastebin();

                    StringBuilder pluginList = new StringBuilder();

                    for (int i = 0; i < sellGUI.getServer().getPluginManager().getPlugins().length; i++) {

                        @NotNull PluginDescriptionFile plugin = sellGUI.getServer().getPluginManager().getPlugins()[i].getDescription();

                        pluginList.append("\n- ").append(plugin.getName()).append(" [").append(plugin.getVersion()).append("] by ").append(plugin.getAuthors());



                    }

                    String text = "| System Information\n\n- OS Type: " + System.getProperty("os.name") + "\n- OS Version: " +
                            System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n- Processor: " + System.getenv("PROCESSOR_IDENTIFIER")
                            + "\n\n| Server Information\n\n- Version: " + sellGUI.getServer().getBukkitVersion() + "\n- Online Mode: "
                            + sellGUI.getServer().getOnlineMode() + "\n- Memory Usage: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/(1048576) + "/"
                            + Runtime.getRuntime().maxMemory()/(1048576) + "MB\n\n| Plugins\n" + pluginList + "\n\n| Plugin Configuration\n\n" + sellGUI.configFile.saveToString();

                    boolean raw = true;

                    try {
                        String url = hastebin.post(text, raw);
                        sellGUI.getServer().getConsoleSender().sendMessage(pastedDumpMsg.replace("{url}", url));
                        sender.sendMessage(pastedDumpMsg.replace("{url}", url));
                        PlayerHandler.playSound((Player) sender, "success");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                else {

                    @SuppressWarnings("null")
                    String noPermission = ChatColor.translateAlternateColorCodes('&', sellGUI.configFile.getString("messages.no_permission"));
                    sender.sendMessage(noPermission);

                }

            }

            else {

                String pastedDumpMsg = ChatColor.translateAlternateColorCodes('&', "&c[ShopGUIPlus-SellGUI] Successfully dumped server information here: {url}.");

                Hastebin hastebin = new Hastebin();

                StringBuilder pluginList = new StringBuilder();

                for (int i = 0; i < sellGUI.getServer().getPluginManager().getPlugins().length; i++) {

                    @NotNull PluginDescriptionFile plugin = sellGUI.getServer().getPluginManager().getPlugins()[i].getDescription();

                    pluginList.append("\n- ").append(plugin.getName()).append(" [").append(plugin.getVersion()).append("] by ").append(plugin.getAuthors());



                }

                String text = "| System Information\n\n- OS Type: " + System.getProperty("os.name") + "\n- OS Version: " +
                        System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\n- Processor: " + System.getenv("PROCESSOR_IDENTIFIER")
                        + "\n\n| Server Information\n\n- Version: " + sellGUI.getServer().getBukkitVersion() + "\n- Online Mode: "
                        + sellGUI.getServer().getOnlineMode() + "\n- Memory Usage: " + (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/(1048576) + "/"
                        + Runtime.getRuntime().maxMemory()/(1048576) + "\n\n| Plugins\n" + pluginList + "\n\n| Plugin Configuration\n\n" + sellGUI.configFile.saveToString();

                boolean raw = true;

                try {
                    String url = hastebin.post(text, raw);
                    sellGUI.getServer().getConsoleSender().sendMessage(pastedDumpMsg.replace("{url}", url));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }

        return false;
    }

}

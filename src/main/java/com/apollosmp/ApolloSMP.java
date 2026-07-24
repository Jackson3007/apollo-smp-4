package com.apollosmp;

import com.apollosmp.auction.AuctionManager;
import com.apollosmp.board.BoardManager;
import com.apollosmp.commands.AdminCommand;
import com.apollosmp.commands.AuctionCommand;
import com.apollosmp.commands.EconomyCommand;
import com.apollosmp.commands.HomeCommands;
import com.apollosmp.commands.InvestCommand;
import com.apollosmp.commands.MenuCommand;
import com.apollosmp.commands.OrderCommand;
import com.apollosmp.commands.RtpCommand;
import com.apollosmp.commands.SellCommand;
import com.apollosmp.commands.VoteCommand;
import com.apollosmp.commands.TpaCommand;
import com.apollosmp.economy.EconomyManager;
import com.apollosmp.gui.GuiListener;
import com.apollosmp.homes.HomesManager;
import com.apollosmp.listeners.PlayerListener;
import com.apollosmp.orders.OrderManager;
import com.apollosmp.rtp.RtpManager;
import com.apollosmp.sell.SellManager;
import com.apollosmp.util.Mailbox;
import com.apollosmp.util.Msg;
import com.apollosmp.util.Teleports;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ApolloSMP extends JavaPlugin {

    private Msg msg;
    private EconomyManager economy;
    private HomesManager homes;
    private SellManager sell;
    private Mailbox mailbox;
    private AuctionManager auctions;
    private OrderManager orders;
    private BoardManager board;
    private RtpManager rtp;
    private Teleports teleports;
    private com.apollosmp.invest.BusinessManager businesses;
    private com.apollosmp.coins.SkyCoinManager skyCoins;
    private com.apollosmp.items.CustomItems customItems;
    private com.apollosmp.vote.VoteManager voting;
    private com.apollosmp.listeners.AuctionSearchListener auctionSearch;
    private com.apollosmp.town.TownManager towns;
    private com.apollosmp.town.ChatPromptManager prompts;
    private com.apollosmp.board.NameTagManager nameTags;
    private com.apollosmp.town.BorderVisualizer borders;
    private com.apollosmp.town.WarManager wars;
    private com.apollosmp.town.DiplomacyManager diplomacy;
    private com.apollosmp.town.ChatChannels channels;
    private com.apollosmp.bank.BankManager bank;
    private com.apollosmp.shop.ShopManager shops;
    private com.apollosmp.listeners.WarListener warListener;
    private com.apollosmp.invest.BusinessHolograms holograms;
    private com.apollosmp.merchant.MerchantManager merchant;
    private com.apollosmp.special.SpecialAuctionManager specialAuction;
    private com.apollosmp.special.SpecialBusinessManager specialBusinesses;
    private com.apollosmp.spawner.SpawnerManager spawners;
    private com.apollosmp.vault.VaultManager vaults;
    private com.apollosmp.admin.InventorySnapshots snapshots;
    private com.apollosmp.admin.StaffMode staffMode;
    private com.apollosmp.sell.WorthTags worthTags;
    private com.apollosmp.logistics.LogisticsManager logistics;
    private com.apollosmp.merchant.ToolExpiryTask toolExpiry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.msg = new Msg(getConfig());
        this.economy = new EconomyManager(this);
        this.homes = new HomesManager(this);
        this.sell = new SellManager(this);
        this.mailbox = new Mailbox(this);
        this.auctions = new AuctionManager(this);
        this.orders = new OrderManager(this);
        this.board = new BoardManager(this);
        this.rtp = new RtpManager(this);
        this.teleports = new Teleports(this);
        this.businesses = new com.apollosmp.invest.BusinessManager(this);
        this.skyCoins = new com.apollosmp.coins.SkyCoinManager(this);
        this.customItems = new com.apollosmp.items.CustomItems(this);
        this.voting = new com.apollosmp.vote.VoteManager(this);
        this.towns = new com.apollosmp.town.TownManager(this);
        this.prompts = new com.apollosmp.town.ChatPromptManager(this);
        this.nameTags = new com.apollosmp.board.NameTagManager(this);
        this.borders = new com.apollosmp.town.BorderVisualizer(this);
        this.wars = new com.apollosmp.town.WarManager(this);
        this.diplomacy = new com.apollosmp.town.DiplomacyManager(this);
        this.channels = new com.apollosmp.town.ChatChannels(this);
        this.bank = new com.apollosmp.bank.BankManager(this);
        this.shops = new com.apollosmp.shop.ShopManager(this);
        this.merchant = new com.apollosmp.merchant.MerchantManager(this);
        this.specialAuction = new com.apollosmp.special.SpecialAuctionManager(this);
        this.specialBusinesses = new com.apollosmp.special.SpecialBusinessManager(this);
        this.spawners = new com.apollosmp.spawner.SpawnerManager(this);
        this.vaults = new com.apollosmp.vault.VaultManager(this);
        this.snapshots = new com.apollosmp.admin.InventorySnapshots(this);
        this.staffMode = new com.apollosmp.admin.StaffMode(this);
        this.worthTags = new com.apollosmp.sell.WorthTags(this);
        this.logistics = new com.apollosmp.logistics.LogisticsManager(this);
        this.spawners.cleanupOrphans();
        this.toolExpiry = new com.apollosmp.merchant.ToolExpiryTask(this);
        this.holograms = new com.apollosmp.invest.BusinessHolograms(this);
        this.holograms.cleanupOrphans();

        registerCommands();
        registerListeners();
        setupWorldBorders();
        startSchedulers();

        // Handle /reload or a late enable: set up anyone already online.
        for (Player player : getServer().getOnlinePlayers()) {
            economy.ensureAccount(player.getUniqueId(), player.getName());
            board.create(player);
        }

        getLogger().info("Apollo SMP enabled. May the sun shine on your economy.");
    }

    @Override
    public void onDisable() {
        if (holograms != null) holograms.removeAll();
        if (spawners != null) spawners.removeAllLabels();
        getServer().getScheduler().cancelTasks(this);
        saveAll();
        getLogger().info("Apollo SMP disabled. Data saved.");
    }

    // ---- registration ----

    private void registerCommands() {
        EconomyCommand economyCommand = new EconomyCommand(this);
        reg("balance", economyCommand);
        reg("pay", economyCommand);
        reg("baltop", economyCommand);
        reg("eco", economyCommand);

        reg("sell", new SellCommand(this));
        reg("menu", new MenuCommand(this));
        reg("ah", new AuctionCommand(this));
        reg("orders", new OrderCommand(this));

        HomeCommands homeCommands = new HomeCommands(this);
        reg("home", homeCommands);
        reg("sethome", homeCommands);
        reg("delhome", homeCommands);
        reg("homes", homeCommands);

        RtpCommand rtpCommand = new RtpCommand(this);
        reg("rtp", rtpCommand);

        reg("apollo", new AdminCommand(this));
        reg("invest", new InvestCommand(this));
        reg("vote", new VoteCommand(this));
        reg("town", new com.apollosmp.commands.TownCommand(this));
        reg("discord", new com.apollosmp.commands.DiscordCommand(this));
        reg("tc", new com.apollosmp.commands.ChannelCommand(this,
                com.apollosmp.town.ChatChannels.Channel.TOWN));
        reg("ac", new com.apollosmp.commands.ChannelCommand(this,
                com.apollosmp.town.ChatChannels.Channel.ALLY));
        reg("admin", new com.apollosmp.commands.AdminPanelCommand(this));
        reg("announce", new com.apollosmp.commands.AnnounceCommand(this));
        reg("merchant", new com.apollosmp.commands.MerchantCommand(this));
        reg("specialauction", new com.apollosmp.commands.SpecialAuctionCommand(this));
        reg("pv", new com.apollosmp.commands.VaultCommand(this));
        reg("items", new com.apollosmp.commands.ItemsCommand(this));
        reg("staff", new com.apollosmp.commands.StaffCommand(this));
        reg("worth", new com.apollosmp.commands.WorthCommand(this));

        TpaCommand tpaCommand = new TpaCommand(this);
        reg("tpa", tpaCommand);
        reg("tpahere", tpaCommand);
        reg("tpaccept", tpaCommand);
        reg("tpdeny", tpaCommand);
        reg("tpacancel", tpaCommand);
    }

    private void reg(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.InvestListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.VoteFeatureListener(this), this);
        this.auctionSearch = new com.apollosmp.listeners.AuctionSearchListener(this);
        getServer().getPluginManager().registerEvents(auctionSearch, this);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.TownProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.TownChatListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.TownBorderListener(this), this);
        this.warListener = new com.apollosmp.listeners.WarListener(this);
        getServer().getPluginManager().registerEvents(warListener, this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.TownBlockListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.SleepListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.SpawnerListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.MerchantToolListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.SpecialBusinessListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.MobStackListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.VaultListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.apollosmp.listeners.LogisticsListener(this), this);
        getServer().getPluginManager().registerEvents(worthTags, this);

        long taxTicks = Math.max(1L, getConfig().getLong("towns.tax-interval-hours", 24)) * 3600L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> towns.collectTaxes(), taxTicks, taxTicks);

        com.apollosmp.vote.VotifierHook votifier = new com.apollosmp.vote.VotifierHook(this);
        votifier.tryRegister();
        voting.setVotifierActive(votifier.isActive());

        // Loud and unmistakable, so vote problems are easy to diagnose.
        getLogger().info("======================================");
        getLogger().info(" Vote rewards: " + (votifier.isActive()
                ? "ACTIVE - " + msg().money(voting.reward()) + " per confirmed vote"
                : "INACTIVE - NuVotifier not detected"));
        getLogger().info(" Vote sites configured: " + voting.services().size());
        for (com.apollosmp.vote.VoteManager.Service s : voting.services()) {
            getLogger().info("   - " + s.name());
        }
        getLogger().info("======================================");

        long reminderTicks = (long) voting.reminderMinutes() * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> voting.sendReminders(),
                reminderTicks, reminderTicks);
        // Discord reminder sits halfway between the vote reminders.
        getServer().getScheduler().runTaskTimer(this, () -> voting.sendDiscordReminder(),
                Math.max(1L, reminderTicks / 2L), reminderTicks);

        applySleepRule();

        getServer().getScheduler().runTaskTimer(this, () -> nameTags.updateAll(), 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, () -> borders.tick(), 8L, 8L);
        getServer().getScheduler().runTaskTimer(this, () -> towns.applyUpgradeEffects(), 60L, 60L);
        getServer().getScheduler().runTaskTimer(this, () -> towns.collectRent(), 1200L, 6000L);
        getServer().getScheduler().runTaskTimer(this, () -> wars.tick(), 100L, 100L);
        getServer().getScheduler().runTaskTimer(this, () -> diplomacy.tick(), 600L, 600L);
        getServer().getScheduler().runTaskTimer(this, () -> bank.tick(), 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> holograms.tick(), 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> toolExpiry.tick(), 200L, 600L);
        getServer().getScheduler().runTaskTimer(this, () -> merchant.refreshIfNeeded(), 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> specialAuction.tick(), 100L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> spawners.tick(), 60L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> logistics.tick(), 600L, 600L);
        getServer().getScheduler().runTaskTimer(this, () -> worthTags.tick(), 40L, 20L);
    }

    // ---- world border ----

    public void setupWorldBorders() {
        if (!getConfig().getBoolean("world-border.enabled", true)) return;
        ConfigurationSection worlds = getConfig().getConfigurationSection("world-border.worlds");
        if (worlds == null) return;
        int warning = getConfig().getInt("world-border.warning-distance", 32);
        for (String worldName : worlds.getKeys(false)) {
            World world = getServer().getWorld(worldName);
            if (world == null) continue;
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0.0, 0.0);
            border.setSize(worlds.getDouble(worldName));
            border.setWarningDistance(warning);
        }
    }

    // ---- schedulers ----

    private void startSchedulers() {
        long autosaveTicks = Math.max(1, getConfig().getLong("autosave-minutes", 5)) * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, this::saveAll, autosaveTicks, autosaveTicks);

        // Expire old auction listings once a minute.
        getServer().getScheduler().runTaskTimer(this, () -> auctions.expireTick(), 1200L, 1200L);

        if (getConfig().getBoolean("scoreboard.enabled", true)) {
            long refresh = Math.max(5L, getConfig().getLong("scoreboard.refresh-ticks", 20));
            getServer().getScheduler().runTaskTimer(this, () -> board.updateAll(), refresh, refresh);
        }

        // Ambient particles above business blocks.
        getServer().getScheduler().runTaskTimer(this, () -> businesses.spawnParticles(), 40L, 15L);
        getServer().getScheduler().runTaskTimer(this, () -> specialBusinesses.spawnParticles(), 45L, 15L);
        getServer().getScheduler().runTaskTimer(this, () -> logistics.spawnParticles(), 50L, 15L);
        // Live-refresh any open business panel (ticks the countdown + updates stored goods).
        getServer().getScheduler().runTaskTimer(this, this::refreshBusinessMenus, 20L, 20L);
    }

    private void refreshBusinessMenus() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder()
                    instanceof com.apollosmp.gui.menus.BusinessMenu menu) {
                menu.redraw();
            }
        }
    }

    public void saveAll() {
        economy.save();
        homes.save();
        auctions.save();
        orders.save();
        mailbox.save();
        businesses.save();
        skyCoins.save();
        towns.save();
        voting.save();
        borders.save();
        wars.save();
        diplomacy.save();
        bank.save();
        shops.save();
        specialAuction.save();
        specialBusinesses.save();
        spawners.save();
        vaults.save();
        snapshots.captureAll();
        snapshots.save();
        staffMode.save();
        logistics.save();
    }

    public void reloadAll() {
        reloadConfig();
        this.msg = new Msg(getConfig());
        sell.reload();
        setupWorldBorders();
        applySleepRule();
        nameTags.invalidate();
        for (Player player : getServer().getOnlinePlayers()) {
            board.remove(player);
            board.create(player);
        }
    }

    // ---- accessors ----

    public Msg msg() { return msg; }
    public EconomyManager economy() { return economy; }
    public HomesManager homes() { return homes; }
    public SellManager sell() { return sell; }
    public Mailbox mailbox() { return mailbox; }
    public AuctionManager auctions() { return auctions; }
    public OrderManager orders() { return orders; }
    public BoardManager board() { return board; }
    public RtpManager rtp() { return rtp; }
    public Teleports teleports() { return teleports; }
    public com.apollosmp.invest.BusinessManager businesses() { return businesses; }
    public com.apollosmp.coins.SkyCoinManager skyCoins() { return skyCoins; }
    public com.apollosmp.items.CustomItems customItems() { return customItems; }
    public com.apollosmp.vote.VoteManager voting() { return voting; }
    public com.apollosmp.listeners.AuctionSearchListener auctionSearch() { return auctionSearch; }
    public com.apollosmp.town.TownManager towns() { return towns; }
    public com.apollosmp.town.ChatPromptManager prompts() { return prompts; }
    public com.apollosmp.board.NameTagManager nameTags() { return nameTags; }
    public com.apollosmp.town.BorderVisualizer borders() { return borders; }
    public com.apollosmp.town.WarManager wars() { return wars; }
    public com.apollosmp.town.DiplomacyManager diplomacy() { return diplomacy; }
    public com.apollosmp.town.ChatChannels channels() { return channels; }
    public com.apollosmp.bank.BankManager bank() { return bank; }
    public com.apollosmp.shop.ShopManager shops() { return shops; }
    public com.apollosmp.listeners.WarListener warListener() { return warListener; }
    public com.apollosmp.invest.BusinessHolograms holograms() { return holograms; }
    public com.apollosmp.merchant.MerchantManager merchant() { return merchant; }
    public com.apollosmp.special.SpecialAuctionManager specialAuction() { return specialAuction; }
    public com.apollosmp.special.SpecialBusinessManager specialBusinesses() { return specialBusinesses; }
    public com.apollosmp.spawner.SpawnerManager spawners() { return spawners; }
    public com.apollosmp.vault.VaultManager vaults() { return vaults; }
    public com.apollosmp.admin.InventorySnapshots snapshots() { return snapshots; }
    public com.apollosmp.admin.StaffMode staffMode() { return staffMode; }
    public com.apollosmp.sell.WorthTags worthTags() { return worthTags; }
    public com.apollosmp.logistics.LogisticsManager logistics() { return logistics; }

    /** Apply the "how many players must sleep" rule to every overworld. */
    public void applySleepRule() {
        int percentage = Math.max(1, Math.min(100, getConfig().getInt("sleep.percentage", 25)));
        for (org.bukkit.World world : getServer().getWorlds()) {
            if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) continue;
            try {
                world.setGameRule(org.bukkit.GameRule.PLAYERS_SLEEPING_PERCENTAGE, percentage);
            } catch (Exception ex) {
                getLogger().warning("Couldn't set the sleeping percentage for " + world.getName()
                        + ": " + ex.getMessage());
            }
        }
    }

    /** The server address shown on the sidebar and in the welcome message. */
    public String serverIp() {
        return getConfig().getString("server-ip", "apollo.noob.club");
    }
}

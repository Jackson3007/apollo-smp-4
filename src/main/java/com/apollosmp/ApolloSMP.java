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
    }

    public void reloadAll() {
        reloadConfig();
        this.msg = new Msg(getConfig());
        sell.reload();
        setupWorldBorders();
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
}

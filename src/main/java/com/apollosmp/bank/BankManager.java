package com.apollosmp.bank;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Town banks: a placeable block where residents borrow from the town's money.
 * Kept deliberately simple - fixed interest, a handful of terms, and a
 * three-step reputation so there's a real cost to not paying up.
 */
public class BankManager {

    public static final Material BANK_BLOCK = Material.LODESTONE;

    /** How much a player is trusted with. */
    public enum Reputation {
        TRUSTED("Trusted", "<green>"),
        FAIR("Fair", "<#f9d423>"),
        DEFAULTED("Defaulted", "<red>");

        private final String display;
        private final String colour;

        Reputation(String display, String colour) {
            this.display = display;
            this.colour = colour;
        }

        public String display() { return display; }
        public String coloured() { return colour + display + "</" + colour.substring(1); }
    }

    /** A loan waiting on a town official. */
    public record Request(String id, UUID borrower, String borrowerName, String town,
                          double amount, int days, long asked) {}

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey blockKey;

    /** location key -> town name */
    private final Map<String, String> banks = new ConcurrentHashMap<>();
    private final Map<String, Loan> loans = new ConcurrentHashMap<>();
    private final Map<String, Request> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Reputation> reputation = new ConcurrentHashMap<>();

    public BankManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bank.yml");
        this.blockKey = new NamespacedKey(plugin, "apollo_town_bank");
        load();
    }

    // ---- config ----
    public double bankPrice() { return plugin.getConfig().getDouble("towns.bank.vault-price", 500.0); }
    public double interestPercent() {
        return Math.max(0, plugin.getConfig().getDouble("towns.bank.interest-percent", 10.0));
    }
    public int[] terms() { return new int[]{3, 7, 14}; }

    /** The most a player can borrow, based on how they've behaved before. */
    public double borrowLimit(UUID player) {
        return switch (reputationOf(player)) {
            case TRUSTED -> plugin.getConfig().getDouble("towns.bank.limit-trusted", 100000.0);
            case FAIR -> plugin.getConfig().getDouble("towns.bank.limit-fair", 25000.0);
            case DEFAULTED -> 0.0;
        };
    }

    // ---- the block ----
    public ItemStack createBlock() {
        ItemStack item = new ItemStack(BANK_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(com.apollosmp.util.Msg.lore(
                    "<gradient:#f9d423:#ff4e50><bold>Town Bank</bold></gradient>"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(com.apollosmp.util.Msg.lore("<gray>Place it in your town. Residents can"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>borrow from the town's money here."));
            lore.add(com.apollosmp.util.Msg.lore(""));
            lore.add(com.apollosmp.util.Msg.lore("<yellow>Right-click once placed."));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isBankItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(blockKey, PersistentDataType.BYTE);
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + ","
                + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public String bankTownAt(Location loc) { return banks.get(key(loc)); }

    /** Where every bank block stands, for labels and particles. */
    public record BankBlock(String world, int x, int y, int z, String town) {}

    public List<BankBlock> allBanks() {
        List<BankBlock> out = new ArrayList<>();
        for (Map.Entry<String, String> e : banks.entrySet()) {
            String[] parts = e.getKey().split(",");
            if (parts.length != 4) continue;
            try {
                out.add(new BankBlock(parts[0], Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), e.getValue()));
            } catch (NumberFormatException ignored) {
                // skip malformed keys
            }
        }
        return out;
    }

    /** Gold flecks and a slow ring, so a bank looks like one. */
    public void spawnParticles() {
        for (BankBlock bank : allBanks()) {
            org.bukkit.World world = plugin.getServer().getWorld(bank.world());
            if (world == null) continue;
            if (!world.isChunkLoaded(bank.x() >> 4, bank.z() >> 4)) continue;
            Location loc = new Location(world, bank.x() + 0.5, bank.y() + 1.1, bank.z() + 0.5);

            org.bukkit.Particle gold = particle("WAX_ON", "HAPPY_VILLAGER");
            if (gold != null) world.spawnParticle(gold, loc, 4, 0.24, 0.14, 0.24, 0.01);

            org.bukkit.Particle crown = particle("END_ROD", "FLAME");
            if (crown != null) {
                double turn = (System.currentTimeMillis() % 4200L) / 4200.0 * Math.PI * 2;
                for (int i = 0; i < 4; i++) {
                    double angle = turn + (Math.PI * 2 * i / 4);
                    world.spawnParticle(crown,
                            loc.clone().add(Math.cos(angle) * 0.65, 0.15, Math.sin(angle) * 0.65),
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private org.bukkit.Particle particle(String... names) {
        for (String name : names) {
            try {
                return org.bukkit.Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next
            }
        }
        return null;
    }

    public boolean placeBank(Location loc, Player player) {
        Town town = plugin.towns().getTownAtLoc(loc);
        Town mine = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null || mine == null || !town.name().equalsIgnoreCase(mine.name())) {
            plugin.msg().send(player, "<red>A town bank has to stand on your own town's land.");
            return false;
        }
        banks.put(key(loc), town.name());
        save();
        plugin.msg().send(player, "<green><white>" + town.name()
                + "</white>'s bank is open. Residents can request loans here.");
        return true;
    }

    public void removeBank(Location loc) {
        banks.remove(key(loc));
        save();
    }

    // ---- reputation ----
    public Reputation reputationOf(UUID player) {
        return reputation.getOrDefault(player, Reputation.FAIR);
    }

    private void setReputation(UUID player, Reputation rep) {
        reputation.put(player, rep);
        save();
    }

    // ---- loans ----
    public List<Loan> loansOf(UUID player) {
        List<Loan> out = new ArrayList<>();
        for (Loan loan : loans.values()) {
            if (loan.borrower().equals(player) && !loan.settled()) out.add(loan);
        }
        return out;
    }

    public List<Loan> loansOwedTo(String town) {
        List<Loan> out = new ArrayList<>();
        for (Loan loan : loans.values()) {
            if (loan.town().equalsIgnoreCase(town) && !loan.settled()) out.add(loan);
        }
        return out;
    }

    public List<Request> requestsFor(String town) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) {
            if (r.town().equalsIgnoreCase(town)) out.add(r);
        }
        return out;
    }

    /** Ask the town for money. */
    public boolean request(Player player, double amount, int days) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }

        if (!loansOf(player.getUniqueId()).isEmpty()) {
            plugin.msg().send(player, "<red>Settle your current loan first."); return false;
        }
        for (Request r : requests.values()) {
            if (r.borrower().equals(player.getUniqueId())) {
                plugin.msg().send(player, "<yellow>You already have a request waiting."); return false;
            }
        }
        double limit = borrowLimit(player.getUniqueId());
        if (limit <= 0) {
            plugin.msg().send(player, "<red>You defaulted on a loan. No town will lend to you until it's cleared.");
            return false;
        }
        if (amount <= 0 || amount > limit) {
            plugin.msg().send(player, "<red>You can borrow up to <white>"
                    + plugin.msg().money(limit) + "</white> at your reputation."); return false;
        }
        if (town.bank() < amount) {
            plugin.msg().send(player, "<red>The town bank only holds "
                    + plugin.msg().money(town.bank()) + "."); return false;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        requests.put(id, new Request(id, player.getUniqueId(), player.getName(),
                town.name(), amount, days, System.currentTimeMillis()));
        save();

        double owed = amount * (1 + interestPercent() / 100.0);
        plugin.msg().send(player, "<green>Request sent. You asked for <#f9d423>"
                + plugin.msg().money(amount) + "</#f9d423> over <white>" + days
                + " days</white>, repaying <#f9d423>" + plugin.msg().money(owed) + "</#f9d423>.");

        Player mayor = plugin.getServer().getPlayer(town.mayor());
        if (mayor != null) {
            plugin.msg().send(mayor, "<#f9d423>" + player.getName()
                    + "</#f9d423> <gray>wants to borrow <white>" + plugin.msg().money(amount)
                    + "</white>. Check the town bank.");
        }
        return true;
    }

    public boolean approve(Player official, String requestId) {
        Request request = requests.get(requestId);
        if (request == null) return false;

        Town town = plugin.towns().townByName(request.town());
        if (town == null) { requests.remove(requestId); save(); return false; }
        if (!town.hasPerm(official.getUniqueId(), TownPerm.WITHDRAW)) {
            plugin.msg().send(official, "<red>You can't approve loans for this town."); return false;
        }
        if (town.bank() < request.amount()) {
            plugin.msg().send(official, "<red>The bank can't cover that any more."); return false;
        }

        town.withdrawBank(request.amount());
        plugin.towns().markDirty();
        plugin.economy().deposit(request.borrower(), request.amount());

        double owed = request.amount() * (1 + interestPercent() / 100.0);
        long due = System.currentTimeMillis() + request.days() * 86_400_000L;
        loans.put(request.id(), new Loan(request.id(), request.borrower(), request.borrowerName(),
                town.name(), request.amount(), owed, System.currentTimeMillis(), due, false));
        requests.remove(requestId);
        save();

        plugin.msg().send(official, "<green>Approved. <white>" + request.borrowerName()
                + "</white> owes <#f9d423>" + plugin.msg().money(owed) + "</#f9d423> in "
                + request.days() + " days.");
        Player borrower = plugin.getServer().getPlayer(request.borrower());
        if (borrower != null) {
            plugin.msg().send(borrower, "<green>Your loan was approved! <#f9d423>"
                    + plugin.msg().money(request.amount()) + "</#f9d423> is in your account.");
            plugin.msg().send(borrower, "<gray>Repay <#f9d423>" + plugin.msg().money(owed)
                    + "</#f9d423> within <white>" + request.days() + " days</white> at the town bank.");
        }
        return true;
    }

    public boolean deny(Player official, String requestId) {
        Request request = requests.remove(requestId);
        save();
        if (request == null) return false;
        plugin.msg().send(official, "<yellow>Request denied.");
        Player borrower = plugin.getServer().getPlayer(request.borrower());
        if (borrower != null) {
            plugin.msg().send(borrower, "<red>Your loan request was turned down.");
        }
        return true;
    }

    /** Pay some or all of what you owe. */
    public boolean repay(Player player, double amount) {
        List<Loan> mine = loansOf(player.getUniqueId());
        if (mine.isEmpty()) { plugin.msg().send(player, "<gray>You don't owe anything."); return false; }
        Loan loan = mine.get(0);

        double pay = Math.min(amount, loan.owed());
        if (pay <= 0) return false;
        if (!plugin.economy().has(player.getUniqueId(), pay)) {
            plugin.msg().send(player, "<red>You don't have that much."); return false;
        }

        plugin.economy().withdraw(player.getUniqueId(), pay);
        Town town = plugin.towns().townByName(loan.town());
        if (town != null) {
            town.depositBank(pay);
            plugin.towns().markDirty();
        }
        loan.setOwed(loan.owed() - pay);
        save();

        if (!loan.settled()) {
            plugin.msg().send(player, "<green>Paid <#f9d423>" + plugin.msg().money(pay)
                    + "</#f9d423>. Still owing <#f9d423>" + plugin.msg().money(loan.owed())
                    + "</#f9d423>.");
            return true;
        }

        // Cleared it - reputation reflects whether it was on time.
        boolean late = loan.defaulted() || loan.overdue();
        setReputation(player.getUniqueId(), late ? Reputation.FAIR : Reputation.TRUSTED);
        loans.remove(loan.id());
        save();
        plugin.msg().send(player, late
                ? "<green>Debt cleared. <gray>Your reputation is back to <#f9d423>Fair</#f9d423>."
                : "<green>Debt cleared on time! <gray>Reputation: <green>Trusted</green>.");
        return true;
    }

    /** Called on a timer - flags anyone who's blown their deadline. */
    public void tick() {
        for (Loan loan : loans.values()) {
            if (loan.defaulted() || !loan.overdue()) continue;
            loan.setDefaulted(true);
            setReputation(loan.borrower(), Reputation.DEFAULTED);

            Player borrower = plugin.getServer().getPlayer(loan.borrower());
            if (borrower != null) {
                plugin.msg().send(borrower, "<red><bold>Your loan is overdue.</bold></red> "
                        + "<gray>Reputation dropped to <red>Defaulted</red>. Repay at the town bank to recover.");
            }
            Town town = plugin.towns().townByName(loan.town());
            if (town != null) {
                Player mayor = plugin.getServer().getPlayer(town.mayor());
                if (mayor != null) {
                    plugin.msg().send(mayor, "<red>" + loan.borrowerName()
                            + " has defaulted on a loan of " + plugin.msg().money(loan.principal()) + ".");
                }
            }
            save();
        }
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, String> e : banks.entrySet()) {
            cfg.set("banks." + e.getKey().replace(",", "_"), e.getValue());
        }
        for (Loan loan : loans.values()) {
            String base = "loans." + loan.id();
            cfg.set(base + ".borrower", loan.borrower().toString());
            cfg.set(base + ".name", loan.borrowerName());
            cfg.set(base + ".town", loan.town());
            cfg.set(base + ".principal", loan.principal());
            cfg.set(base + ".owed", loan.owed());
            cfg.set(base + ".taken", loan.takenAt());
            cfg.set(base + ".due", loan.dueAt());
            cfg.set(base + ".defaulted", loan.defaulted());
        }
        for (Request r : requests.values()) {
            String base = "requests." + r.id();
            cfg.set(base + ".borrower", r.borrower().toString());
            cfg.set(base + ".name", r.borrowerName());
            cfg.set(base + ".town", r.town());
            cfg.set(base + ".amount", r.amount());
            cfg.set(base + ".days", r.days());
            cfg.set(base + ".asked", r.asked());
        }
        for (Map.Entry<UUID, Reputation> e : reputation.entrySet()) {
            cfg.set("reputation." + e.getKey(), e.getValue().name());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save bank.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection bankSection = cfg.getConfigurationSection("banks");
        if (bankSection != null) {
            for (String k : bankSection.getKeys(false)) {
                banks.put(k.replace("_", ","), cfg.getString("banks." + k));
            }
        }
        ConfigurationSection loanSection = cfg.getConfigurationSection("loans");
        if (loanSection != null) {
            for (String id : loanSection.getKeys(false)) {
                String base = "loans." + id;
                try {
                    loans.put(id, new Loan(id, UUID.fromString(cfg.getString(base + ".borrower")),
                            cfg.getString(base + ".name"), cfg.getString(base + ".town"),
                            cfg.getDouble(base + ".principal"), cfg.getDouble(base + ".owed"),
                            cfg.getLong(base + ".taken"), cfg.getLong(base + ".due"),
                            cfg.getBoolean(base + ".defaulted")));
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Skipped a malformed loan: " + id);
                }
            }
        }
        ConfigurationSection reqSection = cfg.getConfigurationSection("requests");
        if (reqSection != null) {
            for (String id : reqSection.getKeys(false)) {
                String base = "requests." + id;
                try {
                    requests.put(id, new Request(id, UUID.fromString(cfg.getString(base + ".borrower")),
                            cfg.getString(base + ".name"), cfg.getString(base + ".town"),
                            cfg.getDouble(base + ".amount"), cfg.getInt(base + ".days"),
                            cfg.getLong(base + ".asked")));
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Skipped a malformed loan request: " + id);
                }
            }
        }
        ConfigurationSection repSection = cfg.getConfigurationSection("reputation");
        if (repSection != null) {
            for (String k : repSection.getKeys(false)) {
                try {
                    reputation.put(UUID.fromString(k),
                            Reputation.valueOf(cfg.getString("reputation." + k, "FAIR")));
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
    }
}

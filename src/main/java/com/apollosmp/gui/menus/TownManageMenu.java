package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownWar;
import com.apollosmp.town.WarManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Your town, one level in from the hub. Six clear areas, nothing overlapping. */
public class TownManageMenu extends Gui {

    private static final int LAND = 20;
    private static final int MEMBERS = 21;
    private static final int BANK = 22;
    private static final int UPGRADES = 23;
    private static final int HOME = 24;
    private static final int WAR = 29;
    private static final int ALLIES = 31;
    private static final int SETTINGS = 33;
    private static final int BACK = 40;

    public TownManageMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<#f9d423><bold>Your Town</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        List<TownWar> wars = plugin.wars().warsFor(town.name());
        WarManager.Declaration pending = plugin.wars().declarationFor(town.name());

        inventory.setItem(4, Items.of(Material.WHITE_BANNER)
                .name("<gradient:#f9d423:#ff4e50><bold>" + town.name() + "</bold></gradient>")
                .lore("<gray>Land: <white>" + town.claims().size() + " / "
                                + plugin.towns().claimLimit(town) + "</white> chunks",
                        "<gray>Residents: <white>" + town.memberCount() + "</white>",
                        "<gray>Bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        wars.isEmpty() ? "<gray>Status: <green>at peace</green>"
                                : "<gray>Status: <red>at war</red>")
                .glow(true).hideAttributes().build());

        inventory.setItem(LAND, Items.of(Material.GRASS_BLOCK)
                .name("<green><bold>Land & Plots</bold>")
                .lore("<gray>Claim chunks, and buy, rent or list",
                        "<gray>the plot you're standing on.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(MEMBERS, Items.of(Material.PLAYER_HEAD)
                .name("<#5ad1e8><bold>Residents</bold>")
                .lore("<gray>Living here: <white>" + town.memberCount() + "</white>",
                        "<gray>Invite people and set their ranks.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(BANK, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>Bank</bold>")
                .lore("<gray>Balance: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        "<gray>Deposit, withdraw, and set taxes.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(UPGRADES, Items.of(Material.BEACON)
                .name("<#f9d423><bold>Upgrades</bold>")
                .lore("<gray>Spend the bank on haste, speed,",
                        "<gray>healing and faster businesses.",
                        "", "<yellow>Click to open")
                .glow(true).hideAttributes().build());

        inventory.setItem(HOME, Items.of(Material.ENDER_PEARL)
                .name("<#5ad1e8><bold>Town Home</bold>")
                .lore("<gray>Teleport to your town spawn.",
                        "<dark_gray>/town home",
                        "", "<yellow>Click to travel")
                .build());

        boolean urgent = !wars.isEmpty() || pending != null;
        inventory.setItem(WAR, Items.of(urgent ? Material.IRON_SWORD : Material.SHIELD)
                .name(urgent ? "<red><bold>War & Peace</bold>" : "<gray><bold>War & Peace</bold>")
                .lore(pending != null
                                ? "<red>" + pending.from() + " has declared war!</red>"
                                : (wars.isEmpty()
                                        ? "<gray>You're at peace with everyone."
                                        : "<red>Fighting " + wars.size() + " town(s)</red>"),
                        "<gray>Declare war, or sign a treaty.",
                        "", "<yellow>Click to open")
                .glow(urgent).hideAttributes().build());

        int allyCount = plugin.diplomacy().alliesOf(town.name()).size();
        var allyOffer = plugin.diplomacy().offerFor(town.name());
        inventory.setItem(ALLIES, Items.of(Material.LIME_BANNER)
                .name("<#5ad1e8><bold>Alliances</bold>")
                .lore(allyOffer != null
                                ? "<green>" + allyOffer.from() + " wants an alliance!</green>"
                                : "<gray>Allies: <white>" + allyCount + "</white>",
                        "<gray>Shared chat, free passage, and",
                        "<gray>they fight alongside you.",
                        "", "<yellow>Click to open")
                .glow(allyOffer != null).hideAttributes().build());

        inventory.setItem(SETTINGS, Items.of(Material.COMPARATOR)
                .name("<#5ad1e8><bold>Settings</bold>")
                .lore("<gray>Spawn, permissions, visitors,",
                        "<gray>borders, and the risky options.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }

        switch (slot) {
            case LAND -> new TownLandMenu(plugin, player).open();
            case MEMBERS -> new TownMembersMenu(plugin, player).open();
            case BANK -> new TownBankMenu(plugin, player).open();
            case UPGRADES -> new TownUpgradesMenu(plugin, player).open();
            case HOME -> { plugin.towns().teleportSpawn(player); player.closeInventory(); }
            case WAR -> new TownWarMenu(plugin, player).open();
            case ALLIES -> new TownDiplomacyMenu(plugin, player).open();
            case SETTINGS -> new TownSettingsMenu(plugin, player).open();
            case BACK -> new TownMenu(plugin, player).open();
            default -> { /* no-op */ }
        }
    }
}

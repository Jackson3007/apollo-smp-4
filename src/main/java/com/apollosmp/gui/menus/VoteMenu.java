package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.vote.VoteManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The /vote hub: links to 5 sites, coin balance, and the coin shop. */
public class VoteMenu extends Gui {

    private static final int[] SITE_SLOTS = {19, 20, 21, 22, 23};
    private static final int SHOP = 39;
    private static final int KEY_INFO = 41;
    private static final int CLOSE = 44;

    private final List<VoteManager.Service> services = new ArrayList<>();

    public VoteMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<gradient:#f9d423:#ff4e50><bold>Vote for Apollo</bold></gradient>");
    }

    @Override
    protected void build() {
        services.clear();
        services.addAll(plugin.voting().services());
        Set<String> claimed = plugin.voting().claimedToday(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<#5ad1e8><bold>Your Sky Coins</bold>")
                .lore("<gray>Balance: <#5ad1e8>" + plugin.skyCoins().get(viewer.getUniqueId()) + " coins</#5ad1e8>",
                        "<gray>Vote all 5 sites for <#f9d423>+" + plugin.voting().allVotedBonus()
                                + " bonus coins</#f9d423>!")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < SITE_SLOTS.length; i++) {
            if (i < services.size()) {
                VoteManager.Service s = services.get(i);
                boolean done = claimed.stream().anyMatch(c -> c.equalsIgnoreCase(s.name()));
                inventory.setItem(SITE_SLOTS[i], Items.of(done ? Material.LIME_DYE : Material.PAPER)
                        .name((done ? "<green>" : "<#f9d423>") + "<bold>" + s.name() + "</bold>")
                        .lore("<gray>" + s.link(),
                                "",
                                done ? "<green>\u2714 Claimed today"
                                        : "<yellow>Click to vote & claim <#5ad1e8>" + plugin.voting().coinsPerVote()
                                        + " coin</#5ad1e8>")
                        .glow(!done).hideAttributes().build());
            } else {
                inventory.setItem(SITE_SLOTS[i], Items.of(Material.GRAY_DYE)
                        .name("<dark_gray>Vote site not set")
                        .lore("<dark_gray>Configure in config.yml").build());
            }
        }

        inventory.setItem(SHOP, Items.of(Material.CHEST)
                .name("<#5ad1e8><bold>Coin Shop</bold>")
                .lore("<gray>Spend Sky Coins on exclusive gear.", "", "<yellow>Click to open")
                .glow(true).hideAttributes().build());

        inventory.setItem(KEY_INFO, Items.of(Material.TRIPWIRE_HOOK)
                .name("<gradient:#f9d423:#ff4e50><bold>Vote Keys</bold></gradient>")
                .lore("<gray>Each vote also gives a <#f9d423>Vote Key</#f9d423>.",
                        "<gray>Right-click a key anywhere to spin",
                        "<gray>the crate for prizes!")
                .glow(true).hideAttributes().build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }
        if (slot == SHOP) { new CoinShopMenu(plugin, player).open(); return; }

        for (int i = 0; i < SITE_SLOTS.length; i++) {
            if (SITE_SLOTS[i] == slot && i < services.size()) {
                VoteManager.Service s = services.get(i);
                // Always send the clickable link so they can actually vote.
                plugin.msg().send(player, "<gray>Vote here: <click:open_url:'" + s.link()
                        + "'><hover:show_text:'Click to open'><#5ad1e8><u>" + s.link() + "</u></#5ad1e8></click>");
                VoteManager.ClaimResult result = plugin.voting().claim(player, s.name());
                switch (result) {
                    case GRANTED -> plugin.msg().send(player, "<green>+<#5ad1e8>"
                            + plugin.voting().coinsPerVote() + " Sky Coin</#5ad1e8> and a Vote Key!");
                    case GRANTED_ALL_BONUS -> plugin.msg().send(player, "<green>All 5 done! +<#5ad1e8>"
                            + plugin.voting().allVotedBonus() + " bonus Sky Coins</#5ad1e8>!");
                    case ALREADY_CLAIMED -> plugin.msg().send(player,
                            "<yellow>You've already claimed this site today.");
                    case NO_SERVICE -> plugin.msg().send(player, "<red>That site isn't set up.");
                }
                redraw();
                return;
            }
        }
    }
}

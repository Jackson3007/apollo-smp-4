package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** A spinning "wheel" vote crate. Consumes a key (handled by the caller) and awards a prize. */
public class CrateMenu extends Gui {

    private enum Kind { COINS, MONEY, ITEM, KEY, SPAWNER }

    private record Prize(Kind kind, int amount, Material material, String display) {}

    private static final int[] REEL_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int CENTER = 22;

    private final List<Prize> pool = new ArrayList<>();
    private List<Prize> reel = new ArrayList<>();
    private Prize winner;
    private int totalSteps;
    private boolean started = false;
    private boolean cancelled = false;
    private boolean awarded = false;

    public CrateMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<gradient:#f9d423:#ff4e50><bold>Vote Crate</bold></gradient>");
        buildPool();
    }

    private void buildPool() {
        add(new Prize(Kind.COINS, 1, Material.NETHER_STAR, "<#5ad1e8>1 Sky Coin"), 10);
        add(new Prize(Kind.COINS, 2, Material.NETHER_STAR, "<#5ad1e8>2 Sky Coins"), 7);
        add(new Prize(Kind.COINS, 5, Material.NETHER_STAR, "<#5ad1e8>5 Sky Coins"), 3);
        add(new Prize(Kind.MONEY, 250, Material.GOLD_INGOT, "<#f9d423>$250"), 8);
        add(new Prize(Kind.MONEY, 1000, Material.GOLD_BLOCK, "<#f9d423>$1,000"), 3);
        add(new Prize(Kind.ITEM, 4, Material.DIAMOND, "<aqua>4 Diamonds"), 4);
        add(new Prize(Kind.ITEM, 16, Material.IRON_INGOT, "<white>16 Iron"), 6);
        add(new Prize(Kind.KEY, 1, Material.TRIPWIRE_HOOK, "<#f9d423>A Vote Key"), 2);
        add(new Prize(Kind.SPAWNER, 1, Material.SPAWNER, "<#e94fd0>A Spawner"), 1);
    }

    private void add(Prize p, int weight) {
        for (int i = 0; i < weight; i++) pool.add(p);
    }

    @Override
    protected void build() {
        // Frame
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(13, Items.of(Material.HOPPER).name("<#f9d423>\u25BC").build());
        inventory.setItem(31, Items.of(Material.HOPPER).name("<#f9d423>\u25B2").build());
        inventory.setItem(4, Items.of(Material.CHEST)
                .name("<#f9d423><bold>Spinning...</bold>").glow(true).hideAttributes().build());

        if (!started) {
            started = true;
            prepareReel();
            renderReel(0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> step(1), 2L);
        }
    }

    private void prepareReel() {
        totalSteps = 34 + ThreadLocalRandom.current().nextInt(0, 10);
        winner = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        reel = new ArrayList<>();
        int length = totalSteps + REEL_SLOTS.length + 2;
        for (int i = 0; i < length; i++) {
            reel.add(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
        }
        // Land the winner in the center slot at the final step.
        reel.set(totalSteps + 4, winner);
    }

    private void step(int stepIndex) {
        if (cancelled || !viewer.isOnline()) return;
        renderReel(stepIndex);
        if (stepIndex >= totalSteps) {
            finish();
            return;
        }
        double p = (double) stepIndex / totalSteps;
        long delay = p < 0.5 ? 1L : p < 0.75 ? 2L : p < 0.9 ? 4L : 6L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> step(stepIndex + 1), delay);
    }

    private void renderReel(int offset) {
        for (int i = 0; i < REEL_SLOTS.length; i++) {
            Prize prize = reel.get(Math.min(offset + i, reel.size() - 1));
            boolean center = REEL_SLOTS[i] == CENTER;
            inventory.setItem(REEL_SLOTS[i], icon(prize, center));
        }
    }

    private ItemStack icon(Prize prize, boolean center) {
        return Items.of(prize.material())
                .name((center ? "<bold>" : "") + prize.display())
                .glow(center).hideAttributes().build();
    }

    private void finish() {
        award();
        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<green><bold>You won: " + (winner != null ? winner.display() : "") + "<green>!</bold>")
                .glow(true).hideAttributes().build());
    }

    private void award() {
        if (awarded || winner == null) return;
        awarded = true;
        switch (winner.kind()) {
            case COINS -> {
                plugin.skyCoins().add(viewer.getUniqueId(), winner.amount());
                plugin.msg().send(viewer, "<green>You won <#5ad1e8>" + winner.amount() + " Sky Coins</#5ad1e8>!");
            }
            case MONEY -> {
                plugin.economy().deposit(viewer.getUniqueId(), winner.amount());
                plugin.msg().send(viewer, "<green>You won <#f9d423>" + plugin.msg().money(winner.amount()) + "</#f9d423>!");
            }
            case ITEM -> {
                Items.give(viewer, new ItemStack(winner.material(), winner.amount()));
                plugin.msg().send(viewer, "<green>You won <white>" + winner.amount() + "x "
                        + Items.pretty(winner.material()) + "</white>!");
            }
            case KEY -> {
                Items.give(viewer, plugin.customItems().voteKey());
                plugin.msg().send(viewer, "<green>You won another <#f9d423>Vote Key</#f9d423>!");
            }
            case SPAWNER -> {
                Items.give(viewer, new ItemStack(Material.SPAWNER));
                plugin.msg().send(viewer, "<green>You won a <#e94fd0>Spawner</#e94fd0>!");
            }
        }
    }

    @Override
    public void onClose(Player player) {
        // If they close mid-spin, still hand over the prize so the key isn't wasted.
        cancelled = true;
        award();
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        // Everything is locked during/after the spin.
    }
}

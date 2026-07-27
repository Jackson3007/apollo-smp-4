package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.onboarding.OnboardingManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The "Getting Started" checklist new players work through for rewards. */
public class GuideMenu extends Gui {

    private static final int[] TASK_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int BONUS = 31;
    private static final int CLOSE = 35;

    public GuideMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<gradient:#f9d423:#ff4e50><bold>Getting Started</bold></gradient>");
    }

    @Override
    protected void build() {
        OnboardingManager ob = plugin.onboarding();
        List<OnboardingManager.Task> tasks = ob.tasks();

        int done = 0;
        for (OnboardingManager.Task t : tasks) {
            if (ob.isClaimed(viewer.getUniqueId(), t.id())) done++;
        }

        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<gradient:#f9d423:#ff4e50><bold>Welcome to Apollo!</bold></gradient>")
                .lore("<gray>Complete these starter tasks to learn",
                        "<gray>the ropes and earn some starting cash.",
                        "",
                        "<gray>Progress: <#f9d423>" + done + "<gray>/</gray>" + tasks.size() + "</#f9d423>")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < TASK_SLOTS.length && i < tasks.size(); i++) {
            OnboardingManager.Task task = tasks.get(i);
            boolean claimed = ob.isClaimed(viewer.getUniqueId(), task.id());
            boolean complete = ob.isComplete(task.id(), viewer);

            String status = claimed
                    ? "<dark_gray>\u2714 Reward claimed</dark_gray>"
                    : complete
                        ? "<green>\u2714 Done! <yellow>Click to claim <#f9d423>"
                                + plugin.msg().money(task.reward()) + "</#f9d423></yellow>"
                        : "<red>\u2717 Not done yet";

            inventory.setItem(TASK_SLOTS[i], Items.of(task.icon())
                    .name((claimed ? "<gray>" : "<white>") + task.title())
                    .lore("<gray>" + task.howTo(),
                            "",
                            "<gray>Reward: <#f9d423>" + plugin.msg().money(task.reward()) + "</#f9d423>",
                            status)
                    .glow(complete && !claimed).hideAttributes().build());
        }

        boolean allClaimed = ob.allTasksClaimed(viewer.getUniqueId());
        boolean bonusClaimed = ob.bonusClaimed(viewer.getUniqueId());
        inventory.setItem(BONUS, Items.of(bonusClaimed ? Material.GOLD_NUGGET : Material.GOLD_BLOCK)
                .name("<#ffd54a><bold>Completion Bonus</bold></#ffd54a>")
                .lore("<gray>Finish and claim every task to unlock",
                        "<gray>a bonus of <#f9d423>" + plugin.msg().money(ob.bonusReward()) + "</#f9d423><gray>.",
                        "",
                        bonusClaimed
                                ? "<dark_gray>\u2714 Bonus claimed</dark_gray>"
                                : allClaimed
                                    ? "<green>\u2714 Ready! <yellow>Click to claim!"
                                    : "<red>\u2717 Claim all tasks first")
                .glow(allClaimed && !bonusClaimed).hideAttributes().build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }

        OnboardingManager ob = plugin.onboarding();

        if (slot == BONUS) {
            if (ob.claimBonus(player)) {
                plugin.msg().send(player, "<green>Completion bonus claimed - <#f9d423>"
                        + plugin.msg().money(ob.bonusReward()) + "</#f9d423><green>! Welcome aboard.");
            }
            redraw();
            return;
        }

        List<OnboardingManager.Task> tasks = ob.tasks();
        for (int i = 0; i < TASK_SLOTS.length && i < tasks.size(); i++) {
            if (TASK_SLOTS[i] != slot) continue;
            OnboardingManager.Task task = tasks.get(i);
            if (ob.isClaimed(player.getUniqueId(), task.id())) {
                plugin.msg().send(player, "<gray>You've already claimed that reward.");
            } else if (ob.claim(player, task.id())) {
                plugin.msg().send(player, "<green>Task complete - <#f9d423>"
                        + plugin.msg().money(task.reward()) + "</#f9d423> <green>added to your balance!");
            } else {
                plugin.msg().send(player, "<yellow>" + task.title() + ": <gray>" + task.howTo());
            }
            redraw();
            return;
        }
    }
}

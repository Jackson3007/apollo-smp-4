package com.apollosmp.sell;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the sell value on item tooltips while they're in a player's inventory or
 * an open chest, and takes it off again when they leave.
 *
 * Because the total depends on stack size, identical items would otherwise stop
 * stacking. The fix is to strip the lines just before anything merges, then
 * re-apply once the dust settles.
 */
public class WorthTags implements Listener {

    /** Bumped whenever the lore layout changes, so old tags get rebuilt once. */
    private static final int VERSION = 2;

    private final ApolloSMP plugin;
    private final NamespacedKey valueKey;
    private final NamespacedKey linesKey;
    private final NamespacedKey versionKey;

    public WorthTags(ApolloSMP plugin) {
        this.plugin = plugin;
        this.valueKey = new NamespacedKey(plugin, "apollo_worth");
        this.linesKey = new NamespacedKey(plugin, "apollo_worth_lines");
        this.versionKey = new NamespacedKey(plugin, "apollo_worth_v");
    }

    /**
     * How many trailing lore lines look like ones we wrote. Used to clean up
     * tags from older builds where the stored line count can't be trusted.
     */
    private int trailingMoneyLines(List<Component> lore, int max) {
        if (lore == null || lore.isEmpty()) return 0;
        String symbol = java.util.regex.Pattern.quote(plugin.msg().symbol());
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("^" + symbol + "[0-9,]+(\\.[0-9]+)?( each)?$");
        int count = 0;
        for (int i = lore.size() - 1; i >= 0 && count < max; i--) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lore.get(i)).trim();
            if (!pattern.matcher(plain).matches()) break;
            count++;
        }
        return count;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("sell.worth-lore", true);
    }

    // ------------------------------------------------ tagging
    /** Add or refresh the price lines. Returns true if the item changed. */
    public boolean mark(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!enabled() || !plugin.sell().isSellable(stack)) return strip(stack);

        double unit = plugin.sell().priceOf(stack.getType());
        if (unit <= 0) return strip(stack);
        int amount = stack.getAmount();
        double total = unit * amount;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Double written = pdc.get(valueKey, PersistentDataType.DOUBLE);
        int version = pdc.getOrDefault(versionKey, PersistentDataType.INTEGER, 1);
        boolean current = version == VERSION;
        if (current && written != null && Math.abs(written - total) < 0.0001) return false;

        List<Component> lore = meta.lore();
        lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);

        int previous;
        if (written == null) {
            previous = 0;
        } else if (current) {
            previous = pdc.getOrDefault(linesKey, PersistentDataType.INTEGER, 1);
        } else {
            // Tagged by an older build - work out how many lines to drop by looking.
            previous = trailingMoneyLines(lore, 3);
        }
        for (int i = 0; i < previous && !lore.isEmpty(); i++) lore.remove(lore.size() - 1);

        int added;
        if (amount > 1) {
            lore.add(Msg.lore("<#f9d423>" + plugin.msg().money(total) + "</#f9d423>"));
            lore.add(Msg.lore("<dark_gray>" + plugin.msg().money(unit) + " each</dark_gray>"));
            added = 2;
        } else {
            lore.add(Msg.lore("<#f9d423>" + plugin.msg().money(total) + "</#f9d423>"));
            added = 1;
        }

        meta.lore(lore);
        pdc.set(valueKey, PersistentDataType.DOUBLE, total);
        pdc.set(linesKey, PersistentDataType.INTEGER, added);
        pdc.set(versionKey, PersistentDataType.INTEGER, VERSION);
        stack.setItemMeta(meta);
        return true;
    }

    /** Take the price lines back off. Returns true if the item changed. */
    public boolean strip(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(valueKey, PersistentDataType.DOUBLE)) return false;

        List<Component> lore = meta.lore();
        int version = pdc.getOrDefault(versionKey, PersistentDataType.INTEGER, 1);
        int lines = version == VERSION
                ? pdc.getOrDefault(linesKey, PersistentDataType.INTEGER, 1)
                : trailingMoneyLines(lore, 3);
        if (lore != null && !lore.isEmpty()) {
            List<Component> trimmed = new ArrayList<>(lore);
            for (int i = 0; i < lines && !trimmed.isEmpty(); i++) trimmed.remove(trimmed.size() - 1);
            meta.lore(trimmed.isEmpty() ? null : trimmed);
        }
        pdc.remove(valueKey);
        pdc.remove(linesKey);
        pdc.remove(versionKey);
        stack.setItemMeta(meta);
        return true;
    }

    // ------------------------------------------------ bulk helpers
    private boolean isOurMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Gui;
    }

    private void markAll(Inventory inventory) {
        if (inventory == null || isOurMenu(inventory)) return;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            if (mark(stack)) inventory.setItem(i, stack);
        }
    }

    private void stripAll(Inventory inventory) {
        if (inventory == null || isOurMenu(inventory)) return;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            if (strip(stack)) inventory.setItem(i, stack);
        }
    }

    /** Clear tags off stacks of one material so vanilla can merge them. */
    private void stripMatching(Inventory inventory, Material material) {
        if (inventory == null) return;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            if (strip(stack)) inventory.setItem(i, stack);
        }
    }

    // ------------------------------------------------ the periodic pass
    public void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (enabled()) markAll(player.getInventory());
            else stripAll(player.getInventory());
            showContainerTotal(player);
        }
    }

    public boolean showTotals() {
        return plugin.getConfig().getBoolean("sell.chest-total", true);
    }

    /** Keep a running total of the open container on the action bar. */
    public void showContainerTotal(Player player) {
        if (!showTotals()) return;
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top == null || top instanceof PlayerInventory || isOurMenu(top)) return;
        if (top.getSize() <= 0) return;

        double total = 0;
        int items = 0;
        int unsellable = 0;
        for (ItemStack stack : top.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (plugin.sell().isSellable(stack)) {
                total += plugin.sell().valueOf(stack);
                items += stack.getAmount();
            } else {
                unsellable += stack.getAmount();
            }
        }

        if (items == 0 && unsellable == 0) {
            player.sendActionBar(Msg.mm("<dark_gray>Empty</dark_gray>"));
            return;
        }
        String text = "<gray>Contents:</gray> <#f9d423>" + plugin.msg().money(total)
                + "</#f9d423> <dark_gray>(" + items + " item" + (items == 1 ? "" : "s");
        if (unsellable > 0) text += ", " + unsellable + " unsellable";
        text += ")</dark_gray>";
        player.sendActionBar(Msg.mm(text));
    }

    // ------------------------------------------------ events
    /** Strip before a pickup so the incoming stack merges, then re-tag. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack ground = event.getItem().getItemStack();
        if (strip(ground)) event.getItem().setItemStack(ground);
        stripMatching(player.getInventory(), ground.getType());
        retagLater(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (strip(stack)) event.getItemDrop().setItemStack(stack);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack stack : event.getDrops()) strip(stack);
    }

    /** Chests show values while you're looking in them. */
    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!enabled()) return;
        Inventory top = event.getInventory();
        if (top instanceof PlayerInventory || isOurMenu(top)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            markAll(top);
            if (event.getPlayer() instanceof Player p) showContainerTotal(p);
        });
    }

    /** ...and go back to normal when you close them. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top instanceof PlayerInventory || isOurMenu(top)) return;
        stripAll(top);
    }

    /** Strip everything involved in a click so merges work, then re-tag. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) return;
        stripAll(event.getView().getTopInventory());
        stripAll(event.getView().getBottomInventory());
        retagViewLater(event.getView());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (isOurMenu(event.getView().getTopInventory())) return;
        stripAll(event.getView().getTopInventory());
        stripAll(event.getView().getBottomInventory());
        retagViewLater(event.getView());
    }

    private void retagLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (enabled()) markAll(player.getInventory());
        });
    }

    private void retagViewLater(InventoryView view) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!enabled()) return;
            markAll(view.getBottomInventory());
            Inventory top = view.getTopInventory();
            if (!(top instanceof PlayerInventory) && !isOurMenu(top)) markAll(top);
            if (view.getPlayer() instanceof Player p) {
                p.updateInventory();
                showContainerTotal(p);
            }
        });
    }
}

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

        int amount = Math.max(1, stack.getAmount());
        double total = plugin.sell().valueOf(stack);
        if (total <= 0) return strip(stack);
        double unit = total / amount;

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
            updateTitle(player);
        }
    }

    public boolean showTotals() {
        // Off by default: Paper only allows retitling inventories the server
        // created itself, so real chests reject it.
        return plugin.getConfig().getBoolean("sell.chest-total", false);
    }

    /** The container's own title, remembered so the total isn't appended twice. */
    private final java.util.Map<java.util.UUID, String> baseTitle = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Double> shownTotal = new java.util.concurrent.ConcurrentHashMap<>();

    /** Put the contents' value in the container's title bar. */
    public void updateTitle(Player player) {
        if (!showTotals()) return;
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top == null || top instanceof PlayerInventory || isOurMenu(top)) {
            forgetTitle(player);
            return;
        }
        // Re-sending the window while an item is on the cursor can drop it.
        if (!player.getItemOnCursor().getType().isAir()) return;

        double total = 0;
        for (ItemStack stack : top.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (plugin.sell().isSellable(stack)) total += plugin.sell().valueOf(stack);
        }

        Double last = shownTotal.get(player.getUniqueId());
        if (last != null && Math.abs(last - total) < 0.005) return;

        String base = baseTitle.computeIfAbsent(player.getUniqueId(), k -> readTitle(view, top));
        String title = total > 0
                ? base + " \u00a76(" + plugin.msg().money(total) + ")"
                : base;
        if (setTitle(view, title)) shownTotal.put(player.getUniqueId(), total);
    }

    // Called reflectively: setTitle/getTitle aren't on every server build, and a
    // missing method should degrade quietly rather than break the plugin.
    private java.lang.reflect.Method setTitleMethod;
    private java.lang.reflect.Method getTitleMethod;
    private boolean titleUnavailable;

    /** True when the resolved method wants a Component rather than a String. */
    private boolean titleTakesComponent;

    private boolean setTitle(InventoryView view, String title) {
        if (titleUnavailable) return false;
        try {
            if (setTitleMethod == null) resolveTitleMethod(view);
            if (setTitleMethod == null) {
                titleUnavailable = true;
                plugin.getLogger().info("Chest totals disabled: this server build can't retitle inventories.");
                return false;
            }
            if (titleTakesComponent) {
                setTitleMethod.invoke(view, Msg.mm(title.replace("\u00a76", "<#f9d423>")));
            } else {
                setTitleMethod.invoke(view, title);
            }
            return true;
        } catch (Throwable ex) {
            titleUnavailable = true;
            plugin.getLogger().info("Chest totals unavailable on this server - "
                    + "real containers can't be retitled. Per-item prices still work.");
            return false;
        }
    }

    /** Different server builds name this differently, so try the known shapes. */
    private void resolveTitleMethod(InventoryView view) {
        Class<?> component = net.kyori.adventure.text.Component.class;
        String[][] candidates = {
                {"title", "component"},
                {"setTitle", "component"},
                {"setTitle", "string"}
        };
        for (String[] candidate : candidates) {
            try {
                if (candidate[1].equals("component")) {
                    setTitleMethod = view.getClass().getMethod(candidate[0], component);
                    titleTakesComponent = true;
                } else {
                    setTitleMethod = view.getClass().getMethod(candidate[0], String.class);
                    titleTakesComponent = false;
                }
                return;
            } catch (NoSuchMethodException ignored) {
                // try the next shape
            }
        }
    }

    private String getTitle(InventoryView view) {
        try {
            if (getTitleMethod == null) {
                try {
                    getTitleMethod = view.getClass().getMethod("getTitle");
                } catch (NoSuchMethodException ex) {
                    getTitleMethod = view.getClass().getMethod("title");
                }
            }
            Object result = getTitleMethod.invoke(view);
            if (result instanceof String s) return s;
            if (result instanceof net.kyori.adventure.text.Component c) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(c);
            }
            return null;
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Work out the container's original name, without any total we added. */
    private String readTitle(InventoryView view, Inventory top) {
        String title = getTitle(view);
        if (title == null || title.isBlank()) {
            title = switch (top.getType()) {
                case CHEST -> top.getSize() > 27 ? "Large Chest" : "Chest";
                case BARREL -> "Barrel";
                case ENDER_CHEST -> "Ender Chest";
                case SHULKER_BOX -> "Shulker Box";
                case HOPPER -> "Hopper";
                case DISPENSER -> "Dispenser";
                case DROPPER -> "Dropper";
                default -> "Container";
            };
        }
        // Strip a total we appended earlier, if the title got captured mid-update.
        int marker = title.lastIndexOf(" \u00a76(");
        if (marker > 0 && title.endsWith(")")) title = title.substring(0, marker);
        return title;
    }

    private void forgetTitle(Player player) {
        baseTitle.remove(player.getUniqueId());
        shownTotal.remove(player.getUniqueId());
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
        if (!isStorage(top)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            markAll(top);
            if (event.getPlayer() instanceof Player p) {
                forgetTitle(p);
                updateTitle(p);
            }
        });
    }

    /** ...and go back to normal when you close them. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (top instanceof PlayerInventory || isOurMenu(top)) return;
        stripAll(top);
        if (event.getPlayer() instanceof Player p) forgetTitle(p);
    }

    /**
     * Only the material actually being moved needs untagging - that's all a
     * merge can involve. Touching every slot on every click was far too slow.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!enabled()) return;
        if (isOurMenu(event.getView().getTopInventory())) return;

        clearFor(event.getView(), event.getCurrentItem());
        clearFor(event.getView(), event.getCursor());
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p) {
            clearFor(event.getView(), p.getInventory().getItem(event.getHotbarButton()));
        }
        retagViewLater(event.getView());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled()) return;
        if (isOurMenu(event.getView().getTopInventory())) return;
        clearFor(event.getView(), event.getOldCursor());
        retagViewLater(event.getView());
    }

    /** Untag this material on both sides of the view so vanilla can merge. */
    private void clearFor(InventoryView view, ItemStack sample) {
        if (sample == null || sample.getType().isAir()) return;
        Material material = sample.getType();
        Inventory top = view.getTopInventory();
        if (!isOurMenu(top)) stripMatching(top, material);
        stripMatching(view.getBottomInventory(), material);
    }

    /** Chests and barrels get labels; crafting tables and furnaces don't. */
    private boolean isStorage(Inventory inventory) {
        if (inventory == null || isOurMenu(inventory)) return false;
        return switch (inventory.getType()) {
            case CHEST, BARREL, SHULKER_BOX, ENDER_CHEST, HOPPER, DISPENSER, DROPPER -> true;
            default -> false;
        };
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
            if (isStorage(top)) markAll(top);
            if (view.getPlayer() instanceof Player p) {
                p.updateInventory();
                updateTitle(p);
            }
        });
    }
}

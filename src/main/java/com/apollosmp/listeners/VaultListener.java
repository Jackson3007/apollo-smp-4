package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.vault.VaultHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/** Saves a vault the moment it's closed. */
public class VaultListener implements Listener {

    private final ApolloSMP plugin;

    public VaultListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof VaultHolder vault)) return;
        plugin.vaults().store(vault.owner(), vault.index(), event.getInventory().getContents());
    }
}

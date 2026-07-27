package com.apollosmp.vault;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marks an inventory as a personal vault so the close handler knows to save it. */
public class VaultHolder implements InventoryHolder {

    private final UUID owner;
    private final int index;
    private Inventory inventory;

    public VaultHolder(UUID owner, int index) {
        this.owner = owner;
        this.index = index;
    }

    public UUID owner() { return owner; }
    public int index() { return index; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}

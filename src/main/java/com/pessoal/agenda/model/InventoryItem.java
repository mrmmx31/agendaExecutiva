package com.pessoal.agenda.model;
public record InventoryItem(long id, String productName, int quantity, int minimumQuantity) {
    public boolean isLowStock() { return quantity <= minimumQuantity; }
}

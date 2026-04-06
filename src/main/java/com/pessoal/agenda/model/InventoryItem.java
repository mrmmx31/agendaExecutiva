package com.pessoal.agenda.model;
public record InventoryItem(long id, String productName, String itemType,
                            int quantity, int minimumQuantity,
                            double unitPrice, String category, String description) {
    public boolean isService()  { return itemType != null && "serviço".equalsIgnoreCase(itemType); }
    public boolean isLowStock() { return !isService() && quantity <= minimumQuantity; }
}

package com.pessoal.agenda.model;
import java.time.LocalDate;
public record SaleEntry(long id, String productName, String itemType, int quantity,
                        double unitPrice, LocalDate saleDate,
                        String clientName, String notes, String status) {
    public double total() { return quantity * unitPrice; }
    public boolean isPending()  { return "pendente".equalsIgnoreCase(status); }
    public boolean isService()  { return "serviço".equalsIgnoreCase(itemType); }
}

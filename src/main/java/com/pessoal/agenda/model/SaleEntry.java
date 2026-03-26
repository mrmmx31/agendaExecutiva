package com.pessoal.agenda.model;
import java.time.LocalDate;
public record SaleEntry(long id, String productName, int quantity, double unitPrice, LocalDate saleDate) {
    public double total() { return quantity * unitPrice; }
}

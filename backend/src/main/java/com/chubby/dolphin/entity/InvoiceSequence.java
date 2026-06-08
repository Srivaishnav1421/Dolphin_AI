package com.chubby.dolphin.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_sequences")
public class InvoiceSequence {

    @Id
    @Column(name = "year_key", length = 9)
    private String yearKey; // e.g., '2026-2027'

    @Column(name = "last_number", nullable = false)
    private Integer lastNumber = 0;

    public InvoiceSequence() {}

    public InvoiceSequence(String yearKey, Integer lastNumber) {
        this.yearKey = yearKey;
        this.lastNumber = lastNumber;
    }

    public String getYearKey() { return yearKey; }
    public void setYearKey(String yearKey) { this.yearKey = yearKey; }
    public Integer getLastNumber() { return lastNumber; }
    public void setLastNumber(Integer lastNumber) { this.lastNumber = lastNumber; }
}

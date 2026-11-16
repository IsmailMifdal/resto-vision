package com.restovision.stock;

import com.restovision.domain.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Un fichier de ventes déjà compté. Son empreinte empêche de le compter deux fois. */
@Entity
@Table(name = "rv_sales_import", uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "file_hash"}))
public class SalesImport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Restaurant restaurant;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Instant importedAt;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    protected SalesImport() {}

    public SalesImport(Restaurant restaurant, String fileHash, String fileName, int lineCount) {
        this.restaurant = restaurant;
        this.fileHash = fileHash;
        this.fileName = fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        this.lineCount = lineCount;
        this.importedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getFileName() { return fileName; }
    public Instant getImportedAt() { return importedAt; }
    public int getLineCount() { return lineCount; }
}

package com.restovision.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalesCsvTest {
    @Test
    void readsSemicolonFilesWithFrenchHeadersAndDates() {
        SalesCsv.Parsed parsed = SalesCsv.parse("\uFEFFDate;Produit;Quantité;Prix\r\n14/11/2026;Burger maison;3;45,00\r\n\r\n2026-11-14 12:31;Frites;2,5;9\n");

        assertThat(parsed.problems()).isEmpty();
        assertThat(parsed.lines()).containsExactly(
                new SalesCsv.Line(LocalDate.of(2026, 11, 14), "Burger maison", new BigDecimal("3")),
                new SalesCsv.Line(LocalDate.of(2026, 11, 14), "Frites", new BigDecimal("2.5")));
    }

    @Test
    void readsCommaFilesWithQuotedNamesInAnyColumnOrder() {
        SalesCsv.Parsed parsed = SalesCsv.parse("qty,item,date\n2,\"Salade, chèvre\",2026-11-14\n");

        assertThat(parsed.lines()).containsExactly(
                new SalesCsv.Line(LocalDate.of(2026, 11, 14), "Salade, chèvre", new BigDecimal("2")));
    }

    @Test
    void aBadLineIsReportedWithItsNumberAndTheRestIsKept() {
        SalesCsv.Parsed parsed = SalesCsv.parse("date;plat;quantite\nhier;Burger;1\n2026-11-14;Burger;zéro\n2026-11-14;Frites;4\n2026-11-14;Frites\n");

        assertThat(parsed.lines()).hasSize(1);
        assertThat(parsed.problems()).extracting(SalesCsv.Problem::line).containsExactly(2, 3, 5);
        assertThat(parsed.problems().get(0).message()).contains("Date illisible", "hier");
    }

    @Test
    void aFileWithoutTheThreeColumnsIsRefusedInPlainWords() {
        assertThatThrownBy(() -> SalesCsv.parse("a;b;c\n1;2;3\n"))
                .isInstanceOf(SalesCsv.UnreadableFile.class).hasMessageContaining("date, plat et quantite");
        assertThatThrownBy(() -> SalesCsv.parse("\n\n")).isInstanceOf(SalesCsv.UnreadableFile.class);
    }

    @Test
    void namesAreComparedWithoutAccentsOrCase() {
        assertThat(SalesCsv.key("  Salade de  CHÈVRE chaud ")).isEqualTo(SalesCsv.key("salade de chevre chaud"));
    }
}

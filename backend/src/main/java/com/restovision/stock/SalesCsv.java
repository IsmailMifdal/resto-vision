package com.restovision.stock;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture d'un export de ventes de caisse en CSV.
 *
 * Trois colonnes suffisent, dans n'importe quel ordre : la date, le plat, la quantité. La première ligne
 * donne leurs noms. Séparateur « ; » ou « , ». Les autres colonnes sont ignorées.
 */
public final class SalesCsv {
    private SalesCsv() {}

    public record Line(LocalDate date, String dish, BigDecimal quantity) {}

    /** Une ligne illisible : son numéro dans le fichier et ce qui ne va pas. */
    public record Problem(int line, String message) {}

    public record Parsed(List<Line> lines, List<Problem> problems) {}

    /** Le fichier entier est inutilisable (pas d'en-tête reconnu). Le message est destiné au gérant. */
    public static class UnreadableFile extends RuntimeException {
        public UnreadableFile(String message) {
            super(message);
        }
    }

    private static final Map<String, List<String>> HEADERS = Map.of(
            "date", List.of("date", "jour", "date de vente", "datevente"),
            "dish", List.of("plat", "produit", "article", "designation", "libelle", "product", "item", "name", "nom"),
            "quantity", List.of("quantite", "qte", "qty", "quantity", "nombre", "nb"));
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    public static Parsed parse(String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;  // marque laissée par Excel
        String[] rows = text.split("\\r?\\n");
        int headerRow = 0;
        while (headerRow < rows.length && rows[headerRow].isBlank()) {
            headerRow++;
        }
        if (headerRow >= rows.length) {
            throw new UnreadableFile("Le fichier est vide.");
        }
        char separator = rows[headerRow].chars().filter(c -> c == ';').count()
                >= rows[headerRow].chars().filter(c -> c == ',').count() ? ';' : ',';
        List<String> header = split(rows[headerRow], separator).stream().map(SalesCsv::key).toList();
        int date = column(header, "date"), dish = column(header, "dish"), quantity = column(header, "quantity");
        if (date < 0 || dish < 0 || quantity < 0) {
            throw new UnreadableFile("La première ligne doit nommer trois colonnes : date, plat et quantite.");
        }

        List<Line> lines = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (int i = headerRow + 1; i < rows.length; i++) {
            if (rows[i].isBlank()) {
                continue;
            }
            List<String> cells = split(rows[i], separator);
            if (cells.size() <= Math.max(date, Math.max(dish, quantity))) {
                problems.add(new Problem(i + 1, "Il manque des colonnes."));
                continue;
            }
            LocalDate day = date(cells.get(date));
            BigDecimal count = number(cells.get(quantity));
            String name = cells.get(dish).strip();
            if (day == null) {
                problems.add(new Problem(i + 1, "Date illisible : « " + cells.get(date).strip() + " »."));
            } else if (count == null || count.signum() <= 0) {
                problems.add(new Problem(i + 1, "Quantité illisible : « " + cells.get(quantity).strip() + " »."));
            } else if (name.isEmpty()) {
                problems.add(new Problem(i + 1, "Le nom du plat est vide."));
            } else {
                lines.add(new Line(day, name, count));
            }
        }
        return new Parsed(lines, problems);
    }

    /** Forme de comparaison d'un nom : sans accents, sans majuscules, espaces réduits. */
    public static String key(String value) {
        String plain = Normalizer.normalize(value.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static int column(List<String> header, String kind) {
        for (int i = 0; i < header.size(); i++) {
            if (HEADERS.get(kind).contains(header.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static LocalDate date(String raw) {
        String value = raw.strip();
        if (value.length() > 10) {
            value = value.substring(0, 10);  // « 2026-11-14 12:31 » : l'heure ne sert pas au stock
        }
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return null;
    }

    private static BigDecimal number(String raw) {
        try {
            return new BigDecimal(raw.strip().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Découpe une ligne, en respectant les guillemets : "Salade, chèvre" reste une seule cellule. */
    private static List<String> split(String row, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == separator && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}

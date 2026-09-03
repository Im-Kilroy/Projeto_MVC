package br.com.renata.model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record Product(
        int id,
        String nome,
        int quantidade,
        String fabricacao,
        String validade,
        boolean status,
        String responsavel,
        String validadeStatus
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String json() {
        return String.format(Locale.ROOT,
                "{\"id\":%d,\"nome\":\"%s\",\"quantidade_produtos\":%d,\"data_fabricacao\":\"%s\","
                        + "\"data_validade\":\"%s\",\"status\":%s,\"responsavel\":\"%s\","
                        + "\"validade_status\":\"%s\",\"status_label\":\"%s\"}",
                id, jsonEscape(nome), quantidade, fabricacao, validade, status, jsonEscape(responsavel),
                validadeStatus, status ? "Em estoque" : "Indisponível");
    }

    public static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public static String validity(String value, LocalDate today) {
        LocalDate expiration = LocalDate.parse(value, DATE);
        if (expiration.isBefore(today)) return "vencido";
        if (!expiration.isAfter(today.plusMonths(3))) return "proximo";
        return "longe";
    }

    public static String label(String status) {
        return switch (status) {
            case "vencido" -> "vencido";
            case "proximo" -> "próximo do vencimento";
            default -> "validade segura";
        };
    }
}

package br.com.renata.service;

import br.com.renata.model.Product;
import br.com.renata.repository.StockRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StockService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockRepository repository;

    public StockService(StockRepository repository) {
        this.repository = repository;
    }

    public List<Product> products(String search) {
        LocalDate today = LocalDate.now();
        return repository.find(search).stream().map(product -> new Product(
                product.id(),
                product.nome(),
                product.quantidade(),
                product.fabricacao(),
                product.validade(),
                product.status(),
                product.responsavel(),
                validity(product.validade(), today)
        )).toList();
    }

    public String answer(String question) {
        String text = normalize(question);
        if (text.isBlank()) {
            return "Olá, sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?";
        }

        if (text.equals("oi") || text.equals("ola") || text.equals("olá") || text.startsWith("oi ") || text.startsWith("ola ") || text.startsWith("olá ") || text.equals("bom dia") || text.equals("boa tarde") || text.equals("boa noite") || text.startsWith("bom dia ") || text.startsWith("boa tarde ") || text.startsWith("boa noite ")) {
            return "Olá! Sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?";
        }

        List<Product> products = products("");

        if (text.contains("vencid") || text.contains("vencido") || text.contains("expirado") || text.contains("fora da validade")) {
            List<Product> expired = products.stream().filter(product -> "vencido".equals(product.validadeStatus())).toList();
            if (expired.isEmpty()) return "Não há produtos vencidos no estoque no momento.";
            return "Produtos vencidos: " + expired.stream().map(product -> product.nome() + " (" + product.validade() + ")").collect(Collectors.joining(", ")) + ".";
        }

        if (text.contains("na validade") || text.contains("dentro da validade") || text.contains("validade segura") || text.contains("vigente") || text.contains("validos") || text.contains("válidos")) {
            List<Product> valid = products.stream().filter(product -> "longe".equals(product.validadeStatus())).toList();
            if (valid.isEmpty()) return "Não há produtos dentro da validade no momento.";
            return "Produtos na validade: " + valid.stream().map(product -> product.nome() + " (" + product.validade() + ")").collect(Collectors.joining(", ")) + ".";
        }

        if (text.contains("proximo") || text.contains("perto") || text.contains("3 meses") || text.contains("três meses") || text.contains("próximo") || text.contains("próximos") || text.contains("near")) {
            List<Product> near = products.stream().filter(product -> "proximo".equals(product.validadeStatus())).toList();
            if (near.isEmpty()) return "Não há produtos próximos do vencimento dentro de 3 meses.";
            return "Produtos próximos do vencimento: " + near.stream().map(product -> product.nome() + " (" + product.validade() + ")").collect(Collectors.joining(", ")) + ".";
        }

        if ((text.contains("todos") && text.contains("respons")) || text.contains("responsaveis")) {
            List<String> responsaveis = products.stream().map(Product::responsavel).distinct().sorted().toList();
            if (responsaveis.isEmpty()) return "Não há responsáveis cadastrados no mercado.";
            return "Responsáveis do mercado: " + String.join(", ", responsaveis) + ".";
        }

        if (text.contains("falta") || text.contains("em falta") || text.contains("sem estoque") || text.contains("fora de estoque")) {
            List<Product> missing = products.stream().filter(product -> product.quantidade() <= 0).toList();
            if (missing.isEmpty()) return "Não há mercadorias em falta no momento.";
            return "Mercadorias em falta: " + missing.stream().map(Product::nome).collect(Collectors.joining(", ")) + ".";
        }

        Matcher lessThanMatcher = Pattern.compile("menos de (\\d+)").matcher(text);
        if (lessThanMatcher.find()) {
            int limit = Integer.parseInt(lessThanMatcher.group(1));
            List<Product> lessThan = products.stream().filter(product -> product.quantidade() < limit).toList();
            if (lessThan.isEmpty()) return "Não há mercadorias com menos de " + limit + " unidades.";
            return "Mercadorias com menos de " + limit + " unidades: "
                    + lessThan.stream().map(product -> product.nome() + " (" + product.quantidade() + ")")
                    .collect(Collectors.joining(", ")) + ".";
        }

        Matcher moreThanMatcher = Pattern.compile("mais de (\\d+)").matcher(text);
        if (moreThanMatcher.find()) {
            int limit = Integer.parseInt(moreThanMatcher.group(1));
            List<Product> moreThan = products.stream().filter(product -> product.quantidade() > limit).toList();
            if (moreThan.isEmpty()) return "Não há mercadorias com mais de " + limit + " unidades.";
            return "Mercadorias com mais de " + limit + " unidades: "
                    + moreThan.stream().map(product -> product.nome() + " (" + product.quantidade() + ")")
                    .collect(Collectors.joining(", ")) + ".";
        }

        if (text.contains("atencao") || text.contains("atenção") || text.contains("alerta") || text.contains("validade") || text.contains("venc")) {
            List<Product> attention = products.stream().filter(product -> !"longe".equals(product.validadeStatus())).toList();
            if (attention.isEmpty()) return "Não há mercadorias vencidas ou próximas do vencimento.";
            return "Mercadorias que precisam de atenção: " + attention.stream()
                    .map(product -> product.nome() + " (" + product.validade() + ", " + label(product.validadeStatus()) + ")")
                    .collect(Collectors.joining("; ")) + ".";
        }

        for (Product product : products) {
            if (text.contains(normalize(product.nome()))) {
                if (text.contains("respons")) {
                    return String.format("O responsável pelo produto %s é %s.", product.nome(), product.responsavel());
                }
                return String.format("Nome: %s | ID: %02d | Quantidade: %d | Fabricação: %s | Validade: %s | Status: %s | Responsável: %s.",
                        product.nome(), product.id(), product.quantidade(), product.fabricacao(), product.validade(),
                        product.status() ? "em estoque" : "indisponível", product.responsavel());
            }
        }

        for (Product product : products) {
            if (text.contains(normalize(product.responsavel()))) {
                return "Produtos do responsável " + product.responsavel() + ": "
                        + products.stream()
                        .filter(item -> normalize(item.responsavel()).contains(normalize(product.responsavel())))
                        .map(item -> item.nome())
                        .collect(Collectors.joining(", ")) + ".";
            }
        }

        return "Posso pesquisar mercadorias, verificar validades, informar o responsável por um produto, listar responsáveis, mostrar itens em falta e produtos com pouca ou muita quantidade. Pergunte, por exemplo: \"qual a situação do Café?\" ou \"quem é o responsável pelo Pão?\"";
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String validity(String value, LocalDate today) {
        LocalDate expiration = LocalDate.parse(value, DATE);
        if (expiration.isBefore(today)) return "vencido";
        if (!expiration.isAfter(today.plusMonths(3))) return "proximo";
        return "longe";
    }

    private static String label(String status) {
        return switch (status) {
            case "vencido" -> "vencido";
            case "proximo" -> "próximo do vencimento";
            default -> "validade segura";
        };
    }
}

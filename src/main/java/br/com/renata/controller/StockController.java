package br.com.renata.controller;

import br.com.renata.model.Product;
import br.com.renata.service.StockService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StockController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockService service;
    private final String geminiApiKey;

    public StockController(StockService service, String geminiApiKey) {
        this.service = service;
        this.geminiApiKey = geminiApiKey;
    }

    public void products(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"erro\":\"Método não permitido.\"}");
            return;
        }
        Map<String, String> query = query(exchange.getRequestURI());
        String search = query.getOrDefault("busca", "");
        String body = "{\"hoje\":\"" + LocalDate.now() + "\",\"mercadorias\":["
                + service.products(search).stream().map(Product::json).collect(Collectors.joining(",")) + "]}";
        send(exchange, 200, "application/json", body);
    }

    public void assistant(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"erro\":\"Método não permitido.\"}");
            return;
        }

        String question = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String answer = (geminiApiKey != null && !geminiApiKey.isBlank()) ? askGemini(question, geminiApiKey) : service.answer(question);
        send(exchange, 200, "application/json", "{\"resposta\":\"" + jsonEscape(answer) + "\"}");
    }

    public void pdfReport(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"erro\":\"Método não permitido.\"}");
            return;
        }
        List<Product> products = service.products("");
        byte[] pdf = buildPdfReport(products);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"relatorio-estoque.pdf\"");
        exchange.sendResponseHeaders(200, pdf.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(pdf);
        }
    }

    public void staticFile(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if ("/".equals(requested)) requested = "/index.html";
        if (requested.contains("..") || requested.contains("\\")) {
            send(exchange, 404, "text/plain", "Página não encontrada.");
            return;
        }
        String resourceName = "web/" + requested.substring(1);
        try (InputStream resource = StockController.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (resource == null) {
                send(exchange, 404, "text/plain", "Página não encontrada.");
                return;
            }
            byte[] content = resource.readAllBytes();
            String contentType = requested.endsWith(".css") ? "text/css"
                    : requested.endsWith(".js") ? "text/javascript" : "text/html";
            send(exchange, 200, contentType, content);
        }
    }

    private String askGemini(String question, String apiKey) {
        try {
            List<Product> products = service.products("");
            String productsContext = products.stream()
                    .map(product -> String.format(Locale.ROOT,
                            "- %s | id=%d | quantidade=%d | fabricacao=%s | validade=%s | status=%s | responsavel=%s",
                            product.nome(), product.id(), product.quantidade(), product.fabricacao(),
                            product.validade(), product.status() ? "em estoque" : "indisponível", product.responsavel()))
                    .collect(Collectors.joining("\n"));

            String prompt = "Você é Renatinha IA, assistente inteligente especializada em controle de estoque do mercado da Renata. "
                    + "Sua função é responder com base nos dados do banco de estoque e ajudar o usuário em consultas sobre produtos, validade, quantidade, status e responsáveis. "
                    + "Você deve usar o arquivo de dados do banco mercado_renata.sql como fonte de verdade. "
                    + "Responda somente dentro do contexto do mercado e estoque. "
                    + "Se a pergunta pedir vencidos, na validade, perto da validade (até 3 meses), em falta, com menos de X unidades, com mais de X unidades, responsáveis, ou dados do produto, responda com precisão. "
                    + "Classifique corretamente: vencidos, próximos do vencimento, e na validade. "
                    + "Se a chave do Gemini não estiver disponível, use o modo local de fallback sem quebrar a conversa. "
                    + "Use no máximo 2 parágrafos. "
                    + "Dados do estoque:\n" + productsContext + "\n\nPergunta do usuário: " + question;

            String body = "{"
                    + "\"contents\":[{\"parts\":[{\"text\":\"" + jsonEscape(prompt) + "\"}]}],"
                    + "\"generationConfig\":{\"temperature\":0.2}"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return service.answer(question);
            }

            Matcher matcher = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
                    .matcher(response.body());
            if (matcher.find()) {
                String result = matcher.group(1)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
                return result.isBlank() ? service.answer(question) : result;
            }
        } catch (Exception ignored) {
            // fallback to local logic when the external API is unavailable
        }
        return service.answer(question);
    }

    public byte[] buildPdfReport(List<Product> products) {
        float currentY = 780f;
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            document.addPage(page);
            var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
            stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
            stream.beginText();
            stream.newLineAtOffset(72f, currentY);
            stream.showText("----------------------------------------------");
            currentY -= 20f;
            stream.newLineAtOffset(0, -20f);
            stream.showText("      ESTOQUE DO MERCADINHO DA RENATA");
            currentY -= 20f;
            stream.newLineAtOffset(0, -20f);
            stream.showText("----------------------------------------------");
            currentY -= 28f;
            stream.newLineAtOffset(0, -28f);
            stream.showText("Data: " + LocalDate.now().format(DATE));
            currentY -= 24f;

            boolean firstProduct = true;
            for (Product product : products) {
                if (currentY < 120f) {
                    stream.endText();
                    stream.close();
                    var newPage = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                    document.addPage(newPage);
                    stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, newPage);
                    stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    stream.beginText();
                    currentY = 780f;
                    stream.newLineAtOffset(72f, currentY);
                }

                if (!firstProduct) {
                    stream.newLineAtOffset(0, -18f);
                    stream.showText("----------------------------------------------");
                    currentY -= 18f;
                }
                firstProduct = false;

                stream.newLineAtOffset(0, -18f);
                stream.showText("Produto: " + ascii(product.nome()));
                currentY -= 18f;
                stream.newLineAtOffset(0, -18f);
                stream.showText("Quantidade de Produtos: " + product.quantidade());
                currentY -= 18f;
                stream.newLineAtOffset(0, -18f);
                stream.showText("Status: " + (product.status() ? "em estoque" : "indisponível"));
                currentY -= 18f;
                stream.newLineAtOffset(0, -18f);
                stream.showText("Responsável: " + ascii(product.responsavel()));
                currentY -= 18f;
                stream.newLineAtOffset(0, -18f);
                stream.showText("Data de Validade: " + product.validade());
                currentY -= 18f;
                stream.newLineAtOffset(0, -18f);
                stream.showText("Data de Fabricação: " + product.fabricacao());
                currentY -= 18f;
            }

            stream.endText();
            stream.close();

            try (var output = new java.io.ByteArrayOutputStream()) {
                document.save(output);
                return output.toByteArray();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o relatório PDF do estoque.", e);
        }
    }

    private static String ascii(String value) {
        return value == null ? "" : value.replace("ã", "a").replace("á", "a").replace("à", "a")
                .replace("é", "e").replace("ê", "e").replace("í", "i").replace("ó", "o")
                .replace("õ", "o").replace("ú", "u").replace("ç", "c");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        send(exchange, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> query(URI uri) {
        if (uri.getRawQuery() == null) return Map.of();
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> decode(pair[0]), pair -> decode(pair[1]), (a, b) -> b));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}

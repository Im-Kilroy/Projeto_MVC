package br.com.renata;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Application {
    private static final int PORT = 8080;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String GEMINI_API_KEY = resolveGeminiKey();
    private static final Repository repository = new Repository();
    private static final Service service = new Service(repository);

    private static String resolveGeminiKey() {
        String[] candidates = {"GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GEMINI_API_KEY", "GEMINI_KEY"};
        for (String key : candidates) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/mercadorias", Application::products);
        server.createContext("/api/agente", Application::assistant);
        server.createContext("/api/relatorio-pdf", Application::pdfReport);
        server.createContext("/", Application::staticFile);
        server.start();
        System.out.printf("Mercado da Renata disponível em http://localhost:%d%n", PORT);
    }

    private static void products(HttpExchange exchange) throws IOException {
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

    private static void assistant(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"erro\":\"Método não permitido.\"}");
            return;
        }
        String question = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String answer = (GEMINI_API_KEY != null && !GEMINI_API_KEY.isBlank()) ? askGemini(question, GEMINI_API_KEY) : service.answer(question);
        send(exchange, 200, "application/json", "{\"resposta\":\"" + jsonEscape(answer) + "\"}");
    }

    private static String askGemini(String question, String apiKey) {
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

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return service.answer(question);
            }

            Matcher matcher = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*?)\\\"")
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

    private static void pdfReport(HttpExchange exchange) throws IOException {
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

    private static void staticFile(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if ("/".equals(requested)) requested = "/index.html";
        if (requested.contains("..") || requested.contains("\\")) {
            send(exchange, 404, "text/plain", "Página não encontrada.");
            return;
        }
        String resourceName = "web/" + requested.substring(1);
        try (InputStream resource = Application.class.getClassLoader().getResourceAsStream(resourceName)) {
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

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        send(exchange, status, type, bytes);
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

    static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static byte[] buildPdfReport(List<Product> products) {
        try (PDDocument document = new PDDocument()) {
            float marginLeft = 24f;
            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float rowHeight = 20f;
            float headerHeight = 18f;
            float[] colWidths = {130f, 72f, 72f, 58f, 120f};
            float tableWidth = sum(colWidths);
            float startX = Math.max(marginLeft, (pageWidth - tableWidth) / 2f);

            List<Product> rows = products == null ? List.of() : products;
            if (rows.isEmpty()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDPageContentStream stream = new PDPageContentStream(document, page);
                drawPdfTitle(stream, startX, pageHeight - 48f, "estoque do mercadinho da renata.");
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.beginText();
                stream.newLineAtOffset(startX, pageHeight - 90f);
                stream.showText("Nenhum produto cadastrado no estoque.");
                stream.endText();
                stream.close();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                document.save(output);
                return output.toByteArray();
            }

            int index = 0;
            while (index < rows.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDPageContentStream stream = new PDPageContentStream(document, page);
                drawPdfTitle(stream, startX, pageHeight - 48f, "estoque do mercadinho da renata.");

                float currentY = pageHeight - 90f;
                drawPdfHeader(stream, startX, currentY, colWidths, headerHeight);
                currentY -= headerHeight;

                int rowsInPage = 0;
                int maxRowsPerPage = Math.max(1, (int) ((pageHeight - 140f) / (rowHeight * 2f)));
                while (index < rows.size() && rowsInPage < maxRowsPerPage) {
                    drawPdfRow(stream, startX, currentY, rowHeight, colWidths, rows.get(index++));
                    currentY -= rowHeight;
                    rowsInPage++;
                }
                stream.close();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar o relatório PDF.", e);
        }
    }

    private static void drawPdfTitle(PDPageContentStream stream, float left, float topY, String title) throws IOException {
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 15);
        stream.setNonStrokingColor(new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
        stream.beginText();
        stream.newLineAtOffset(left + 110f, topY);
        stream.showText(title);
        stream.endText();
    }

    private static void drawPdfHeader(PDPageContentStream stream, float left, float y, float[] colWidths, float headerHeight) throws IOException {
        stream.setNonStrokingColor(new PDColor(new float[] {0.87f, 0.87f, 0.87f}, PDDeviceRGB.INSTANCE));
        stream.addRect(left, y - headerHeight, sum(colWidths), headerHeight);
        stream.fill();

        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8);
        stream.setNonStrokingColor(new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));

        String[] headers = {"produto", "fabricação", "validade", "quantidade", "responsável"};
        float x = left;
        for (int i = 0; i < headers.length; i++) {
            stream.beginText();
            stream.newLineAtOffset(x + 4f, y - 12f);
            stream.showText(headers[i]);
            stream.endText();
            x += colWidths[i];
        }

        stream.setNonStrokingColor(new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
    }

    private static void drawPdfRow(PDPageContentStream stream, float left, float y, float rowHeight, float[] colWidths, Product product) throws IOException {
        float x = left;
        String[] values = {
                ascii(product.nome()),
                ascii(product.fabricacao()),
                ascii(product.validade()),
                String.valueOf(product.quantidade()),
                ascii(product.responsavel())
        };

        for (int i = 0; i < values.length; i++) {
            float width = colWidths[i];
            if (i == 2) {
                stream.setNonStrokingColor(pdfColor(product.validadeStatus()));
                stream.addRect(x, y - rowHeight, width, rowHeight);
                stream.fill();
                stream.setNonStrokingColor(new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
            } else {
                stream.setNonStrokingColor(new PDColor(new float[] {0.98f, 0.98f, 0.98f}, PDDeviceRGB.INSTANCE));
                stream.addRect(x, y - rowHeight, width, rowHeight);
                stream.fill();
                stream.setNonStrokingColor(new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE));
            }

            stream.setStrokingColor(new PDColor(new float[] {0.25f, 0.25f, 0.25f}, PDDeviceRGB.INSTANCE));
            stream.addRect(x, y - rowHeight, width, rowHeight);
            stream.stroke();

            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7);
            stream.beginText();
            stream.newLineAtOffset(x + 3f, y - 13f);
            stream.showText(truncate(values[i], i == 0 ? 18 : 20));
            stream.endText();

            x += width;
        }
    }

    private static float sum(float[] values) {
        float total = 0f;
        for (float value : values) {
            total += value;
        }
        return total;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        String clean = ascii(value).trim();
        if (clean.length() <= maxLength) return clean;
        return clean.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private static String ascii(String value) {
        return value == null ? "" : value.replace("\u00A0", " ");
    }

    private static PDColor pdfColor(String status) {
        return switch (status) {
            case "vencido" -> new PDColor(new float[] {0.93f, 0.13f, 0f}, PDDeviceRGB.INSTANCE);
            case "proximo" -> new PDColor(new float[] {1f, 0.87f, 0.13f}, PDDeviceRGB.INSTANCE);
            default -> new PDColor(new float[] {0f, 0.5f, 0f}, PDDeviceRGB.INSTANCE);
        };
    }

    record Product(int id, String nome, int quantidade, String fabricacao, String validade,
                   boolean status, String responsavel, String validadeStatus) {
        String json() {
            return String.format(Locale.ROOT,
                    "{\"id\":%d,\"nome\":\"%s\",\"quantidade_produtos\":%d,\"data_fabricacao\":\"%s\","
                            + "\"data_validade\":\"%s\",\"status\":%s,\"responsavel\":\"%s\","
                            + "\"validade_status\":\"%s\",\"status_label\":\"%s\"}",
                    id, jsonEscape(nome), quantidade, fabricacao, validade, status, jsonEscape(responsavel),
                    validadeStatus, status ? "Em estoque" : "Indisponível");
        }
    }

    static class Database {
        private static final String URL = "jdbc:h2:file:./data/mercado_renata;MODE=LEGACY;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
        private static final String USER = "sa";
        private static final String PASSWORD = "";

        Database() {
            initialize();
        }

        Connection connection() throws SQLException {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }

        private void initialize() {
            try (Connection connection = connection()) {
                executeScript(connection, loadScript());
            } catch (SQLException | IOException e) {
                throw new IllegalStateException("Não foi possível inicializar o banco de dados do estoque.", e);
            }
        }

        private void executeScript(Connection connection, String script) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    String normalized = sql.trim();
                    if (!normalized.isEmpty() && !normalized.startsWith("SELECT ")) {
                        statement.execute(normalized);
                    }
                }
            }
        }

        private String loadScript() throws IOException {
            InputStream input = Application.class.getClassLoader().getResourceAsStream("db/mercado_renata.sql");
            if (input == null) {
                throw new IllegalStateException("Arquivo SQL do banco não encontrado em src/main/resources/db/mercado_renata.sql");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static class Repository {
        private final Database database;

        Repository() {
            this.database = new Database();
        }

        List<ProductSeed> find(String search) {
            String normalized = search.toLowerCase(Locale.ROOT).trim();
            String sql = normalized.isBlank()
                    ? "SELECT id, nome, quantidade_produtos, data_fabricacao, data_validade, status, responsavel FROM mercadorias ORDER BY id"
                    : "SELECT id, nome, quantidade_produtos, data_fabricacao, data_validade, status, responsavel FROM mercadorias WHERE LOWER(nome) LIKE ? ORDER BY id";

            try (Connection connection = database.connection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                if (!normalized.isBlank()) {
                    statement.setString(1, "%" + normalized + "%");
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    List<ProductSeed> products = new ArrayList<>();
                    while (resultSet.next()) {
                        products.add(new ProductSeed(
                                resultSet.getInt("id"),
                                resultSet.getString("nome"),
                                resultSet.getInt("quantidade_produtos"),
                                resultSet.getString("data_fabricacao"),
                                resultSet.getString("data_validade"),
                                resultSet.getBoolean("status"),
                                resultSet.getString("responsavel")));
                    }
                    return products;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Não foi possível consultar os produtos no banco de dados.", e);
            }
        }
    }

    record ProductSeed(int id, String nome, int quantidade, String fabricacao, String validade,
                       boolean status, String responsavel) {
    }

    static class Service {
        private final Repository repository;
        Service(Repository repository) { this.repository = repository; }

        List<Product> products(String search) {
            LocalDate today = LocalDate.now();
            return repository.find(search).stream().map(product -> new Product(
                    product.id(), product.nome(), product.quantidade(), product.fabricacao(), product.validade(),
                    product.status(), product.responsavel(), validity(product.validade(), today))).toList();
        }

        String answer(String question) {
            String text = normalize(question);
            if (text.isBlank()) {
                return "Olá, sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?";
            }

            if (text.equals("oi") || text.equals("ola") || text.equals("olá") || text.startsWith("oi ") || text.startsWith("ola ") || text.startsWith("olá ") || text.equals("bom dia") || text.equals("boa tarde") || text.equals("boa noite") || text.startsWith("bom dia ") || text.startsWith("boa tarde ") || text.startsWith("boa noite ")) {
                return "Olá! Sou a Renatinha IA, sua amiguinha, o que deseja saber sobre o estoque?";
            }

            List<Product> products = products("");
            boolean mentionsKnownProduct = products.stream()
                    .anyMatch(product -> text.contains(normalize(product.nome())) || text.contains(normalize(product.responsavel())));

            if (text.contains("quantos") || text.contains("quantidade") || text.contains("total") && (text.contains("venc") || text.contains("validade") || text.contains("respons"))) {
                if ((text.contains("venc") || text.contains("expir") || text.contains("validade")) && !text.contains("respons")) {
                    long expired = products.stream().filter(product -> "vencido".equals(product.validadeStatus())).count();
                    long near = products.stream().filter(product -> "proximo".equals(product.validadeStatus())).count();
                    long valid = products.stream().filter(product -> "longe".equals(product.validadeStatus())).count();
                    return String.format(Locale.ROOT,
                            "Resumo de validade: %d vencidos, %d próximos do vencimento e %d na validade.",
                            expired, near, valid);
                }

                if (text.contains("respons")) {
                    String responsibleName = products.stream()
                            .map(Product::responsavel)
                            .filter(name -> text.contains(normalize(name)))
                            .findFirst()
                            .orElse(null);
                    if (responsibleName != null) {
                        long count = products.stream()
                                .filter(product -> normalize(product.responsavel()).contains(normalize(responsibleName)))
                                .count();
                        return String.format("O responsável %s tem %d produto(s) no mercado.", responsibleName, count);
                    }
                }
            }

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
                List<String> responsaveis = products.stream()
                        .map(Product::responsavel)
                        .distinct()
                        .sorted()
                        .toList();
                if (responsaveis.isEmpty()) {
                    return "Não há responsáveis cadastrados no mercado.";
                }
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
                    .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
}

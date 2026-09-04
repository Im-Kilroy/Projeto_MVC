package br.com.renata;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationTest {

    @Test
    void pdfReportShouldFollowRequiredBusinessTemplate() throws Exception {
        Method method = Application.class.getDeclaredMethod("buildPdfReport", List.class);
        method.setAccessible(true);

        byte[] pdf = (byte[]) method.invoke(null, List.of(
                new Application.Product(1, "Pão", 10, "12/10/2025", "01/02/2033", true, "Seu Zé", "longe")
        ));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String report = new PDFTextStripper().getText(document);

            assertTrue(report.contains("estoque do mercadinho da renata."), "O PDF deve conter o título principal do estoque.");
            assertTrue(report.contains("quantidade"), "O PDF deve incluir a coluna de quantidade.");
            assertTrue(report.contains("validade"), "O PDF deve incluir a coluna de validade.");
            assertTrue(report.contains("fabricação"), "O PDF deve incluir a coluna de fabricação.");
            assertTrue(report.contains("responsável"), "O PDF deve incluir a coluna de responsável.");
            assertTrue(report.contains("Pão"), "O PDF deve incluir o nome do produto.");
            assertTrue(report.contains("10"), "O PDF deve incluir a quantidade do produto.");
            assertTrue(report.contains("Seu Zé"), "O PDF deve manter o texto em português com acentos.");
            assertTrue(report.contains("01/02/2033"), "O PDF deve manter a data de validade no relatório.");
        }
    }

    @Test
    void pdfReportShouldCreateMultiplePagesWhenNeeded() throws Exception {
        Method method = Application.class.getDeclaredMethod("buildPdfReport", List.class);
        method.setAccessible(true);

        List<Application.Product> products = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new Application.Product(
                        i,
                        "Produto " + i,
                        5,
                        "01/01/2024",
                        "15/11/2027",
                        true,
                        "Responsável " + i,
                        "longe"))
                .toList();

        byte[] pdf = (byte[]) method.invoke(null, products);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1, "O PDF deve criar páginas adicionais quando a lista exceder o espaço da primeira página.");
        }
    }

    @Test
    void agentShouldReturnProductInfoWhenQuestionMentionsProductName() {
        Application.Service service = new Application.Service(new Application.Repository());
        String answer = service.answer("pão");

        assertTrue(answer.contains("Pão") || answer.contains("Nome:"), "O agente deve retornar informações do produto quando a pergunta menciona o nome do produto.");
    }

    @Test
    void repositoryShouldUseSqlDatabaseTableForProducts() throws Exception {
        Application.Database database = new Application.Database();
        try (var connection = database.connection()) {
            var result = connection.prepareStatement("SELECT COUNT(*) FROM mercadorias").executeQuery();
            result.next();
            assertTrue(result.getInt(1) >= 5, "A tabela mercadorias deve existir e conter os produtos iniciais cadastrados em SQL.");
        }
    }

    @Test
    void agentShouldAnswerGreetingWithoutMarketContext() {
        Application.Service service = new Application.Service(new Application.Repository());
        String answer = service.answer("oi");

        assertTrue(answer.toLowerCase().contains("olá") || answer.toLowerCase().contains("oi") || answer.toLowerCase().contains("estoque"),
                "O agente deve responder ao cumprimento mesmo sem uma pergunta de mercado.");
        assertTrue(answer.toLowerCase().contains("renatinha ia"), "A assistente deve se identificar como Renatinha IA.");
        assertFalse(answer.toLowerCase().contains("renatinha bot"), "A assistente local Renatinha Bot não deve mais existir.");
    }

    @Test
    void agentShouldCountProductsByValidityStatus() {
        Application.Service service = new Application.Service(new Application.Repository());
        String answer = service.answer("quantos produtos estão vencidos perto da validade e válidos");

        assertTrue(answer.toLowerCase().contains("vencidos") && answer.toLowerCase().contains("próximos") && answer.toLowerCase().contains("validade"),
                "O agente deve retornar contagens por categoria de validade.");
    }

    @Test
    void agentShouldCountProductsByResponsible() {
        Application.Service service = new Application.Service(new Application.Repository());
        String answer = service.answer("quantos produtos o Seu Zé tem");

        assertTrue(answer.toLowerCase().contains("seu zé") || answer.toLowerCase().contains("produto") || answer.toLowerCase().contains("responsável"),
                "O agente deve responder com a quantidade de produtos de um responsável.");
    }
}

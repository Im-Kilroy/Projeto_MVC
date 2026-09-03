package br.com.renata.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:h2:file:./data/mercado_renata;MODE=LEGACY;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public Database() {
        initialize();
    }

    public Connection connection() throws SQLException {
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
        InputStream input = Database.class.getClassLoader().getResourceAsStream("db/mercado_renata.sql");
        if (input == null) {
            throw new IllegalStateException("Arquivo SQL do banco não encontrado em src/main/resources/db/mercado_renata.sql");
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

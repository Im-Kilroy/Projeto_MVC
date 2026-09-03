package br.com.renata.repository;

import br.com.renata.model.ProductSeed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StockRepository {
    private final Database database;

    public StockRepository(Database database) {
        this.database = database;
    }

    public List<ProductSeed> find(String search) {
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

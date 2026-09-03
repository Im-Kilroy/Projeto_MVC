package br.com.renata.model;

public record ProductSeed(
        int id,
        String nome,
        int quantidade,
        String fabricacao,
        String validade,
        boolean status,
        String responsavel
) {
}

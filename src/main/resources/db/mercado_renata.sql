CREATE TABLE IF NOT EXISTS mercadorias (
    id INT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    quantidade_produtos INT NOT NULL,
    data_fabricacao VARCHAR(20) NOT NULL,
    data_validade VARCHAR(20) NOT NULL,
    status BOOLEAN NOT NULL,
    responsavel VARCHAR(255) NOT NULL
);

MERGE INTO mercadorias (id, nome, quantidade_produtos, data_fabricacao, data_validade, status, responsavel) KEY(id) VALUES
    (0, 'Pão', 10, '30/10/1997', '02/09/2026', TRUE, 'Seu Zé'),
    (1, 'Macarrão', 15, '01/02/2025', '30/11/2027', TRUE, 'Seu Camilo'),
    (2, 'Café', 0, '01/01/2020', '30/08/2028', FALSE, 'Seu Lopez'),
    (3, 'Cerveja', 10, '01/01/2020', '12/12/2020', TRUE, 'Seu Lopez'),
    (4, 'Carne', 5, '01/01/2020', '10/11/2026', TRUE, 'Seu Camilo'),
    (5, 'Pasta', 90, '01/01/2000', '10/11/2010', TRUE, 'Seu Zé'),
    (6, 'Cabo Verde', 11, '01/01/2000', '10/11/2030', TRUE, 'Seu Antonio'),
    (7, 'Cabo Azul', 11, '01/01/2000', '10/11/2004', TRUE, 'Seu Camilo'),
    (8, 'Papiro', 11, '01/01/2000', '10/11/2100', TRUE, 'Seu Coisinha'),
    (9, 'Pedra', 11, '01/01/1980', '10/11/1997', TRUE, 'Seu Coisinha'),
    (10, 'Meia', 11, '01/01/2020', '10/11/2040', TRUE, 'Seu Coisinha'),
    (11, 'Chocolate', 11, '01/01/2002', '10/11/2010', TRUE, 'Seu Anderson'),
    (99, 'Morango', 40, '01/01/2026', '10/11/2026', TRUE, 'Seu Zé'),
    (12, 'Guaraná', 40, '01/01/2026', '10/11/2027', TRUE, 'Seu Anderson'),
    (13, 'Pirulito', 0, '01/01/2026', '10/11/2028', FALSE, 'Seu Anderson'),
    (14, 'Brócolis', 100, '01/01/2026', '20/10/2026', TRUE, 'Seu Zé'),
    (15, 'Alface', 0, '01/01/2026', '20/10/2028', FALSE, 'Seu Anderson');
  

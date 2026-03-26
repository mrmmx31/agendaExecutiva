package com.pessoal.agenda.model;
import java.util.List;
public final class TaskCategory {
    public static final List<String> VALUES = List.of(
        "Geral", "Pesquisa", "Experimento", "Leitura",
        "Escrita", "Reuniao", "Administracao", "Saude", "Pessoal", "Projeto"
    );
    private TaskCategory() {}
}

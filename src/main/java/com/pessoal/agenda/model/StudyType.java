package com.pessoal.agenda.model;

/** Tipo do plano de estudo — orienta o comportamento do progresso e da exibição. */
public enum StudyType {
    LIVRO          ("Livro"),
    ARTIGO         ("Artigo Científico"),
    CURSO          ("Curso / Treinamento"),
    EXPERIMENTO    ("Experimento"),
    REVISAO        ("Revisão Bibliográfica"),
    GERAL          ("Geral");

    private final String label;
    StudyType(String label) { this.label = label; }
    public String label()   { return label; }
    @Override public String toString() { return label; }
}


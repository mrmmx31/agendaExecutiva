package com.pessoal.agenda.service;

import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.StudyType;
import com.pessoal.agenda.model.CategoryDomain;
import com.pessoal.agenda.repository.CategoryRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Regras de negocio para gerenciamento de categorias personalizadas.
 */
public class CategoryService {

    private final CategoryRepository repo;

    public CategoryService(CategoryRepository repo) { this.repo = repo; }

    public List<Category> list(CategoryDomain domain) {
        return repo.findByDomain(domain);
    }

    /** Retorna somente os nomes, para preencher ComboBoxes. */
    public List<String> names(CategoryDomain domain) {
        return repo.findByDomain(domain).stream()
                .map(Category::name)
                .collect(Collectors.toList());
    }

    public void add(String name, CategoryDomain domain) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Nome da categoria e obrigatorio");
        repo.save(name.trim(), domain, null);
    }

    public void remove(long id) {
        repo.delete(id);
    }

    /**
     * Popula categorias padrao para todos os dominios (chamado na inicializacao).
     * Idempotente — nao duplica se ja existirem.
     */
    public void seedDefaults() {
        repo.seedIfEmpty(CategoryDomain.TASK, List.of(
                "Geral", "Pesquisa", "Experimento", "Leitura", "Escrita",
                "Reuniao", "Administracao", "Saude", "Pessoal", "Projeto"));
        repo.seedIfEmpty(CategoryDomain.CHECKLIST, List.of(
                "Geral", "Laboratorio", "Protocolo Experimental",
                "Manutencao", "Seguranca", "Administracao", "Campo"));
        repo.seedIfEmpty(CategoryDomain.STUDY, List.of(
                "Geral", "Leitura de Artigos", "Redacao Cientifica",
                "Analise de Dados", "Programacao", "Estatistica",
                "Revisao Bibliografica", "Curso Online"));
        repo.seedIfEmpty(CategoryDomain.STUDY_TYPE,
                java.util.Arrays.stream(StudyType.values())
                        .map(StudyType::label)
                        .collect(java.util.stream.Collectors.toList()));
        repo.seedIfEmpty(CategoryDomain.IDEA, List.of(
                "Geral", "Hipotese", "Metodologia", "Publicacao",
                "Projeto de Extensao", "Inovacao", "Colaboracao"));
    }
}


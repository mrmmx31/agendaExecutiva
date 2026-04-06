package com.pessoal.agenda.model;

/**
 * Item de checklist vinculado a uma Ideia / Projeto.
 *
 * Campos:
 *   id       – PK gerada pelo banco
 *   ideaId   – FK para project_ideas.id
 *   text     – descrição da ação
 *   done     – true = concluído / false = pendente
 *   position – ordem de exibição (crescente)
 */
public record IdeaChecklistItem(long id, long ideaId, String text, boolean done, int position) {}


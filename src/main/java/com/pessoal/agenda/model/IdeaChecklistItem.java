package com.pessoal.agenda.model;

/**
 * Item de checklist vinculado a uma Ideia / Projeto.
 *
 * Campos:
 *   id           – PK gerada pelo banco
 *   ideaId       – FK para project_ideas.id
 *   text         – descrição da ação
 *   done         – true = concluído / false = pendente
 *   position     – ordem de exibição (crescente)
 *   kanbanColumn – coluna do Kanban: backlog | em_andamento | em_revisao | concluido
 */
public record IdeaChecklistItem(long id, long ideaId, String text, boolean done, int position, String kanbanColumn) {

    /** Compatibilidade retroativa — usa 'backlog' como coluna padrão. */
    public IdeaChecklistItem(long id, long ideaId, String text, boolean done, int position) {
        this(id, ideaId, text, done, position, "backlog");
    }
}

package com.pessoal.agenda.model;

/**
 * Item de checklist vinculado a uma Tarefa da agenda.
 *
 * Campos:
 *   id           – PK gerada pelo banco
 *   taskId       – FK para tasks.id
 *   text         – descrição do passo / ação
 *   done         – true = concluído / false = pendente
 *   position     – ordem de exibição (crescente)
 *   kanbanColumn – coluna do Kanban: backlog | em_andamento | em_revisao | concluido
 */
public record TaskChecklistItem(long id, long taskId, String text, boolean done, int position, String kanbanColumn) {

    /** Compatibilidade retroativa — usa 'backlog' como coluna padrão. */
    public TaskChecklistItem(long id, long taskId, String text, boolean done, int position) {
        this(id, taskId, text, done, position, "backlog");
    }
}


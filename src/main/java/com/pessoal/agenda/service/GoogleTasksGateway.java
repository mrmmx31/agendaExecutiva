package com.pessoal.agenda.service;

import com.pessoal.agenda.service.GoogleTasksService.GTask;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface GoogleTasksGateway {
    List<GTask> listTasks(String taskListId, boolean showCompleted)
            throws IOException, InterruptedException;

    default List<GTask> listTasksForSync(String taskListId)
            throws IOException, InterruptedException {
        return listTasks(taskListId, true);
    }

    String createTask(String taskListId, String title, String notes, LocalDate dueDate)
            throws IOException, InterruptedException;

    void completeTask(String taskListId, String taskId)
            throws IOException, InterruptedException;

    void reopenTask(String taskListId, String taskId)
            throws IOException, InterruptedException;

    void updateTask(String taskListId, String taskId, String title, String notes, LocalDate dueDate)
            throws IOException, InterruptedException;

    void deleteTask(String taskListId, String taskId)
            throws IOException, InterruptedException;
}

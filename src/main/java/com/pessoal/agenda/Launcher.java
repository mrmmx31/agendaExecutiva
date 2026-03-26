package com.pessoal.agenda;

import com.pessoal.agenda.app.AppContext;
import com.pessoal.agenda.app.AppContextHolder;
import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        AppContextHolder.init(AppContext.create());
        Application.launch(AgendaApp.class, args);
    }
}

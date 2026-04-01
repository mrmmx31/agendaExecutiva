package com.pessoal.agenda.ui.view;

import javafx.stage.Stage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Gerencia janelas secundárias abertas para permitir fechamento global.
 */
public class WindowManager {
    private static final Set<Stage> openStages = Collections.synchronizedSet(new HashSet<>());

    public static void register(Stage stage) {
        openStages.add(stage);
        stage.setOnHiding(e -> openStages.remove(stage));
    }

    public static void closeAll() {
        synchronized (openStages) {
            for (Stage s : openStages) {
                if (s.isShowing()) s.close();
            }
            openStages.clear();
        }
    }
}


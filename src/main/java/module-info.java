module com.pessoal.agenda {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires java.desktop;
    requires java.prefs;
    requires java.net.http;
    requires org.xerial.sqlitejdbc;

    opens com.pessoal.agenda to javafx.fxml;
    opens com.pessoal.agenda.model to javafx.base;
    exports com.pessoal.agenda;
}
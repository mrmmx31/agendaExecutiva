module com.pessoal.agenda {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires java.desktop;
    requires java.prefs;
    requires java.net.http;
    requires jdk.httpserver;
    requires org.xerial.sqlitejdbc;
    requires com.google.gson;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.util;
    requires com.google.zxing;

    opens com.pessoal.agenda to javafx.fxml;
    opens com.pessoal.agenda.model to javafx.base;
    opens com.pessoal.agenda.infra.pairing to com.google.gson;
    exports com.pessoal.agenda;
}

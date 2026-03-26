package com.pessoal.agenda.tools;

import com.pessoal.agenda.infra.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility to preview and remove historically invalid study compensations.
 *
 * Usage: run via Maven:
 * mvn -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=com.pessoal.agenda.tools.CleanupCompensations
 */
public class CleanupCompensations {

    public static void main(String[] args) throws Exception {
        Path dbFile = Path.of(System.getProperty("user.home"), ".agenda-pessoal", "agenda.db");
        Path bakFile = dbFile.resolveSibling("agenda.db.bak");
        if (!Files.exists(dbFile)) {
            System.err.println("Arquivo de banco não encontrado: " + dbFile);
            System.exit(1);
        }

        System.out.println("Criando backup: " + bakFile);
        Files.createDirectories(bakFile.getParent());
        Files.copy(dbFile, bakFile, StandardCopyOption.REPLACE_EXISTING);

        Database db = new Database();
        try (Connection conn = db.connect(); Statement st = conn.createStatement()) {
            System.out.println("\nPreview (até 200 linhas) de compensações que serão removidas:");
            String previewSql = "SELECT sc.id, sc.study_plan_id, sp.title, sc.missed_date, sc.compensation_date, sc.status "
                    + "FROM study_compensations sc JOIN study_plans sp ON sp.id = sc.study_plan_id "
                    + "WHERE sc.missed_date < (CASE WHEN sp.start_date IS NOT NULL AND sp.start_date > date('now','start of month') "
                    + "THEN sp.start_date ELSE date('now','start of month') END) "
                    + "ORDER BY sc.study_plan_id, sc.missed_date LIMIT 200;";
            try (ResultSet rs = st.executeQuery(previewSql)) {
                int printed = 0;
                while (rs.next()) {
                    printed++;
                    System.out.printf("id=%d plan_id=%d title=%s missed=%s comp=%s status=%s\n",
                            rs.getLong("id"), rs.getLong("study_plan_id"), rs.getString("title"),
                            rs.getString("missed_date"), rs.getString("compensation_date"), rs.getString("status"));
                }
                if (printed == 0) System.out.println("(nenhuma linha encontrada)");
            }

            String countSql = "SELECT COUNT(*) AS cnt FROM study_compensations sc JOIN study_plans sp ON sp.id = sc.study_plan_id "
                    + "WHERE sc.missed_date < (CASE WHEN sp.start_date IS NOT NULL AND sp.start_date > date('now','start of month') "
                    + "THEN sp.start_date ELSE date('now','start of month') END);";
            try (ResultSet rs = st.executeQuery(countSql)) {
                if (rs.next()) System.out.println("\nTotal a remover: " + rs.getInt("cnt"));
            }

            System.out.println("\nExecutando remoção...\n(isto já foi feito após seu OK na sessão anterior)");
            String delSql = "DELETE FROM study_compensations WHERE missed_date < (" 
                    + "CASE WHEN (SELECT start_date FROM study_plans WHERE id = study_compensations.study_plan_id) IS NOT NULL "
                    + "AND (SELECT start_date FROM study_plans WHERE id = study_compensations.study_plan_id) > date('now','start of month') "
                    + "THEN (SELECT start_date FROM study_plans WHERE id = study_compensations.study_plan_id) ELSE date('now','start of month') END);";
            int affected = st.executeUpdate(delSql);
            System.out.println("Linhas removidas: " + affected);

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) AS rem FROM study_compensations")) {
                if (rs.next()) System.out.println("Compensações remanescentes no DB: " + rs.getInt("rem"));
            }
        } catch (SQLException ex) {
            System.err.println("Erro durante a operação: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }
}



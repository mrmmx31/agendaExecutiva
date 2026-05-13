package com.pessoal.agenda.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser JSON minimalista para respostas da Google API.
 * Suporta campos string/número de primeiro nível e arrays de objetos.
 */
public final class SimpleJson {

    private SimpleJson() {}

    /** Extrai o valor de um campo string: "key": "value" */
    public static String str(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        idx = json.indexOf(':', idx) + 1;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length()) return null;
        char first = json.charAt(idx);
        if (first == '"') {
            // string value
            idx++;
            StringBuilder sb = new StringBuilder();
            while (idx < json.length()) {
                char c = json.charAt(idx++);
                if (c == '\\' && idx < json.length()) {
                    char esc = json.charAt(idx++);
                    switch (esc) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        default   -> { sb.append('\\'); sb.append(esc); }
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            // número, boolean ou null
            int end = idx;
            while (end < json.length() && ",}\n\r]".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(idx, end).trim();
        }
    }

    /** Extrai valor long de um campo numérico. Retorna -1 se não encontrado. */
    public static long num(String json, String key) {
        String v = str(json, key);
        if (v == null) return -1;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    /**
     * Extrai um array de objetos JSON: "key": [{...},{...}]
     * Retorna cada objeto como string JSON independente.
     */
    public static List<String> array(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return List.of();
        idx = json.indexOf('[', idx);
        if (idx < 0) return List.of();
        idx++; // skip '['
        List<String> result = new ArrayList<>();
        while (idx < json.length()) {
            while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
            if (idx >= json.length()) break;
            char c = json.charAt(idx);
            if (c == ']') break;
            if (c == ',') { idx++; continue; }
            if (c == '{') {
                int depth = 0, start = idx;
                while (idx < json.length()) {
                    char ch = json.charAt(idx);
                    if      (ch == '{') depth++;
                    else if (ch == '}') { depth--; if (depth == 0) { idx++; break; } }
                    idx++;
                }
                result.add(json.substring(start, idx));
            } else {
                idx++;
            }
        }
        return result;
    }

    /** Verifica se um campo boolean é true */
    public static boolean bool(String json, String key) {
        String v = str(json, key);
        return "true".equalsIgnoreCase(v);
    }
}


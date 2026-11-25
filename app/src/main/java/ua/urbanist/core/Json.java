package ua.urbanist.core;

import java.util.*;

/** Мінімальний JSON (без залежностей, сумісний з Java 8 / Android). */
public final class Json {
    private final String s; private int i;
    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text); p.ws(); Object v = p.val(); p.ws();
        if (p.i != p.s.length()) throw new IllegalArgumentException("зайві символи");
        return v;
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.trim().isEmpty()) return new LinkedHashMap<String, Object>();
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("очікувався об'єкт");
        return (Map<String, Object>) o;
    }
    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private Object val() {
        if (i >= s.length()) throw new IllegalArgumentException("кінець");
        char c = s.charAt(i);
        if (c == '{') {
            i++; Map<String, Object> m = new LinkedHashMap<String, Object>(); ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = str(); ws(); expect(':'); ws(); m.put(k, val()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect('}'); return m;
            }
        }
        if (c == '[') {
            i++; List<Object> l = new ArrayList<Object>(); ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws(); l.add(val()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect(']'); return l;
            }
        }
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(st, i);
        if (n.isEmpty()) throw new IllegalArgumentException("невідомий токен");
        if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) return Double.valueOf(n);
        return Long.valueOf(n);
    }
    private void expect(char c) { if (i >= s.length() || s.charAt(i) != c) throw new IllegalArgumentException("очікувалось " + c); i++; }
    private String str() {
        expect('"'); StringBuilder b = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new IllegalArgumentException("незакритий рядок");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
    }

    public static String stringify(Object o) { StringBuilder b = new StringBuilder(); write(b, o); return b.toString(); }
    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object o) {
        if (o == null) b.append("null");
        else if (o instanceof String) quote(b, (String) o);
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) b.append(o);
        else if (o instanceof Number) b.append(((Number) o).doubleValue());
        else if (o instanceof Map) {
            b.append('{'); boolean f = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!f) b.append(','); f = false; quote(b, e.getKey()); b.append(':'); write(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof Iterable) {
            b.append('['); boolean f = true;
            for (Object x : (Iterable<Object>) o) { if (!f) b.append(','); f = false; write(b, x); }
            b.append(']');
        } else if (o instanceof int[]) {
            b.append('['); int[] a = (int[]) o; for (int k = 0; k < a.length; k++) { if (k > 0) b.append(','); b.append(a[k]); } b.append(']');
        } else quote(b, String.valueOf(o));
    }
    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break; case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                default: if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }

    // зручні геттери
    public static Integer intVal(Object o) { return (o instanceof Number && !(o instanceof Double)) ? Integer.valueOf(((Number) o).intValue()) : (o instanceof Double ? Integer.valueOf(((Double) o).intValue()) : null); }
    public static String strVal(Object o) { return o instanceof String ? (String) o : null; }
}

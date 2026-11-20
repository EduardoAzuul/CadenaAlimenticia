package distribuido;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// JSON mínimo para los mensajes (sin librerías externas).
// Soporta objetos, arreglos, texto, números, booleanos y null.
public final class Json {
    private Json() {}

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                escribirTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object o : lista) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else {
            escribirTexto(v.toString(), sb);
        }
    }

    private static void escribirTexto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // Regresa Map, List, String, Long, Double, Boolean o null.
    public static Object leer(String texto) {
        Lector lector = new Lector(texto);
        Object valor = lector.valor();
        lector.espacios();
        if (lector.i != texto.length()) {
            throw lector.error("texto sobrante");
        }
        return valor;
    }

    private static final class Lector {
        private final String s;
        private int i = 0;

        Lector(String s) { this.s = s; }

        Object valor() {
            espacios();
            if (i >= s.length()) throw error("fin inesperado");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> objeto();
                case '[' -> arreglo();
                case '"' -> texto();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numero();
            };
        }

        Map<String, Object> objeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            i++; // {
            espacios();
            if (s.charAt(i) == '}') { i++; return mapa; }
            while (true) {
                espacios();
                String clave = texto();
                espacios();
                esperar(':');
                mapa.put(clave, valor());
                espacios();
                char c = s.charAt(i++);
                if (c == '}') return mapa;
                if (c != ',') throw error("se esperaba , o }");
            }
        }

        List<Object> arreglo() {
            List<Object> lista = new ArrayList<>();
            i++; // [
            espacios();
            if (s.charAt(i) == ']') { i++; return lista; }
            while (true) {
                lista.add(valor());
                espacios();
                char c = s.charAt(i++);
                if (c == ']') return lista;
                if (c != ',') throw error("se esperaba , o ]");
            }
        }

        String texto() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                    default -> sb.append(e); // \" \\ \/
                }
            }
        }

        Object numero() {
            int inicio = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String n = s.substring(inicio, i);
            if (n.isEmpty()) throw error("valor inválido");
            return (n.contains(".") || n.contains("e") || n.contains("E"))
                    ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
        }

        Object literal(String palabra, Object valor) {
            if (!s.startsWith(palabra, i)) throw error("se esperaba " + palabra);
            i += palabra.length();
            return valor;
        }

        void esperar(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw error("se esperaba " + c);
            i++;
        }

        void espacios() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON inválido en posición " + i + ": " + msg);
        }
    }
}

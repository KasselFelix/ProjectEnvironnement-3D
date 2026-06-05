package loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser JSON minimal et autonome — utilise uniquement le JDK. Retourne des
 * Map&lt;String,Object&gt;, List&lt;Object&gt;, Double, String, Boolean, ou null.
 *
 * Suffisant pour parser un header glTF 2.0 (~2 KB). Pas conçu pour le grand
 * volume ; rapide à lire et à modifier si besoin.
 */
public final class MiniJson {

    public static Object parse(String src) {
        Parser p = new Parser(src);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        return v;
    }

    private MiniJson() {}

    private static final class Parser {
        final String s;
        int i;
        Parser(String s) { this.s = s; this.i = 0; }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { i++; }
                else break;
            }
        }

        Object readValue() {
            skipWs();
            if (i >= s.length()) throw new IllegalStateException("JSON: fin inattendue");
            char c = s.charAt(i);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBool();
            if (c == 'n') return readNull();
            return readNumber();
        }

        Map<String,Object> readObject() {
            Map<String,Object> m = new LinkedHashMap<String,Object>();
            i++; // '{'
            skipWs();
            if (peek('}')) { i++; return m; }
            while (true) {
                skipWs();
                String k = readString();
                skipWs();
                expect(':');
                Object v = readValue();
                m.put(k, v);
                skipWs();
                if (peek(',')) { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> readArray() {
            List<Object> a = new ArrayList<Object>();
            i++; // '['
            skipWs();
            if (peek(']')) { i++; return a; }
            while (true) {
                a.add(readValue());
                skipWs();
                if (peek(',')) { i++; continue; }
                expect(']');
                return a;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            int cp = Integer.parseInt(s.substring(i, i+4), 16);
                            sb.append((char) cp);
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            throw new IllegalStateException("JSON: string non terminée");
        }

        Double readNumber() {
            int start = i;
            if (peek('-')) i++;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++;
                else break;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        Boolean readBool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw new IllegalStateException("JSON: bool invalide @" + i);
        }

        Object readNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw new IllegalStateException("JSON: null invalide @" + i);
        }

        boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c)
                throw new IllegalStateException("JSON: attendu '" + c + "' @" + i);
            i++;
        }
    }
}

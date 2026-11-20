package visual;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import modelo.CausaMuerte;
import modelo.Especie;

// Guarda lo que pasó cada día en cada región, para las gráficas del final.
public class HistorialSimulacion {
    // muertes: acumuladas desde el inicio, por especie y causa.
    public record Muestra(Map<Especie, Integer> poblaciones, int biomasa, int nutrientes,
                          int cadaveres, int llegaron, int crias,
                          Map<Especie, Map<CausaMuerte, Integer>> muertes) {
        public int poblacion(Especie e) { return poblaciones.getOrDefault(e, 0); }
    }

    private final Map<String, TreeMap<Integer, Muestra>> porRegion = new LinkedHashMap<>();

    public synchronized void registrar(String region, int dia, Muestra muestra) {
        porRegion.computeIfAbsent(region, r -> new TreeMap<>()).put(dia, muestra);
    }

    public synchronized List<String> regiones() {
        return new ArrayList<>(porRegion.keySet());
    }

    public synchronized boolean estaVacio() {
        return porRegion.values().stream().allMatch(TreeMap::isEmpty);
    }

    public synchronized int primerDia() {
        return porRegion.values().stream().filter(m -> !m.isEmpty())
                .mapToInt(TreeMap::firstKey).min().orElse(1);
    }

    public synchronized int ultimoDia() {
        return porRegion.values().stream().filter(m -> !m.isEmpty())
                .mapToInt(TreeMap::lastKey).max().orElse(0);
    }

    // Último día que reportó una región (si se cayó, es antes del final).
    public synchronized int ultimoDia(String region) {
        TreeMap<Integer, Muestra> m = porRegion.get(region);
        return m == null || m.isEmpty() ? 0 : m.lastKey();
    }

    // Valores por día de una región; NaN donde no hay dato (p. ej. después de caerse).
    public synchronized double[] serie(String region, ToIntFunction<Muestra> valor) {
        int desde = primerDia(), hasta = ultimoDia();
        double[] v = new double[Math.max(0, hasta - desde + 1)];
        TreeMap<Integer, Muestra> m = porRegion.get(region);
        for (int d = desde; d <= hasta; d++) {
            Muestra x = m == null ? null : m.get(d);
            v[d - desde] = x == null ? Double.NaN : valor.applyAsInt(x);
        }
        return v;
    }

    // Suma de todas las regiones que reportaron ese día.
    public synchronized double[] serieTotal(ToIntFunction<Muestra> valor) {
        int desde = primerDia(), hasta = ultimoDia();
        double[] v = new double[Math.max(0, hasta - desde + 1)];
        for (int d = desde; d <= hasta; d++) {
            double suma = 0;
            boolean alguno = false;
            for (TreeMap<Integer, Muestra> m : porRegion.values()) {
                Muestra x = m.get(d);
                if (x != null) {
                    suma += valor.applyAsInt(x);
                    alguno = true;
                }
            }
            v[d - desde] = alguno ? suma : Double.NaN;
        }
        return v;
    }

    // Primer día en que el total de cada especie llegó a 0 después de haber existido.
    public Map<Especie, Integer> extinciones() {
        Map<Especie, Integer> resultado = new EnumMap<>(Especie.class);
        int desde = primerDia();
        for (Especie e : Especie.values()) {
            double[] total = serieTotal(x -> x.poblacion(e));
            boolean existio = false;
            for (int i = 0; i < total.length; i++) {
                if (Double.isNaN(total[i])) continue;
                if (total[i] > 0) existio = true;
                else if (existio) {
                    resultado.put(e, desde + i);
                    break;
                }
            }
        }
        return resultado;
    }

    public synchronized int totalMigrantes() {
        return porRegion.values().stream()
                .flatMap(m -> m.values().stream()).mapToInt(Muestra::llegaron).sum();
    }

    // Muertes por especie y causa en todo el sistema. Son acumuladas: se suma la última
    // muestra de cada región (si una región se cayó, cuenta lo que llevaba hasta entonces).
    public synchronized Map<Especie, Map<CausaMuerte, Integer>> muertesTotales() {
        Map<Especie, Map<CausaMuerte, Integer>> total = new EnumMap<>(Especie.class);
        for (Especie e : Especie.values()) total.put(e, new EnumMap<>(CausaMuerte.class));
        for (TreeMap<Integer, Muestra> m : porRegion.values()) {
            if (m.isEmpty()) continue;
            m.lastEntry().getValue().muertes().forEach((e, porCausa) ->
                    porCausa.forEach((c, n) -> total.get(e).merge(c, n, Integer::sum)));
        }
        return total;
    }

    // Las crías por nido son acumuladas: basta la última muestra de cada región.
    public synchronized int totalCriasEnNidos() {
        return porRegion.values().stream().filter(m -> !m.isEmpty())
                .mapToInt(m -> m.lastEntry().getValue().crias()).sum();
    }
}

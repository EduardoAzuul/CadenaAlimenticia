package soporte;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import distribuido.Mensaje;
import distribuido.nodos.NodoCoordinador;
import modelo.Especie;

// Vive en el coordinador. Junta las estadísticas de todas las regiones,
// las reenvía a los paneles y detecta extinciones.
public class Monitor {
    private final NodoCoordinador coordinador;
    private final Map<String, Map<Especie, Integer>> poblaciones = new ConcurrentHashMap<>();
    private final Map<String, Integer> llegadas = new ConcurrentHashMap<>();
    private final List<Map<Especie, Integer>> historial = Collections.synchronizedList(new ArrayList<>());

    public Monitor(NodoCoordinador coordinador) {
        this.coordinador = coordinador;
    }

    // Llega un STATS de una región.
    public void recolectarEstadisticas(Mensaje stats) {
        poblaciones.put(stats.getOrigen(), stats.getPoblaciones());
        llegadas.put(stats.getOrigen(), stats.getEntero("llegaron"));
        enviarStatsAlPanel(stats);
    }

    // Lo llama el Reloj cuando todas las regiones terminaron el día.
    public void cerrarDia(int dia) {
        Map<Especie, Integer> totales = new EnumMap<>(Especie.class);
        for (Especie e : Especie.values()) totales.put(e, 0);
        StringBuilder detalle = new StringBuilder();
        for (String region : coordinador.regionesActivas()) {
            Map<Especie, Integer> p = poblaciones.getOrDefault(region, Map.of());
            p.forEach((e, n) -> totales.merge(e, n, Integer::sum));
            detalle.append("  ").append(region)
                    .append(" P=").append(p.getOrDefault(Especie.PLANTA, 0))
                    .append(" H=").append(p.getOrDefault(Especie.HERBIVORO, 0))
                    .append(" Dp=").append(p.getOrDefault(Especie.DEPREDADOR, 0))
                    .append(" Ds=").append(p.getOrDefault(Especie.DESCOMPONEDOR, 0));
            int llegaron = llegadas.getOrDefault(region, 0);
            if (llegaron > 0) detalle.append(" (+").append(llegaron).append(" migrantes)");
        }
        detectarExtincion(dia, totales);
        historial.add(totales);
        coordinador.log(String.format("Día %3d  P=%3d H=%3d Dp=%3d Ds=%3d |%s", dia, totales.get(Especie.PLANTA),
                totales.get(Especie.HERBIVORO), totales.get(Especie.DEPREDADOR),
                totales.get(Especie.DESCOMPONEDOR), detalle));
    }

    // Una especie que existía el día anterior y hoy tiene 0 en todas las regiones.
    public void detectarExtincion(int dia, Map<Especie, Integer> totales) {
        if (historial.isEmpty()) return;
        Map<Especie, Integer> ayer = historial.get(historial.size() - 1);
        for (Especie e : Especie.values()) {
            if (ayer.get(e) > 0 && totales.get(e) == 0) {
                coordinador.log("¡EXTINCIÓN! Día " + dia + ": ya no quedan " + e);
            }
        }
    }

    public void enviarStatsAlPanel(Mensaje stats) {
        coordinador.enviarAPaneles(stats);
    }

    public void olvidarRegion(String region) {
        poblaciones.remove(region);
        llegadas.remove(region);
    }

    public List<Map<Especie, Integer>> getHistorial() {
        synchronized (historial) {
            return new ArrayList<>(historial);
        }
    }
}

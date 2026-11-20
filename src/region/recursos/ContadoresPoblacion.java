package region.recursos;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import modelo.Especie;

// Sección crítica (búfer = 1) resuelta con variables atómicas.
public class ContadoresPoblacion {
    private final Map<Especie, AtomicInteger> contadores = new EnumMap<>(Especie.class);

    public ContadoresPoblacion() {
        for (Especie e : Especie.values()) {
            contadores.put(e, new AtomicInteger());
        }
    }

    public void incrementar(Especie especie) { contadores.get(especie).incrementAndGet(); }
    public void decrementar(Especie especie) { contadores.get(especie).decrementAndGet(); }
    public int obtener(Especie especie) { return contadores.get(especie).get(); }

    // Copia para la ventana / el monitor.
    public Map<Especie, Integer> foto() {
        Map<Especie, Integer> copia = new EnumMap<>(Especie.class);
        contadores.forEach((e, n) -> copia.put(e, n.get()));
        return copia;
    }
}

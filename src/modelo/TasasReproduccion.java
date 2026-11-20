package modelo;

import java.util.LinkedHashMap;
import java.util.Map;

// Qué tanto se reproduce cada especie, para todo el sistema.
// Cada valor es la probabilidad (0–100 %) de que un individuo que ya puede reproducirse
// (tiene energía, hay lugar, etc.) de verdad lo intente ese día.
public record TasasReproduccion(int plantas, int herbivoros, int depredadores, int descomponedores) {

    public TasasReproduccion {
        for (int t : new int[] { plantas, herbivoros, depredadores, descomponedores }) {
            if (t < 0 || t > 100) throw new IllegalArgumentException("Las tasas van de 0 a 100: " + t);
        }
    }

    public static TasasReproduccion porDefecto() {
        return new TasasReproduccion(40, 40, 100, 100);
    }

    public int de(Especie especie) {
        return switch (especie) {
            case PLANTA -> plantas;
            case HERBIVORO -> herbivoros;
            case DEPREDADOR -> depredadores;
            case DESCOMPONEDOR -> descomponedores;
        };
    }

    // Para mandarlas en un mensaje JSON.
    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Especie e : Especie.values()) m.put(e.name(), de(e));
        return m;
    }

    public static TasasReproduccion desdeMapa(Map<?, ?> m) {
        return new TasasReproduccion(valor(m, Especie.PLANTA), valor(m, Especie.HERBIVORO),
                valor(m, Especie.DEPREDADOR), valor(m, Especie.DESCOMPONEDOR));
    }

    private static int valor(Map<?, ?> m, Especie e) {
        return ((Number) m.get(e.name())).intValue();
    }
}

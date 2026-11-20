package distribuido.nodos;

import region.Region;

// Poblaciones iniciales, capacidades de carga, nidos y nutrientes iniciales del suelo de una región.
public record ConfigRegion(
        int plantas, int herbivoros, int depredadores, int descomponedores,
        int capacidadPlantas, int capacidadHerbivoros, int capacidadDepredadores, int capacidadDescomponedores,
        int nidosHerbivoros, int nidosDepredadores,
        int nutrientesIniciales) {

    public static ConfigRegion porDefecto() {
        return new ConfigRegion(20, 10, 3, 5, 80, 60, 15, 30,
                Region.NIDOS_HERBIVOROS, Region.NIDOS_DEPREDADORES, Region.NUTRIENTES_INICIALES);
    }

    // Región pobre: caben pocas plantas y hay menos nidos.
    public static ConfigRegion pobre() {
        return new ConfigRegion(5, 10, 2, 5, 15, 60, 15, 30, 3, 2, Region.NUTRIENTES_INICIALES);
    }
}

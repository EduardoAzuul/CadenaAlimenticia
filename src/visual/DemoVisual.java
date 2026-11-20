package visual;

import java.util.Map;
import modelo.Especie;

// Prueba rápida de las ventanas con números fijos (no es la simulación).
public class DemoVisual {
    public static void main(String[] args) {
        VentanaRegion pradera = new VentanaRegion("Pradera");
        pradera.actualizar(Map.of(
                Especie.PLANTA, 40, Especie.HERBIVORO, 20,
                Especie.DEPREDADOR, 5, Especie.DESCOMPONEDOR, 8));
        pradera.mostrar();

        VentanaRegion bosque = new VentanaRegion("Bosque");
        bosque.setLocation(520, 0);
        bosque.actualizar(Map.of(
                Especie.PLANTA, 25, Especie.HERBIVORO, 10,
                Especie.DEPREDADOR, 3, Especie.DESCOMPONEDOR, 12));
        bosque.mostrar();
    }
}

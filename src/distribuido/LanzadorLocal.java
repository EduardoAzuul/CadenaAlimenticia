package distribuido;

import java.util.List;
import distribuido.nodos.ConfigRegion;
import modelo.TasasReproduccion;
import distribuido.nodos.NodoCoordinador;
import distribuido.nodos.NodoPanel;
import distribuido.nodos.NodoRegion;

// Arranca todo en una sola JVM (igual se comunican por TCP).
// Para correrlo de verdad distribuido, lanza cada nodo con su propio main.
public class LanzadorLocal {

    public record DefinicionRegion(String nombre, List<String> vecinas, ConfigRegion config) {}

    public static void main(String[] args) throws InterruptedException {
        // La Pradera es fértil; en el Bosque caben pocas plantas, así que sus herbívoros migran.
        lanzar(5000, 150, 300, true, TasasReproduccion.porDefecto(), List.of(
                new DefinicionRegion("Pradera", List.of("Bosque"), ConfigRegion.porDefecto()),
                new DefinicionRegion("Bosque", List.of("Pradera"), ConfigRegion.pobre())));
    }

    // Bloquea unos cientos de ms mientras arrancan los nodos: no llamarlo desde el hilo de Swing.
    public static void lanzar(int puerto, int dias, long pausaMs, boolean conPanel, TasasReproduccion tasas,
                              List<DefinicionRegion> regiones) throws InterruptedException {
        hilo("coordinador", new NodoCoordinador(puerto, dias, pausaMs, regiones.size(), tasas));
        Thread.sleep(300);
        if (conPanel) {
            hilo("panel", new NodoPanel("PANEL", "localhost", puerto));
            Thread.sleep(300);
        }
        for (DefinicionRegion r : regiones) {
            hilo(r.nombre(), new NodoRegion(r.nombre(), "localhost", puerto, r.vecinas(), r.config()));
        }
    }

    private static void hilo(String nombre, Runnable nodo) {
        new Thread(nodo, nombre).start();
    }
}

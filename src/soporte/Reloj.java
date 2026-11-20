package soporte;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import distribuido.Mensaje;
import distribuido.TipoMensaje;
import distribuido.nodos.Nodo;
import distribuido.nodos.NodoCoordinador;

// Vive en el coordinador. Cada ciclo es un "día": manda TICK a todas las regiones
// y espera el TICK_DONE de todas (barrera distribuida) antes de pasar al siguiente.
public class Reloj implements Runnable {
    public static final long TIMEOUT_DIA_MS = 10_000;

    private final NodoCoordinador coordinador;
    private final int dias;
    private final long pausaMs; // solo para que se pueda ver en el panel
    private int diaActual = 0;
    private final Set<String> tickDone = new HashSet<>();

    public Reloj(NodoCoordinador coordinador, int dias, long pausaMs) {
        this.coordinador = coordinador;
        this.dias = dias;
        this.pausaMs = pausaMs;
    }

    @Override
    public void run() {
        coordinador.esperarRegiones();
        while (diaActual < dias) {
            if (coordinador.regionesActivas().isEmpty()) {
                coordinador.log("No quedan regiones activas");
                break;
            }
            pasarDia();
            if (!esperarTickDone()) {
                coordinador.log("Día " + diaActual + ": no todas las regiones terminaron a tiempo, se sigue");
            }
            coordinador.getMonitor().cerrarDia(diaActual);
            try {
                Thread.sleep(pausaMs);
            } catch (InterruptedException e) {
                return;
            }
        }
        coordinador.finalizar();
    }

    public void pasarDia() {
        synchronized (this) {
            diaActual++;
            tickDone.clear();
        }
        Mensaje tick = new Mensaje(TipoMensaje.TICK, Nodo.COORDINADOR, null, diaActual);
        for (String region : coordinador.regionesActivas()) {
            coordinador.enviarMensaje(region, tick);
        }
    }

    public synchronized void registrarTickDone(String region, int dia) {
        if (dia == diaActual) {
            tickDone.add(region);
            notifyAll();
        }
    }

    // Una región se cayó: ya no hay que esperarla.
    public synchronized void despertar() {
        notifyAll();
    }

    // Barrera: espera a todas las regiones activas (las caídas no cuentan). false = timeout.
    public synchronized boolean esperarTickDone() {
        long limite = System.currentTimeMillis() + TIMEOUT_DIA_MS;
        while (true) {
            List<String> activas = coordinador.regionesActivas();
            if (tickDone.containsAll(activas)) {
                return true;
            }
            long restante = limite - System.currentTimeMillis();
            if (restante <= 0) {
                return false;
            }
            try {
                wait(restante);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    public synchronized int getDiaActual() { return diaActual; }
}

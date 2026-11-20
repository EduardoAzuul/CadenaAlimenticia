package distribuido.nodos;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import distribuido.ConexionTCP;
import distribuido.Mensaje;
import distribuido.TipoMensaje;
import modelo.Especie;
import visual.HistorialSimulacion;
import visual.VentanaRegion;
import visual.VentanaResultados;

// Solo mira: recibe los STATS que reenvía el coordinador y abre una ventana por región.
// No interviene en la simulación.
public class NodoPanel extends Nodo {
    private final Map<String, Map<Especie, Integer>> poblacionesPorRegion = new ConcurrentHashMap<>();
    private final Map<String, VentanaRegion> ventanas = new ConcurrentHashMap<>();
    private final HistorialSimulacion historial = new HistorialSimulacion();

    public NodoPanel(String id, String host, int puerto) {
        super(id, host, puerto);
    }

    @Override
    public void run() {
        ConexionTCP coordinador = new ConexionTCP(host, puerto);
        try {
            coordinador.conectar();
        } catch (IOException e) {
            log("No pude conectarme al coordinador en " + host + ":" + puerto + " (" + e.getMessage() + ")");
            return;
        }
        conexiones.put(COORDINADOR, coordinador);
        coordinador.enviar(new Mensaje(TipoMensaje.REGISTRO, id, COORDINADOR, 0).con("rol", "PANEL"));
        log("Conectado al coordinador");
        escuchar(coordinador);
        log("Desconectado");
    }

    @Override
    public void procesarMensaje(Mensaje m) {
        switch (m.getTipo()) {
            case STATS -> actualizarVista(m);
            case REGION_CAIDA -> {
                VentanaRegion v = ventanas.get(m.getTexto("region"));
                if (v != null) v.marcarCaida();
            }
            case FIN -> {
                log("Fin de la simulación: abriendo resultados (las ventanas se quedan abiertas)");
                VentanaResultados.mostrar(historial);
                activo = false;
                conexiones.get(COORDINADOR).cerrar();
            }
            default -> { }
        }
    }

    public void actualizarVista(Mensaje stats) {
        String region = stats.getOrigen();
        Map<Especie, Integer> poblaciones = stats.getPoblaciones();
        poblacionesPorRegion.put(region, poblaciones);
        historial.registrar(region, stats.getDia(), new HistorialSimulacion.Muestra(poblaciones,
                stats.getEntero("biomasa"), stats.getEntero("nutrientes"), stats.getEntero("materiaMuerta"),
                stats.getEntero("llegaron"),
                stats.getNidos().values().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum(),
                stats.getMuertes()));
        VentanaRegion ventana = ventanas.computeIfAbsent(region, nombre -> {
            VentanaRegion v = new VentanaRegion(nombre);
            v.setLocation(40 + ventanas.size() * 520, 60);
            v.mostrar();
            return v;
        });
        ventana.actualizar(poblaciones, stats.getDia());
        ventana.actualizarRecursos(stats.getEntero("biomasa"), stats.getEntero("biomasaMax"),
                stats.getEntero("nutrientes"), stats.getEntero("nutrientesMax"),
                stats.getEntero("materiaMuerta"));
        ventana.actualizarNidos(stats.getNidos());
        ventana.actualizarAnimales(stats.getAnimales());
    }

    public void mostrar() {
        ventanas.values().forEach(VentanaRegion::mostrar);
    }

    // Uso: NodoPanel [host] [puerto]
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        new NodoPanel("PANEL-" + ProcessHandle.current().pid(), host, puerto).run();
    }
}

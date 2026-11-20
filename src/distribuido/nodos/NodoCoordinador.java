package distribuido.nodos;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import distribuido.ConexionTCP;
import distribuido.Mensaje;
import distribuido.TipoMensaje;
import modelo.TasasReproduccion;
import soporte.Monitor;
import soporte.Reloj;

// Aloja al Reloj y al Monitor. Acepta conexiones de regiones y paneles,
// reenvía los mensajes entre regiones y vigila los heartbeats.
public class NodoCoordinador extends Nodo {
    public static final long TIMEOUT_HEARTBEAT_MS = 3000;
    public static final long REVISION_HEARTBEAT_MS = 1000;

    private final Reloj reloj;
    private final Monitor monitor;
    private final int minRegiones;
    private final TasasReproduccion tasasReproduccion; // general: la misma para todas las regiones
    private final Map<String, Long> ultimoHeartbeat = new ConcurrentHashMap<>();
    private final List<String> regiones = new CopyOnWriteArrayList<>();
    private final Set<String> regionesCaidas = ConcurrentHashMap.newKeySet();
    private final Set<String> paneles = ConcurrentHashMap.newKeySet();
    private ServerSocket servidor;

    public NodoCoordinador(int puerto, int dias, long pausaMs, int minRegiones) {
        this(puerto, dias, pausaMs, minRegiones, TasasReproduccion.porDefecto());
    }

    public NodoCoordinador(int puerto, int dias, long pausaMs, int minRegiones, TasasReproduccion tasas) {
        super(COORDINADOR, "0.0.0.0", puerto);
        this.tasasReproduccion = tasas;
        this.minRegiones = minRegiones;
        this.monitor = new Monitor(this);
        this.reloj = new Reloj(this, dias, pausaMs);
    }

    @Override
    public void run() {
        try {
            servidor = new ServerSocket(puerto);
        } catch (IOException e) {
            log("No pude abrir el puerto " + puerto + ": " + e.getMessage());
            return;
        }
        log("Escuchando en el puerto " + puerto + ", esperando " + minRegiones + " regiones...");
        iniciarHilo("vigia-heartbeats", this::revisarHeartbeats);
        iniciarHilo("reloj", reloj);

        while (activo) {
            try {
                Socket socket = servidor.accept();
                ConexionTCP conexion = new ConexionTCP(socket);
                iniciarHilo("conexion-" + socket.getPort(), () -> atender(conexion));
            } catch (IOException e) {
                if (activo) log("Error aceptando conexión: " + e.getMessage());
            }
        }
    }

    // Primer mensaje de cada conexión: REGISTRO con su rol.
    private void atender(ConexionTCP conexion) {
        String idNodo = null;
        boolean esRegion = false;
        try {
            Mensaje registro = conexion.recibir();
            if (registro == null || registro.getTipo() != TipoMensaje.REGISTRO) {
                conexion.cerrar();
                return;
            }
            idNodo = registro.getOrigen();
            esRegion = "REGION".equals(registro.getTexto("rol"));
            conexiones.put(idNodo, conexion);
            if (esRegion) registrarRegion(idNodo);
            else registrarPanel(idNodo);
        } catch (IOException e) {
            conexion.cerrar();
            return;
        }

        escuchar(conexion);

        // Se desconectó. Solo cuenta si esta era su conexión vigente.
        if (conexiones.remove(idNodo, conexion)) {
            if (esRegion) marcarRegionCaida(idNodo, "se desconectó");
            else paneles.remove(idNodo);
        }
    }

    public synchronized void registrarRegion(String idRegion) {
        if (!regiones.contains(idRegion)) regiones.add(idRegion);
        if (regionesCaidas.remove(idRegion)) {
            difundir(new Mensaje(TipoMensaje.REGISTRO, COORDINADOR, null, reloj.getDiaActual())
                    .con("rol", "REGION").con("region", idRegion));
        }
        ultimoHeartbeat.put(idRegion, System.currentTimeMillis());
        // Parámetros generales antes del primer TICK (mismo canal, así que llegan en orden).
        enviarMensaje(idRegion, new Mensaje(TipoMensaje.CONFIGURACION, COORDINADOR, idRegion, reloj.getDiaActual())
                .con("tasasReproduccion", tasasReproduccion.aMapa()));
        log("Región registrada: " + idRegion + " (" + regionesActivas().size() + " activas)");
        notifyAll(); // para esperarRegiones()
    }

    private void registrarPanel(String idPanel) {
        paneles.add(idPanel);
        log("Panel conectado: " + idPanel);
    }

    @Override
    public void procesarMensaje(Mensaje m) {
        switch (m.getTipo()) {
            case HEARTBEAT -> ultimoHeartbeat.put(m.getOrigen(), System.currentTimeMillis());
            case STATS -> monitor.recolectarEstadisticas(m);
            case TICK_DONE -> reloj.registrarTickDone(m.getOrigen(), m.getDia());
            case MIGRATION_REQUEST -> {
                if (!estaActiva(m.getDestino())) {
                    // El destino está caído: el coordinador contesta NACK por él.
                    enviarMensaje(m.getOrigen(), new Mensaje(TipoMensaje.MIGRATION_NACK, m.getDestino(), m.getOrigen(), m.getDia())
                            .con("idMigracion", m.getTexto("idMigracion"))
                            .con("motivo", "región caída"));
                } else {
                    reenviar(m);
                }
            }
            default -> reenviar(m);
        }
    }

    // Mensajes de región a región pasan por aquí (no hay memoria compartida entre nodos).
    private void reenviar(Mensaje m) {
        if (m.getDestino() == null || m.getDestino().equals(COORDINADOR)) {
            log("Mensaje sin destino: " + m.getTipo());
            return;
        }
        if (!estaActiva(m.getDestino()) || !enviarMensaje(m.getDestino(), m)) {
            log("No se pudo entregar " + m.getTipo() + " a " + m.getDestino());
        }
    }

    public void revisarHeartbeats() {
        while (activo) {
            try {
                Thread.sleep(REVISION_HEARTBEAT_MS);
            } catch (InterruptedException e) {
                return;
            }
            long ahora = System.currentTimeMillis();
            for (String region : regionesActivas()) {
                Long ultimo = ultimoHeartbeat.get(region);
                if (ultimo != null && ahora - ultimo > TIMEOUT_HEARTBEAT_MS) {
                    marcarRegionCaida(region, "sin heartbeat");
                }
            }
        }
    }

    // Tolerancia a fallos: se marca como caída, se rechazan migraciones hacia ella
    // y la simulación sigue con las demás.
    public void marcarRegionCaida(String idRegion, String motivo) {
        if (!activo || !regionesCaidas.add(idRegion)) return;
        log("Región CAÍDA: " + idRegion + " (" + motivo + ")");
        ConexionTCP conexion = conexiones.remove(idRegion);
        if (conexion != null) conexion.cerrar();
        monitor.olvidarRegion(idRegion);
        difundir(new Mensaje(TipoMensaje.REGION_CAIDA, COORDINADOR, null, reloj.getDiaActual())
                .con("region", idRegion));
        reloj.despertar();
    }

    public synchronized void esperarRegiones() {
        while (regionesActivas().size() < minRegiones) {
            try {
                wait();
            } catch (InterruptedException e) {
                return;
            }
        }
        log("Empieza la simulación con " + regionesActivas());
    }

    public List<String> regionesActivas() {
        List<String> activas = new ArrayList<>(regiones);
        activas.removeAll(regionesCaidas);
        return activas;
    }

    private boolean estaActiva(String region) {
        return regiones.contains(region) && !regionesCaidas.contains(region);
    }

    public void enviarAPaneles(Mensaje m) {
        for (String panel : paneles) enviarMensaje(panel, m);
    }

    private void difundir(Mensaje m) {
        for (String nodo : conexiones.keySet()) enviarMensaje(nodo, m);
    }

    public void finalizar() {
        log("Simulación terminada en el día " + reloj.getDiaActual());
        difundir(new Mensaje(TipoMensaje.FIN, COORDINADOR, null, reloj.getDiaActual()));
        activo = false;
        try {
            servidor.close();
        } catch (IOException ignored) {
        }
        conexiones.values().forEach(ConexionTCP::cerrar);
    }

    private static void iniciarHilo(String nombre, Runnable tarea) {
        Thread t = new Thread(tarea, nombre);
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void log(String texto) {
        super.log(texto);
    }

    public Monitor getMonitor() { return monitor; }
    public Reloj getReloj() { return reloj; }

    // Uso: NodoCoordinador [puerto] [días] [pausaMs] [mínimo de regiones]
    //                      [% reproducción plantas] [% herbívoros] [% depredadores] [% descomponedores]
    public static void main(String[] args) {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        int dias = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        long pausa = args.length > 2 ? Long.parseLong(args[2]) : 300;
        int minRegiones = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        TasasReproduccion d = TasasReproduccion.porDefecto();
        TasasReproduccion tasas = new TasasReproduccion(
                args.length > 4 ? Integer.parseInt(args[4]) : d.plantas(),
                args.length > 5 ? Integer.parseInt(args[5]) : d.herbivoros(),
                args.length > 6 ? Integer.parseInt(args[6]) : d.depredadores(),
                args.length > 7 ? Integer.parseInt(args[7]) : d.descomponedores());
        new NodoCoordinador(puerto, dias, pausa, minRegiones, tasas).run();
    }
}

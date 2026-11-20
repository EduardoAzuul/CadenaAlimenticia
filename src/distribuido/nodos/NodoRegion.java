package distribuido.nodos;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import distribuido.ConexionTCP;
import distribuido.Mensaje;
import distribuido.ProtocoloMigracion;
import distribuido.TipoMensaje;
import modelo.Especie;
import modelo.TasasReproduccion;
import region.Region;
import region.recursos.Nido;

// Un proceso por región: tiene sus agentes y búferes locales y solo avanza cuando el
// coordinador manda TICK. Toda la autoridad sobre sus recursos es suya.
public class NodoRegion extends Nodo {
    public static final long HEARTBEAT_MS = 1000;

    private final Region region;
    private final ProtocoloMigracion migracion;
    private final ConfigRegion config;
    private volatile int tickActual = 0;

    // Un solo hilo procesa los días en orden; el hilo lector queda libre para la red.
    private final ExecutorService hiloDias = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService hiloLatidos = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NodoRegion(String id, String host, int puerto, List<String> vecinos, ConfigRegion config) {
        super(id, host, puerto);
        this.config = config;
        this.region = new Region(id, Map.of(
                Especie.PLANTA, config.capacidadPlantas(),
                Especie.HERBIVORO, config.capacidadHerbivoros(),
                Especie.DEPREDADOR, config.capacidadDepredadores(),
                Especie.DESCOMPONEDOR, config.capacidadDescomponedores()),
                config.nutrientesIniciales(),
                Map.of(Especie.HERBIVORO, config.nidosHerbivoros(), Especie.DEPREDADOR, config.nidosDepredadores()));
        this.region.getVecinas().addAll(vecinos);
        this.migracion = new ProtocoloMigracion(id, region, this::enviar, () -> tickActual);
        this.region.setPuertaMigracion(migracion);
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
        enviar(new Mensaje(TipoMensaje.REGISTRO, id, COORDINADOR, 0).con("rol", "REGION"));
        log("Conectada. Vecinas: " + region.getVecinas());

        poblar();
        hiloLatidos.scheduleAtFixedRate(this::enviarHeartbeat, 0, HEARTBEAT_MS, TimeUnit.MILLISECONDS);

        escuchar(coordinador);
        detener();
    }

    private void poblar() {
        region.poblarInicial(Especie.PLANTA, config.plantas());
        region.poblarInicial(Especie.HERBIVORO, config.herbivoros());
        region.poblarInicial(Especie.DEPREDADOR, config.depredadores());
        region.poblarInicial(Especie.DESCOMPONEDOR, config.descomponedores());
    }

    @Override
    public void procesarMensaje(Mensaje m) {
        switch (m.getTipo()) {
            case TICK -> hiloDias.submit(() -> procesarDia(m.getDia()));
            case MIGRATION_REQUEST -> migracion.responderSolicitud(m);
            case MIGRATION_ACK -> migracion.recibirRespuesta(m, true);
            case MIGRATION_NACK -> migracion.recibirRespuesta(m, false);
            case MIGRATION_TRANSFER -> migracion.recibirTransferencia(m);
            case MIGRATION_CANCEL -> migracion.liberarReserva(m);
            case MIGRATION_ARRIVED -> migracion.confirmarLlegada(m);
            case REGION_CAIDA -> {
                log("La región " + m.getTexto("region") + " se cayó");
                migracion.regionCaida(m.getTexto("region"));
            }
            case REGISTRO -> migracion.regionRecuperada(m.getTexto("region"));
            case CONFIGURACION -> {
                if (m.getMapa("tasasReproduccion") instanceof Map<?, ?> tasas) {
                    region.setTasasReproduccion(TasasReproduccion.desdeMapa(tasas));
                    log("Tasas de reproducción: " + region.getTasasReproduccion());
                }
            }
            case FIN -> {
                log("Fin de la simulación");
                activo = false;
                conexiones.get(COORDINADOR).cerrar();
            }
            default -> log("Mensaje inesperado: " + m.getTipo());
        }
    }

    // Lo que pasa en un día, en orden.
    private void procesarDia(int dia) {
        recibirTick(dia);
        int llegaron = aplicarMigracionesPendientes(); // las migraciones se aplican al inicio del tick
        region.meteorizarSuelo();
        region.germinarBancoDeSemillas();
        region.avanzarDia();                            // todos los agentes hacen su día
        enviarStats(llegaron);
        reportarTickDone();
    }

    public void recibirTick(int dia) {
        tickActual = dia;
    }

    public int aplicarMigracionesPendientes() {
        return migracion.aplicarLlegadas();
    }

    public void reportarTickDone() {
        enviar(new Mensaje(TipoMensaje.TICK_DONE, id, COORDINADOR, tickActual));
    }

    public void enviarHeartbeat() {
        enviar(new Mensaje(TipoMensaje.HEARTBEAT, id, COORDINADOR, tickActual));
    }

    public void enviarStats(int llegaron) {
        enviar(new Mensaje(TipoMensaje.STATS, id, COORDINADOR, tickActual)
                .conPoblaciones(region.getContadores().foto())
                .con("biomasa", region.biomasaTotal())
                .con("biomasaMax", region.biomasaMaxima())
                .con("materiaMuerta", region.cadaveresTotales())
                .con("nutrientes", region.getReservaNutrientes().tamano())
                .con("nutrientesMax", region.getReservaNutrientes().getCapacidad())
                .conNidos(criasPorNido(Especie.HERBIVORO), criasPorNido(Especie.DEPREDADOR))
                .conMuertes(region.fotoMuertes())
                .conAnimales(region.fotoAnimales())
                .con("llegaron", llegaron));
    }

    private List<Integer> criasPorNido(Especie especie) {
        return region.getNidos(especie).stream().map(Nido::getCrias).toList();
    }

    private void enviar(Mensaje m) {
        enviarMensaje(COORDINADOR, m);
    }

    public void detener() {
        activo = false;
        hiloLatidos.shutdownNow();
        hiloDias.shutdownNow();
        region.detener();
        ConexionTCP c = conexiones.get(COORDINADOR);
        if (c != null) c.cerrar();
    }

    public Region getRegion() { return region; }

    // Uso: NodoRegion <nombre> <vecinas> [host] [puerto] [plantas] [herbívoros] [depredadores] [descomponedores]
    //                  [capPlantas] [capHerbívoros] [capDepredadores] [capDescomponedores]
    //                  [nidosHerbívoros] [nidosDepredadores] [nutrientesIniciales]
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: NodoRegion <nombre> <vecina1,vecina2> [host=localhost] [puerto=5000] "
                    + "[plantas=20] [herbivoros=10] [depredadores=3] [descomponedores=5] "
                    + "[capPlantas=80] [capHerbivoros=60] [capDepredadores=15] [capDescomponedores=30] "
                    + "[nidosHerbivoros=6] [nidosDepredadores=3] [nutrientesIniciales=150]");
            return;
        }
        String nombre = args[0];
        List<String> vecinas = Arrays.stream(args[1].split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String host = args.length > 2 ? args[2] : "localhost";
        int puerto = args.length > 3 ? Integer.parseInt(args[3]) : 5000;
        ConfigRegion d = ConfigRegion.porDefecto();
        ConfigRegion config = new ConfigRegion(
                entero(args, 4, d.plantas()), entero(args, 5, d.herbivoros()),
                entero(args, 6, d.depredadores()), entero(args, 7, d.descomponedores()),
                entero(args, 8, d.capacidadPlantas()), entero(args, 9, d.capacidadHerbivoros()),
                entero(args, 10, d.capacidadDepredadores()), entero(args, 11, d.capacidadDescomponedores()),
                entero(args, 12, d.nidosHerbivoros()), entero(args, 13, d.nidosDepredadores()),
                entero(args, 14, d.nutrientesIniciales()));
        new NodoRegion(nombre, host, puerto, vecinas, config).run();
    }

    private static int entero(String[] args, int i, int porDefecto) {
        return args.length > i ? Integer.parseInt(args[i]) : porDefecto;
    }
}

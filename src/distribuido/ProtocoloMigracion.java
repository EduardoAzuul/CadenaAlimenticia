package distribuido;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import agentes.Agente;
import agentes.Animal;
import agentes.Depredador;
import agentes.Herbivoro;
import modelo.Especie;
import region.PuertaMigracion;
import region.Region;

// Migración en dos fases para que ningún animal se duplique ni se pierda.
//
//  ORIGEN                                   DESTINO
//  1. MIGRATION_REQUEST ------------------>  reserva lugar en su semáforo de capacidad
//                       <------------------  MIGRATION_ACK  (o NACK si no hay lugar)
//  2. el animal sale (VIVO -> MIGRADO)
//     MIGRATION_TRANSFER (su estado) ----->  lo guarda para el inicio del siguiente día
//  3.                   <------------------  MIGRATION_ARRIVED al crearlo
//
// Si el ACK no llega a tiempo: se cancela y el animal se queda (MIGRATION_CANCEL libera la reserva).
// Si la región destino se cae antes de confirmar: el animal regresa al origen.
public class ProtocoloMigracion implements PuertaMigracion {
    public static final long TIMEOUT_RESPUESTA_MS = 500;

    private record Reserva(Especie especie, String origen) {}

    private final String idNodo;
    private final Region region;
    private final Consumer<Mensaje> enviar;
    private final IntSupplier dia;
    private final AtomicInteger contador = new AtomicInteger();

    // En el origen
    private final Map<String, CompletableFuture<Boolean>> respuestasPendientes = new ConcurrentHashMap<>();
    private final Map<String, Mensaje> enTransito = new ConcurrentHashMap<>();
    // En el destino
    private final Map<String, Reserva> reservas = new ConcurrentHashMap<>();
    private final Queue<Mensaje> llegadasPendientes = new ConcurrentLinkedQueue<>();

    private final Set<String> regionesCaidas = ConcurrentHashMap.newKeySet();

    public ProtocoloMigracion(String idNodo, Region region, Consumer<Mensaje> enviar, IntSupplier dia) {
        this.idNodo = idNodo;
        this.region = region;
        this.enviar = enviar;
        this.dia = dia;
    }

    // ================= ORIGEN =================

    // Lo llama el hilo del animal (desde Animal.migrar()). Bloquea máximo TIMEOUT_RESPUESTA_MS.
    @Override
    public boolean migrar(Animal animal) {
        List<String> destinos = new ArrayList<>(region.getVecinas());
        destinos.removeAll(regionesCaidas);
        if (destinos.isEmpty()) {
            return false;
        }
        String destino = destinos.get(ThreadLocalRandom.current().nextInt(destinos.size()));
        Especie especie = Region.especieDe(animal);
        String idMigracion = idNodo + "-" + contador.incrementAndGet();

        // Fase 1: pedir lugar
        CompletableFuture<Boolean> respuesta = new CompletableFuture<>();
        respuestasPendientes.put(idMigracion, respuesta);
        enviar.accept(new Mensaje(TipoMensaje.MIGRATION_REQUEST, idNodo, destino, dia.getAsInt())
                .con("idMigracion", idMigracion)
                .con("especie", especie.name()));
        boolean aceptada;
        try {
            aceptada = respuesta.get(TIMEOUT_RESPUESTA_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancelar(idMigracion, destino); // por si el destino sí reservó pero tarde
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            respuestasPendientes.remove(idMigracion);
        }
        if (!aceptada) {
            return false;
        }

        // Fase 2: el animal sale de aquí. Si lo cazaron mientras esperaba, se cancela.
        if (!animal.salirDeRegion(destino)) {
            cancelar(idMigracion, destino);
            return false;
        }
        Mensaje transferencia = new Mensaje(TipoMensaje.MIGRATION_TRANSFER, idNodo, destino, dia.getAsInt())
                .con("idMigracion", idMigracion)
                .con("especie", especie.name())
                .con("energia", animal.getEnergia())
                .con("edad", animal.getEdad());
        enTransito.put(idMigracion, transferencia);
        enviar.accept(transferencia);
        return true;
    }

    public void recibirRespuesta(Mensaje m, boolean aceptada) {
        CompletableFuture<Boolean> respuesta = respuestasPendientes.get(m.getTexto("idMigracion"));
        if (respuesta != null) {
            respuesta.complete(aceptada);
        }
    }

    // Fase 3 en el origen: el destino confirmó.
    public void confirmarLlegada(Mensaje m) {
        enTransito.remove(m.getTexto("idMigracion"));
    }

    private void cancelar(String idMigracion, String destino) {
        enviar.accept(new Mensaje(TipoMensaje.MIGRATION_CANCEL, idNodo, destino, dia.getAsInt())
                .con("idMigracion", idMigracion));
    }

    // ================= DESTINO =================

    // Lo llama el hilo lector de la red: no debe bloquear.
    public void responderSolicitud(Mensaje m) {
        Especie especie = Especie.valueOf(m.getTexto("especie"));
        boolean hayLugar = sePuedeCrear(especie) && region.reservarEspacio(especie);
        if (hayLugar) {
            reservas.put(m.getTexto("idMigracion"), new Reserva(especie, m.getOrigen()));
        }
        enviar.accept(new Mensaje(hayLugar ? TipoMensaje.MIGRATION_ACK : TipoMensaje.MIGRATION_NACK,
                idNodo, m.getOrigen(), dia.getAsInt())
                .con("idMigracion", m.getTexto("idMigracion")));
    }

    public void recibirTransferencia(Mensaje m) {
        Reserva reserva = reservas.remove(m.getTexto("idMigracion"));
        if (reserva == null && !region.reservarEspacio(Especie.valueOf(m.getTexto("especie")))) {
            // La reserva ya no existe y no hay lugar: no debería pasar, pero que quede registrado.
            region.getBitacora().registrar(dia.getAsInt(), "Se perdió un migrante " + m.getTexto("idMigracion"));
            return;
        }
        llegadasPendientes.add(m);
    }

    public void liberarReserva(Mensaje m) {
        Reserva reserva = reservas.remove(m.getTexto("idMigracion"));
        if (reserva != null) {
            region.liberarEspacio(reserva.especie());
        }
    }

    // Al inicio de cada día (entre días, sin agentes actuando) se crean los que llegaron.
    // Regresa cuántos se crearon.
    public int aplicarLlegadas() {
        int n = 0;
        Mensaje m;
        while ((m = llegadasPendientes.poll()) != null) {
            Agente agente = crearAgente(Especie.valueOf(m.getTexto("especie")),
                    m.getEntero("energia"), m.getEntero("edad"));
            region.agregarAgente(agente); // el lugar ya estaba reservado
            n++;
            if (m.getOrigen().equals(idNodo)) {
                region.getBitacora().registrar(dia.getAsInt(), agente + " regresó (su destino se cayó)");
            } else {
                region.getBitacora().registrar(dia.getAsInt(), agente + " llegó de " + m.getOrigen());
                enviar.accept(new Mensaje(TipoMensaje.MIGRATION_ARRIVED, idNodo, m.getOrigen(), dia.getAsInt())
                        .con("idMigracion", m.getTexto("idMigracion")));
            }
        }
        return n;
    }

    private static boolean sePuedeCrear(Especie especie) {
        return especie == Especie.HERBIVORO || especie == Especie.DEPREDADOR;
    }

    private Agente crearAgente(Especie especie, int energia, int edad) {
        return switch (especie) {
            case HERBIVORO -> new Herbivoro(region, energia, edad);
            case DEPREDADOR -> new Depredador(region, energia, edad);
            default -> throw new IllegalArgumentException("No se puede migrar " + especie);
        };
    }

    // ================= TOLERANCIA A FALLOS =================

    public void regionCaida(String nombre) {
        regionesCaidas.add(nombre);
        // Animales que iban hacia ella y no confirmaron: regresan aquí si hay lugar.
        enTransito.entrySet().removeIf(e -> {
            Mensaje t = e.getValue();
            if (!t.getDestino().equals(nombre)) return false;
            if (region.reservarEspacio(Especie.valueOf(t.getTexto("especie")))) {
                llegadasPendientes.add(t);
            } else {
                region.getBitacora().registrar(dia.getAsInt(), "Se perdió un migrante hacia " + nombre + " (sin lugar para regresar)");
            }
            return true;
        });
        // Reservas que hizo para ella y nunca se usarán.
        reservas.entrySet().removeIf(e -> {
            if (!e.getValue().origen().equals(nombre)) return false;
            region.liberarEspacio(e.getValue().especie());
            return true;
        });
    }

    public void regionRecuperada(String nombre) {
        regionesCaidas.remove(nombre);
    }
}

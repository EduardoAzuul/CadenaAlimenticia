package agentes;

import java.util.concurrent.locks.ReentrantLock;
import modelo.Actividad;
import modelo.EstadoAgente;
import modelo.Especie;
import region.PuertaMigracion;
import region.recursos.Nido;
import region.Region;

public abstract class Animal extends Agente {
    // Sección crítica: estado de la presa. Solo un depredador puede cazarla.
    protected final ReentrantLock lockEstado = new ReentrantLock();

    // Si no comió y le queda esta energía o menos, se duerme (solo cambia cómo se dibuja).
    public static final int ENERGIA_PARA_DORMIR = 4;

    // Lo escribe su propio hilo durante el día y lo lee el panel entre días: volatile basta.
    protected volatile Actividad actividad;
    // Nido (índice en su especie) donde se reprodujo; solo cuenta si actividad == REPRODUCE.
    private volatile int nidoReproduccion = -1;

    protected Animal(Region region, int energiaInicial) {
        this(region, energiaInicial, 0);
    }

    protected Animal(Region region, int energiaInicial, int edadInicial) {
        super(region, energiaInicial, edadInicial);
        actividad = edadInicial == 0 ? Actividad.NACE : Actividad.DESCANSA;
    }

    public Actividad getActividad() { return actividad; }

    public int getNidoReproduccion() {
        return actividad == Actividad.REPRODUCE ? nidoReproduccion : -1;
    }

    // Hoy no comió: descansa, o duerme si ya casi no tiene energía.
    protected void quedarseSinComer() {
        actividad = energia <= ENERGIA_PARA_DORMIR ? Actividad.DUERME : Actividad.DESCANSA;
    }

    // Lo llama un depredador. tryLock evita la condición de carrera entre depredadores:
    // solo uno logra pasar de VIVO a CAZADO.
    public boolean intentarSerCazado() {
        if (!lockEstado.tryLock()) {
            return false;
        }
        try {
            return estado.compareAndSet(EstadoAgente.VIVO, EstadoAgente.CAZADO);
        } finally {
            lockEstado.unlock();
        }
    }

    //public void moverAlAzar() {} //Por si lo quieren mover

    // Pide a la región que lo mande a una región vecina.
    // true = ya se fue; false = se queda (no hay lugar, timeout, o lo cazaron antes).
    public boolean migrar() {
        PuertaMigracion puerta = region.getPuertaMigracion();

        return puerta != null && estaVivo() && puerta.migrar(this);
    }

    // Fase 2 de la migración en el origen: el destino ya reservó lugar.
    // Usa el mismo lock que la caza para que no lo cacen y migre a la vez.
    public boolean salirDeRegion(String destino) {
        if (!lockEstado.tryLock()) {
            return false;
        }
        try {
            if (!estado.compareAndSet(EstadoAgente.VIVO, EstadoAgente.MIGRADO)) {
                return false;
            }
        } finally {
            lockEstado.unlock();
        }
        salirSinMorir("migró a " + destino);
        return true;
    }
    public abstract void buscarAlimento();

    // ---------------- Reproducción en nido ----------------
    // Necesita un nido de su especie y otro individuo de su especie.
    // El primero espera en el nido (con timeout); el segundo se une; el que esperaba crea la cría.
    public static final long TIMEOUT_NIDO_MS = 120;

    protected void reproducirseEnNido(int umbral) {
        if (!estaVivo() || energia < umbral) {
            return;
        }
        Especie especie = Region.especieDe(this);
        int aporte = energia / 2;
        // Solo puede esperar en un nido si ya apartó lugar para la cría (capacidad de carga).
        boolean lugarReservado = region.reservarEspacio(especie);
        Region.LugarEnNido lugar = region.buscarLugarEnNido(especie, this, aporte, lugarReservado);

        if (lugar == null) {                       // no hay nido libre ni pareja esperando
            if (lugarReservado) region.liberarEspacio(especie);
            return;
        }
        if (lugar.unido()) {                       // llegué con alguien que esperaba: él crea la cría
            if (lugarReservado) region.liberarEspacio(especie);
            energia -= aporte;
            nidoReproduccion = lugar.nido().getId();
            actividad = Actividad.REPRODUCE;
            return;
        }

        Nido.Pareja pareja = lugar.nido().esperarPareja(TIMEOUT_NIDO_MS);
        if (pareja == null || !estaVivo()) {       // nadie llegó (o me cazaron esperando)
            region.liberarEspacio(especie);
            return;
        }
        energia -= aporte;
        nidoReproduccion = lugar.nido().getId();
        actividad = Actividad.REPRODUCE;
        Animal cria = crearCria(aporte + pareja.aporte());
        lugar.nido().registrarCria();
        region.agregarAgente(cria);
        region.getBitacora().registrar(region.getDia(), this + " y " + pareja.animal()
                + " tuvieron una cría " + cria + " en el nido " + lugar.nido().getId());
    }

    protected abstract Animal crearCria(int energia);

    public void morirPorInanicion() {
        morir();
    }
}

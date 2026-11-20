package agentes;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import modelo.CausaMuerte;
import modelo.EstadoAgente;
import region.Region;

// Ciclo de vida de cada día: percibir, actuar, gastar energía, reproducirse o morir.
// Cada agente corre en su propio hilo y al terminar su día espera a los demás (barrera).
public abstract class Agente implements Runnable {
    private static final AtomicInteger siguienteId = new AtomicInteger(1);

    protected final int id;
    protected int energia;
    protected int edad;
    // Atómico porque otro hilo (un depredador) puede cambiarlo a CAZADO.
    protected final AtomicReference<EstadoAgente> estado = new AtomicReference<>(EstadoAgente.VIVO);
    protected final Region region;

    protected Agente(Region region, int energiaInicial) {
        this(region, energiaInicial, 0);
    }

    // Para agentes que llegan migrando desde otra región con su estado.
    protected Agente(Region region, int energiaInicial, int edadInicial) {
        this.id = siguienteId.getAndIncrement();
        this.region = region;
        this.energia = energiaInicial;
        this.edad = edadInicial;
    }

    @Override
    public void run() {
        // Si nació a mitad de un día (es cría), ese día ya no actúa: solo marca que terminó.
        if (region.estaEnMedioDelDia() && !region.terminarDia()) {
            return;
        }
        while (region.esperarInicioDia()) {
            if (!estaVivo()) {
                break; // lo cazaron mientras esperaba
            }
            percibir();
            actuar();
            if (estado.get() == EstadoAgente.MIGRADO) {
                return; // se fue a otra región; ya salió de esta
            }
            if (!estaVivo()) {
                break; // lo cazaron durante el día
            }
            gastarEnergia();
            envejecer();

            if (debeMorir()) {
                morir();
                // Si un depredador lo cazó justo ahora, morir() no hace nada: se sale
                // del ciclo y abajo se elimina como cazado (si no, se quedaría en la barrera).
                break;
            }
            if (quiereReproducirse()) {
                reproducirse();
            }

            if (!region.terminarDia()) {
                return; // la simulación terminó
            }
        }
        EstadoAgente estadoFinal = estado.get();
        if (estadoFinal == EstadoAgente.CAZADO || estadoFinal == EstadoAgente.COMIDA) {
            fueEliminado(); // lo cazaron (animal) o se lo comieron entero (planta)
        }
    }

    // ---- Pasos del ciclo que define cada especie ----
    public abstract void percibir();
    public abstract void actuar();
    public abstract void reproducirse();
    protected abstract int costoEnergiaDiario();
    protected abstract int edadMaxima();

    // ---- Pasos comunes ----

    // Tasa de reproducción de su especie (general para todo el sistema): solo algunos días lo intenta.
    protected boolean quiereReproducirse() {
        int tasa = region.getTasasReproduccion().de(Region.especieDe(this));
        return ThreadLocalRandom.current().nextInt(100) < tasa;
    }
    public void gastarEnergia() {
        energia -= costoEnergiaDiario();
    }

    public void envejecer() {
        edad++;
    }

    // Muerte natural (hambre o vejez): deja cadáver para los descomponedores.
    public void morir() {
        if (estado.compareAndSet(EstadoAgente.VIVO, EstadoAgente.MUERTO)) {
            finalizar(true, causaMuerte());
        }
    }

    // Cada especie puede agregar sus propias causas de muerte.
    protected boolean debeMorir() {
        return energia <= 0 || edad > edadMaxima();
    }

    protected CausaMuerte causaMuerte() {
        return energia <= 0 ? CausaMuerte.HAMBRE : CausaMuerte.VEJEZ;
    }

    // Lo sacaron de la simulación desde fuera (p. ej. lo cazaron). Por defecto sin cadáver.
    protected void fueEliminado() {
        finalizar(false, CausaMuerte.OTRA);
    }

    // Muere: se cuenta en las muertes de la región por especie y causa.
    protected final void finalizar(boolean dejarCadaver, CausaMuerte causa) {
        region.registrarMuerte(Region.especieDe(this), causa);
        salirDeLaRegion(dejarCadaver, causa.textoBitacora());
    }

    // Sale de la región sin morir (p. ej. migró): no cuenta como muerte.
    protected final void salirSinMorir(String motivo) {
        salirDeLaRegion(false, motivo);
    }

    private void salirDeLaRegion(boolean dejarCadaver, String motivo) {
        alMorir();
        if (dejarCadaver) {
            region.depositarMateriaMuerta(this);
        }
        region.eliminarAgente(this);
        region.getBitacora().registrar(region.getDia(), this + " " + motivo);
    }


    public void alNacer() {}
    protected void alMorir() {}

    public boolean estaVivo() {
        return estado.get() == EstadoAgente.VIVO;
    }

    public int getId() { return id; }
    public int getEnergia() { return energia; }
    public int getEdad() { return edad; }
    public EstadoAgente getEstado() { return estado.get(); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + id;
    }
}

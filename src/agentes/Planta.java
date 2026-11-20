package agentes;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import modelo.CausaMuerte;
import modelo.EstadoAgente;
import modelo.Especie;
import region.Region;

// Consumidor de la reserva de nutrientes y alimento de los herbívoros.
// Su biomasa (tamaño) crece con la fotosíntesis y más con nutrientes; un herbívoro
// se la come entera y la mata. Sin nutrientes por varios días se seca.
public class Planta extends Agente {
    public static final int ENERGIA_INICIAL = 6;
    public static final int COSTO_DIARIO = 2;
    public static final int ENERGIA_FOTOSINTESIS = 1;     // gratis, todos los días
    public static final int ENERGIA_POR_NUTRIENTE = 3;
    public static final int CRECIMIENTO_CON_NUTRIENTE = 2;
    public static final int CRECIMIENTO_SIN_NUTRIENTE = 1;
    public static final int BIOMASA_INICIAL = 2;
    // Los brotes (plantas de menos de estos días) son muy chicos: los herbívoros no los comen.
    // Sin esto, una semilla recién nacida moría comida al instante y las plantas se podían acabar.
    public static final int DIAS_BROTE = 3;
    public static final int TAMANO_MAXIMO = 8;
    public static final int UMBRAL_REPRODUCCION = 12;
    public static final int SUELO_MIN_PARA_SEMBRAR = 15;   // nutrientes que debe ver para soltar semilla
    public static final int DIAS_SIN_NUTRIENTES_TOLERADOS = 3;
    public static final int MAX_DIAS_SIN_NUTRIENTES = 8;   // a partir de aquí se seca seguro
    public static final int EDAD_MAXIMA = 80;
    public static final long TIMEOUT_NUTRIENTE_MS = 20;

    // Sección crítica: la biomasa la cambian la planta (crece) y los herbívoros (muerden).
    private final ReentrantLock lockBiomasa = new ReentrantLock();
    private int biomasa = BIOMASA_INICIAL;
    private volatile boolean esBrote = true;
    private int nutrientesVistos;
    private boolean absorbioHoy;
    private int diasSinNutrientes = 0;
    private boolean seSeco = false;

    public Planta(Region region) {
        this(region, ENERGIA_INICIAL);
    }

    public Planta(Region region, int energiaInicial) {
        super(region, energiaInicial);
    }

    public Planta(Region region, int energiaInicial, int edadInicial) {
        super(region, energiaInicial, edadInicial);
    }

    @Override
    public void percibir() {
        nutrientesVistos = region.getReservaNutrientes().tamano();
    }

    @Override
    public void actuar() {
        fotosintesis();
        absorberNutrientes();
        crecer();
    }

    public void fotosintesis() {
        energia += ENERGIA_FOTOSINTESIS;
    }

    // Consumidor de la zona crítica de nutrientes del suelo.
    public void absorberNutrientes() {
        absorbioHoy = region.consumirNutriente(TIMEOUT_NUTRIENTE_MS);
        if (absorbioHoy) {
            energia += ENERGIA_POR_NUTRIENTE;
            diasSinNutrientes = 0;
        } else {
            diasSinNutrientes++;
        }
    }

    public void crecer() {
        esBrote = edad < DIAS_BROTE;
        lockBiomasa.lock();
        try {
            int crecimiento = absorbioHoy ? CRECIMIENTO_CON_NUTRIENTE : CRECIMIENTO_SIN_NUTRIENTE;
            biomasa = Math.min(TAMANO_MAXIMO, biomasa + crecimiento);
        } finally {
            lockBiomasa.unlock();
        }
    }

    // Lo llama un herbívoro (desde su hilo): se come la planta entera y la mata.
    // Regresa la biomasa que se llevó (0 si es brote o si ya estaba muerta).
    // El lock evita que crezca mientras se la comen; el compareAndSet hace que solo
    // un herbívoro (o ninguno, si se secó antes) se la lleve.
    public int serDevorada() {
        if (esBrote) {
            return 0;
        }
        lockBiomasa.lock();
        try {
            if (!estado.compareAndSet(EstadoAgente.VIVO, EstadoAgente.COMIDA)) {
                return 0;
            }
            int tomado = biomasa;
            biomasa = 0;
            return tomado;
        } finally {
            lockBiomasa.unlock();
        }
    }

    // Al nacer queda disponible para los herbívoros; al morir se quita.
    @Override
    public void alNacer() {
        region.getPlantasDisponibles().intentarProducir(this);
    }

    @Override
    protected void alMorir() {
        region.getPlantasDisponibles().remover(this);
    }

    // Se la comieron entera: no deja restos.
    @Override
    protected void fueEliminado() {
        finalizar(false, CausaMuerte.COMIDA);
    }

    @Override
    public void reproducirse() {
        reproducirsePorSemillas();
    }

    public void reproducirsePorSemillas() {
        if (!estaVivo() || energia < UMBRAL_REPRODUCCION) {
            return;
        }
        // Solo siembra si el suelo da para otra planta (evita que se acaben los nutrientes de golpe).
        if (!absorbioHoy || nutrientesVistos < SUELO_MIN_PARA_SEMBRAR) {
            return;
        }
        if (!region.reservarEspacio(Especie.PLANTA)) {
            return;
        }
        int energiaSemilla = energia / 2;
        energia -= energiaSemilla;
        Planta brote = new Planta(region, energiaSemilla);
        region.agregarAgente(brote);
        region.getBitacora().registrar(region.getDia(), this + " soltó una semilla " + brote);
    }

    // Sin nutrientes aguanta unos días; después cada día tiene más probabilidad de secarse.
    // Así no se secan todas el mismo día.
    @Override
    protected boolean debeMorir() {
        if (super.debeMorir()) {
            return true;
        }
        if (diasSinNutrientes > DIAS_SIN_NUTRIENTES_TOLERADOS) {
            double probabilidad = (double) (diasSinNutrientes - DIAS_SIN_NUTRIENTES_TOLERADOS)
                    / (MAX_DIAS_SIN_NUTRIENTES - DIAS_SIN_NUTRIENTES_TOLERADOS);
            seSeco = ThreadLocalRandom.current().nextDouble() < probabilidad;
        }
        return seSeco;
    }

    @Override
    protected CausaMuerte causaMuerte() {
        return seSeco ? CausaMuerte.SECA : super.causaMuerte();
    }

    // Se seca: deja su materia (planta seca) para los descomponedores.
    public void morirPorFaltaDeNutrientes() {
        morir();
    }

    public int getBiomasa() {
        lockBiomasa.lock();
        try {
            return biomasa;
        } finally {
            lockBiomasa.unlock();
        }
    }
    public int getNutrientesVistos() { return nutrientesVistos; }

    @Override
    protected int costoEnergiaDiario() { return COSTO_DIARIO; }

    @Override
    protected int edadMaxima() { return EDAD_MAXIMA; }
}

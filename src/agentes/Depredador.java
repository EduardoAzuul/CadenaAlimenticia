package agentes;

import java.util.concurrent.ThreadLocalRandom;
import modelo.Actividad;
import region.Region;

// Consumidor del búfer de presas disponibles (los herbívoros son los productores).
// La caza usa la sección crítica del estado de la presa: tryLock + VIVO -> CAZADO.
public class Depredador extends Animal {

    public static final int ENERGIA_INICIAL = 15;
    public static final int COSTO_DIARIO = 2;
    public static final int ENERGIA_POR_PRESA = 15;
    public static final int DIAS_DESCANSO = 5;            // digestión: después de cazar no vuelve a cazar en 5 días

    // La presa puede escapar. Con pocas presas es más difícil cazar (se esconden mejor):
    // éxito = EXITO_MAXIMO * presas / (presas + PRESAS_MEDIA_EFICACIA).
    public static final double EXITO_MAXIMO = 0.7;
    public static final int PRESAS_MEDIA_EFICACIA = 20;
    public static final int UMBRAL_REPRODUCCION = 25;

    // Esperanza de vida: cada depredador vive EDAD_MAXIMA ± VARIACION_EDAD días (no todos mueren el mismo día).
    public static final int EDAD_MAXIMA = 45;
    public static final int VARIACION_EDAD = 10;
    public static final long TIMEOUT_PRESA_MS = 50;
    public static final int ENERGIA_MIN_MIGRAR = 8;
    public static final double PROBABILIDAD_MIGRAR = 0.3;

    private int presasVistas;
    private int diasDescanso = 0;
    private final int edadMaximaPropia =
            EDAD_MAXIMA + ThreadLocalRandom.current().nextInt(-VARIACION_EDAD, VARIACION_EDAD + 1);

    public Depredador(Region region) {
        this(region, ENERGIA_INICIAL);
    }

    public Depredador(Region region, int energiaInicial) {
        super(region, energiaInicial);
    }

    public Depredador(Region region, int energiaInicial, int edadInicial) {
        super(region, energiaInicial, edadInicial);
    }

    @Override
    public void percibir() {
        presasVistas = region.getBuferPresas().tamano();
    }

    @Override
    public void actuar() {
        if (diasDescanso > 0) {
            descansar();
            return;
        }
        if (quiereMigrar() && migrar()) {
            return;
        }
        buscarAlimento();
    }

    // Sin presas a la vista y con fuerzas para el viaje: a veces intenta irse.
    private boolean quiereMigrar() {
        return presasVistas == 0
                && energia >= ENERGIA_MIN_MIGRAR
                && ThreadLocalRandom.current().nextDouble() < PROBABILIDAD_MIGRAR;
    }

    @Override
    public void buscarAlimento() {
        rastrearPresa();
    }

    // Toma una presa del búfer (espera con timeout) e intenta cazarla.
    public void rastrearPresa() {
        Herbivoro presa = region.getBuferPresas().consumirConTimeout(TIMEOUT_PRESA_MS);
        if (presa != null) {
            cazar(presa);
        } else {
            quedarseSinComer();
        }
    }

    public void cazar(Herbivoro presa) {
        boolean escapo = ThreadLocalRandom.current().nextDouble() >= probabilidadExito();

        if (!escapo && presa.intentarSerCazado()) {
            energia += ENERGIA_POR_PRESA;
            actividad = Actividad.COME;
            diasDescanso = DIAS_DESCANSO;

            region.getBitacora().registrar(region.getDia(), this + " cazó a " + presa);

            return;
        }
        // Escapó, o alguien más la tenía (otro depredador, o estaba migrando):
        // si sigue viva, vuelve a estar disponible como presa.
        if (presa.estaVivo()) {
            region.getBuferPresas().intentarProducir(presa);
        }
        quedarseSinComer();
    }

    private double probabilidadExito() {
        return EXITO_MAXIMO * presasVistas / (double) (presasVistas + PRESAS_MEDIA_EFICACIA);
    }

    // Descansa (digiere) tras la caza: no caza, pero gasta su costo diario como cualquier día.
    public void descansar() {
        actividad = Actividad.DESCANSA;
        diasDescanso--;
    }

    // En pareja, dentro de un nido de depredadores (ver Animal.reproducirseEnNido).
    @Override
    public void reproducirse() {
        reproducirseEnNido(UMBRAL_REPRODUCCION);
    }

    @Override
    protected Animal crearCria(int energia) {
        return new Depredador(region, energia);
    }

    @Override
    protected int costoEnergiaDiario() { return COSTO_DIARIO; }

    @Override
    protected int edadMaxima() { return edadMaximaPropia; }

    public boolean estaDescansando() { return diasDescanso > 0; }
}

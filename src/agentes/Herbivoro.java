package agentes;

import java.util.concurrent.ThreadLocalRandom;
import modelo.Actividad;
import modelo.CausaMuerte;
import modelo.Especie;
import region.Region;

public class Herbivoro extends Animal {

    public static final int ENERGIA_INICIAL = 10;
    public static final int COSTO_DIARIO = 2;
    public static final int ENERGIA_POR_BIOMASA = 2;    // energía por unidad de biomasa de la planta que se come
    public static final int PLANTAS_A_REVISAR = 3;      // si le toca un brote (no se come), prueba otra
    public static final int UMBRAL_REPRODUCCION = 16;
    public static final int EDAD_MAXIMA = 30;
    public static final long TIMEOUT_COMIDA_MS = 50;
    public static final int ENERGIA_MIN_MIGRAR = 6;
    public static final double PROBABILIDAD_MIGRAR = 0.3;

    private int plantasVistas;

    public Herbivoro(Region region) {
        this(region, ENERGIA_INICIAL);
    }

    public Herbivoro(Region region, int energiaInicial) {
        super(region, energiaInicial);
    }

    public Herbivoro(Region region, int energiaInicial, int edadInicial) {
        super(region, energiaInicial, edadInicial);
    }

    @Override
    public void alNacer() {
        // Queda disponible como presa para los depredadores.
        region.getBuferPresas().intentarProducir(this);
    }

    @Override
    protected void alMorir() {
        region.getBuferPresas().remover(this);
    }

    @Override
    public void percibir() {
        plantasVistas = region.getPlantasDisponibles().tamano();
    }

    @Override
    public void actuar() {
        moverAlAzar();
        if (quiereMigrar() && migrar()) {
            return;
        }
        buscarAlimento();
    }

    public void moverAlAzar(){

    }

    // Sin comida a la vista y con fuerzas para el viaje: a veces intenta irse.
    private boolean quiereMigrar() {
        return plantasVistas == 0
                && energia >= ENERGIA_MIN_MIGRAR
                && ThreadLocalRandom.current().nextDouble() < PROBABILIDAD_MIGRAR;
    }

    @Override
    public void buscarAlimento() {
        consumirBiomasa();
    }

    // Consumidor de la zona crítica de plantas disponibles: toma una planta y se la come
    // entera (la mata), ganando energía según su biomasa. La planta ya no regresa al búfer.
    // Si le toca un brote (no se come) lo regresa y prueba otra.
    // Si no hay plantas espera con timeout y ese día no come (solo paga su costo diario).
    public void consumirBiomasa() {
        for (int intento = 0; intento < PLANTAS_A_REVISAR; intento++) {
            Planta planta = region.getPlantasDisponibles().consumirConTimeout(TIMEOUT_COMIDA_MS);
            if (planta == null) {
                break;                                   // no hay plantas: se queda con hambre
            }
            int comido = planta.serDevorada();
            if (comido > 0) {
                energia += comido * ENERGIA_POR_BIOMASA;
                actividad = Actividad.COME;
                region.getBitacora().registrar(region.getDia(), this + " se comió " + planta);
                return;
            }
            if (planta.estaVivo()) {
                region.getPlantasDisponibles().intentarProducir(planta); // era brote: la regresa
            }
        }
        quedarseSinComer();
    }

    @Override
    public void reproducirse() {
        reproducirseEnNido(UMBRAL_REPRODUCCION);
    }

    @Override
    protected Animal crearCria(int energia) {
        return new Herbivoro(region, energia);
    }

    @Override
    protected void fueEliminado() {
        morirPorCaza();
    }

    // El depredador se lo comió: los restos van a la materia muerta para los descomponedores.
    public void morirPorCaza() {
        finalizar(true, CausaMuerte.CAZADO);
    }

    public int getPlantasVistas() { return plantasVistas; }

    @Override
    protected int costoEnergiaDiario() { return COSTO_DIARIO; }

    @Override
    protected int edadMaxima() { return EDAD_MAXIMA; }
}

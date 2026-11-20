package agentes;

import modelo.Especie;
import region.Region;

// Consumidor del búfer de materia muerta y productor de la reserva de nutrientes.
// Cierra el ciclo: cadáveres -> nutrientes -> (plantas) -> biomasa -> herbívoros -> cadáveres.
public class Descomponedor extends Agente {
    public static final int ENERGIA_INICIAL = 10;
    public static final int COSTO_DIARIO = 1;
    public static final int ENERGIA_POR_CADAVER = 4;
    public static final int NUTRIENTES_POR_CADAVER = 3;
    public static final int UMBRAL_REPRODUCCION = 16;
    public static final int EDAD_MAXIMA = 30;
    public static final long TIMEOUT_COMIDA_MS = 50;

    private int materiaMuertaVista;
    private Agente cadaverActual;

    public Descomponedor(Region region) {
        this(region, ENERGIA_INICIAL);
    }

    public Descomponedor(Region region, int energiaInicial) {
        super(region, energiaInicial);
    }

    public Descomponedor(Region region, int energiaInicial, int edadInicial) {
        super(region, energiaInicial, edadInicial);
    }

    @Override
    public void percibir() {
        materiaMuertaVista = region.getBuferMateriaMuerta().tamano();
    }

    @Override
    public void actuar() {
        detectarMateriaMuerta();
        if (cadaverActual != null) {
            descomponer();
            devolverNutrientes();
        }
    }

    // Consumidor de la zona crítica de materia muerta. Espera con timeout;
    // si no llega nada, ese día no come (solo paga su costo diario).
    public void detectarMateriaMuerta() {
        cadaverActual = region.consumirMateriaMuerta(TIMEOUT_COMIDA_MS);
    }

    public void descomponer() {
        energia += ENERGIA_POR_CADAVER;
    }

    // Productor de la reserva de nutrientes. Si está llena, lo que no cabe se pierde.
    public void devolverNutrientes() {
        region.depositarNutrientes(NUTRIENTES_POR_CADAVER);
        region.getBitacora().registrar(region.getDia(), this + " descompuso a " + cadaverActual);
        cadaverActual = null;
    }

    @Override
    public void reproducirse() {
        if (!estaVivo() || energia < UMBRAL_REPRODUCCION) {
            return;
        }
        if (!region.reservarEspacio(Especie.DESCOMPONEDOR)) {
            return;
        }
        int energiaHijo = energia / 2;
        energia -= energiaHijo;
        Descomponedor hijo = new Descomponedor(region, energiaHijo);
        region.agregarAgente(hijo);
        region.getBitacora().registrar(region.getDia(), this + " tuvo una cría " + hijo);
    }

    public int getMateriaMuertaVista() { return materiaMuertaVista; }

    @Override
    protected int costoEnergiaDiario() { return COSTO_DIARIO; }

    @Override
    protected int edadMaxima() { return EDAD_MAXIMA; }
}

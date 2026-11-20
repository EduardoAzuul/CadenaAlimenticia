package region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import agentes.Agente;
import agentes.Animal;
import agentes.Depredador;
import agentes.Descomponedor;
import agentes.Herbivoro;
import agentes.Planta;
import modelo.CausaMuerte;
import modelo.Especie;
import modelo.TasasReproduccion;
import modelo.VistaAnimal;
import region.recursos.Bitacora;
import region.recursos.BuferRecurso;
import region.recursos.ContadoresPoblacion;
import region.recursos.Nido;

public class Region {
    public static final int CAPACIDAD_MATERIA_MUERTA = 1000;
    public static final int CAPACIDAD_NUTRIENTES = 500;
    public static final int NIDOS_HERBIVOROS = 6;
    public static final int NIDOS_DEPREDADORES = 3;
    public static final int NUTRIENTES_INICIALES = 150; // el suelo empieza fértil
    public static final int NUTRIENTES_METEORIZACION = 4; // lo que el suelo recupera solo cada día (lluvia, roca)
    // Banco de semillas: si casi no quedan plantas pero el suelo es fértil, a veces brota una.
    public static final int BANCO_SEMILLAS_MAX_PLANTAS = 3;
    public static final int BANCO_SEMILLAS_MIN_NUTRIENTES = 10;
    public static final double BANCO_SEMILLAS_PROBABILIDAD = 0.5;

    private final String nombre;
    private final List<Agente> agentes = Collections.synchronizedList(new ArrayList<>());
    private final List<String> vecinas = new ArrayList<>(); // nombres de regiones vecinas (otros nodos)
    private volatile PuertaMigracion puertaMigracion;
    private volatile TasasReproduccion tasasReproduccion = TasasReproduccion.porDefecto();       // null = sin red, no se migra

    // Zonas críticas (búfer > 1)
    private final BuferRecurso<Planta> plantasDisponibles; // los herbívoros comen de aquí
    private final BuferRecurso<Herbivoro> buferPresas;
    private final BuferRecurso<Agente> buferMateriaMuerta = new BuferRecurso<>(CAPACIDAD_MATERIA_MUERTA);
    private final List<Agente> cadaveresFrescos = Collections.synchronizedList(new ArrayList<>());
    private final BuferRecurso<Integer> reservaNutrientes = new BuferRecurso<>(CAPACIDAD_NUTRIENTES);
    private final Map<Especie, Semaphore> capacidadCarga = new EnumMap<>(Especie.class);
    private final int capacidadPlantas;

    // Secciones críticas (búfer = 1)
    // Nidos por especie (solo herbívoros y depredadores se reproducen en nido).
    private final Map<Especie, List<Nido>> nidos = new EnumMap<>(Especie.class);
    private final ContadoresPoblacion contadores = new ContadoresPoblacion();
    // Muertes acumuladas por especie y causa (las escriben los hilos de los agentes).
    private final Map<Especie, Map<CausaMuerte, AtomicInteger>> muertes = new EnumMap<>(Especie.class);
    private final Bitacora bitacora = new Bitacora();

    // Barrera de días (TICK_DONE). La parte inicial (1) es la del Reloj.
    private final Phaser sincronizadorDia = new Phaser(1);
    private volatile int dia = 0;

    public Region(String nombre, Map<Especie, Integer> capacidades) {
        this(nombre, capacidades, NUTRIENTES_INICIALES);
    }

    public Region(String nombre, Map<Especie, Integer> capacidades, int nutrientesIniciales) {
        this(nombre, capacidades, nutrientesIniciales,
                Map.of(Especie.HERBIVORO, NIDOS_HERBIVOROS, Especie.DEPREDADOR, NIDOS_DEPREDADORES));
    }

    public Region(String nombre, Map<Especie, Integer> capacidades, int nutrientesIniciales,
                  Map<Especie, Integer> numeroDeNidos) {
        this.nombre = nombre;
        for (Especie e : Especie.values()) {
            capacidadCarga.put(e, new Semaphore(capacidades.getOrDefault(e, 0), true));
        }
        this.buferPresas = new BuferRecurso<>(Math.max(1, capacidades.getOrDefault(Especie.HERBIVORO, 0)));
        this.plantasDisponibles = new BuferRecurso<>(Math.max(1, capacidades.getOrDefault(Especie.PLANTA, 0)));
        this.capacidadPlantas = capacidades.getOrDefault(Especie.PLANTA, 0);
        for (Especie e : Especie.values()) {
            Map<CausaMuerte, AtomicInteger> porCausa = new EnumMap<>(CausaMuerte.class);
            for (CausaMuerte c : CausaMuerte.values()) porCausa.put(c, new AtomicInteger());
            muertes.put(e, porCausa);
            List<Nido> lista = new ArrayList<>();
            for (int i = 0; i < numeroDeNidos.getOrDefault(e, 0); i++) {
                lista.add(new Nido(i, e));
            }
            nidos.put(e, Collections.unmodifiableList(lista));
        }
        depositarNutrientes(nutrientesIniciales);
    }

    // ---- Búferes (zonas críticas) ----

    // Biomasa total: lo que suman todas las plantas vivas de la región.
    public int biomasaTotal() {
        int total = 0;
        for (Agente a : getAgentes()) {
            if (a instanceof Planta p && p.estaVivo()) total += p.getBiomasa();
        }
        return total;
    }

    // Lo más que podría haber: todas las plantas que caben, de tamaño máximo.
    public int biomasaMaxima() {
        return capacidadPlantas * Planta.TAMANO_MAXIMO;
    }

    // Productor de nutrientes (descomponedores). Regresa cuántas unidades cupieron.
    public int depositarNutrientes(int unidades) {
        int guardadas = 0;
        while (guardadas < unidades && reservaNutrientes.intentarProducir(1)) {
            guardadas++;
        }
        return guardadas;
    }

    // Consumidor de nutrientes (plantas). false si no llegó nada a tiempo.
    public boolean consumirNutriente(long timeoutMs) {
        return reservaNutrientes.consumirConTimeout(timeoutMs) != null;
    }

    // Consumidor de materia muerta (descomponedores). null si no llegó nada a tiempo.
    public Agente consumirMateriaMuerta(long timeoutMs) {
        return buferMateriaMuerta.consumirConTimeout(timeoutMs);
    }

    // Lo llama el Reloj de la región entre días: el suelo recupera algo de nutrientes solo.
    public void meteorizarSuelo() {
        depositarNutrientes(NUTRIENTES_METEORIZACION);
    }

    // Lo llama el Reloj de la región entre días (sin agentes actuando).
    public void germinarBancoDeSemillas() {
        if (contadores.obtener(Especie.PLANTA) < BANCO_SEMILLAS_MAX_PLANTAS
                && reservaNutrientes.tamano() >= BANCO_SEMILLAS_MIN_NUTRIENTES
                && ThreadLocalRandom.current().nextDouble() < BANCO_SEMILLAS_PROBABILIDAD
                && reservarEspacio(Especie.PLANTA)) {
            Planta brote = new Planta(this);
            agregarAgente(brote);
            bitacora.registrar(dia, brote + " brotó del banco de semillas");
        }
    }

    // Resultado de buscar lugar en un nido para reproducirse.
    public record LugarEnNido(Nido nido, boolean unido) {}

    // Busca pareja para un animal, de forma atómica por especie:
    //   1) si alguien de su especie espera en un nido, se une (unido = true);
    //   2) si no, y puedeEsperar, ocupa un nido vacío para esperar (unido = false);
    //   3) si no, null.
    // El lock por especie evita que dos animales ocupen nidos distintos al mismo tiempo
    // y se queden esperando cada uno solo. Orden de locks: especie -> nido.
    public LugarEnNido buscarLugarEnNido(Especie especie, Animal animal, int aporte, boolean puedeEsperar) {
        List<Nido> lista = nidos.get(especie);
        if (lista.isEmpty()) {
            return null;
        }
        // Se empieza en un nido al azar para que se repartan (si no, todos usarían el primero).
        int inicio = ThreadLocalRandom.current().nextInt(lista.size());
        synchronized (lista) {
            for (int k = 0; k < lista.size(); k++) {
                Nido nido = lista.get((inicio + k) % lista.size());
                if (nido.unirse(animal, aporte)) {
                    return new LugarEnNido(nido, true);
                }
            }
            if (puedeEsperar) {
                for (int k = 0; k < lista.size(); k++) {
                    Nido nido = lista.get((inicio + k) % lista.size());
                    if (nido.ocupar(animal)) {
                        return new LugarEnNido(nido, false);
                    }
                }
            }
            return null;
        }
    }

    // ---- Población ----

    // El lugar en la capacidad de carga se debe reservar antes con reservarEspacio.
    public void agregarAgente(Agente agente) {
        sincronizadorDia.register(); // entra a la barrera antes de arrancar su hilo
        agentes.add(agente);
        contadores.incrementar(especieDe(agente));
        agente.alNacer();
        Thread hilo = new Thread(agente, agente.toString());
        hilo.start();
    }

    // Lo llama el propio agente al morir, desde su hilo.
    public void eliminarAgente(Agente agente) {
        if (agentes.remove(agente)) {
            Especie especie = especieDe(agente);
            contadores.decrementar(especie);
            liberarEspacio(especie);
            sincronizadorDia.arriveAndDeregister();
        }
    }

    public void registrarMuerte(Especie especie, CausaMuerte causa) {
        muertes.get(especie).get(causa).incrementAndGet();
    }

    // Copia de las muertes acumuladas (solo las causas con al menos una).
    public Map<Especie, Map<CausaMuerte, Integer>> fotoMuertes() {
        Map<Especie, Map<CausaMuerte, Integer>> copia = new EnumMap<>(Especie.class);
        muertes.forEach((e, porCausa) -> {
            Map<CausaMuerte, Integer> m = new EnumMap<>(CausaMuerte.class);
            porCausa.forEach((c, n) -> { if (n.get() > 0) m.put(c, n.get()); });
            copia.put(e, m);
        });
        return copia;
    }

    public boolean reservarEspacio(Especie especie) {
        return capacidadCarga.get(especie).tryAcquire();
    }

    public void liberarEspacio(Especie especie) {
        capacidadCarga.get(especie).release();
    }

    // Un cadáver nuevo pasa la noche "fresco" y hasta el día siguiente lo pueden descomponer.
    // (Si se descompusiera el mismo día, casi nunca se verían cadáveres.)
    public void depositarMateriaMuerta(Agente cadaver) {
        cadaveresFrescos.add(cadaver);
    }

    // Entre días (sin agentes actuando): los cadáveres frescos pasan al búfer de materia muerta.
    private void madurarCadaveres() {
        synchronized (cadaveresFrescos) {
            for (Agente cadaver : cadaveresFrescos) {
                if (!buferMateriaMuerta.intentarProducir(cadaver)) {
                    bitacora.registrar(dia, "Materia muerta llena, se perdió " + cadaver);
                }
            }
            cadaveresFrescos.clear();
        }
    }

    // Todos los cadáveres que hay: los de hoy más los que esperan a los descomponedores.
    public int cadaveresTotales() {
        return cadaveresFrescos.size() + buferMateriaMuerta.tamano();
    }

    // ---- Población inicial ----

    // Agrega n individuos con edad y energía un poco distintas, para que no todos
    // tengan hambre, se reproduzcan o mueran de viejos el mismo día.
    public void poblarInicial(Especie especie, int n) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n && reservarEspacio(especie); i++) {
            int energiaBase = switch (especie) {
                case PLANTA -> Planta.ENERGIA_INICIAL;
                case HERBIVORO -> Herbivoro.ENERGIA_INICIAL;
                case DEPREDADOR -> Depredador.ENERGIA_INICIAL;
                case DESCOMPONEDOR -> Descomponedor.ENERGIA_INICIAL;
            };
            int edadMaxima = switch (especie) {
                case PLANTA -> Planta.EDAD_MAXIMA;
                case HERBIVORO -> Herbivoro.EDAD_MAXIMA;
                case DEPREDADOR -> Depredador.EDAD_MAXIMA;
                case DESCOMPONEDOR -> Descomponedor.EDAD_MAXIMA;
            };
            // Edad entre 0 y una quinta parte de su vida; energía apenas distinta (±10 %),
            // para que no les dé hambre a todos el mismo día sin dejar a nadie débil al inicio.
            int energia = Math.max(1, (int) Math.round(energiaBase * rnd.nextDouble(0.9, 1.1)));
            int edad = rnd.nextInt(edadMaxima / 5 + 1);
            agregarAgente(switch (especie) {
                case PLANTA -> new Planta(this, energia, edad);
                case HERBIVORO -> new Herbivoro(this, energia, edad);
                case DEPREDADOR -> new Depredador(this, energia, edad);
                case DESCOMPONEDOR -> new Descomponedor(this, energia, edad);
            });
        }
    }

    public static Especie especieDe(Agente agente) {
        return switch (agente) {
            case Planta p -> Especie.PLANTA;
            case Herbivoro h -> Especie.HERBIVORO;
            case Depredador d -> Especie.DEPREDADOR;
            case Descomponedor d -> Especie.DESCOMPONEDOR;
            default -> throw new IllegalArgumentException("Agente desconocido: " + agente);
        };
    }

    // ---- Tiempo ----
    // Cada día son dos fases del Phaser:
    //   fase par   = entre días: los agentes esperan el inicio; el Reloj puede leer/escribir sin carreras.
    //   fase impar = durante el día: los agentes hacen su ciclo; el Reloj espera a que todos terminen (TICK_DONE).

    // El agente espera a que empiece el día. false = la simulación terminó.
    public boolean esperarInicioDia() {
        return sincronizadorDia.arriveAndAwaitAdvance() >= 0;
    }

    // El agente avisa que terminó su día y espera a los demás. false = la simulación terminó.
    public boolean terminarDia() {
        return sincronizadorDia.arriveAndAwaitAdvance() >= 0;
    }

    public boolean estaEnMedioDelDia() {
        return sincronizadorDia.getPhase() % 2 == 1;
    }

    // Lo llama el Reloj: arranca el día y regresa cuando todos los agentes lo terminaron.
    public void avanzarDia() {
        madurarCadaveres();
        dia++;
        sincronizadorDia.arriveAndAwaitAdvance(); // inicio del día
        sincronizadorDia.arriveAndAwaitAdvance(); // fin del día
    }

    // Suelta a todos los agentes que esperan en la barrera.
    public void detener() {
        sincronizadorDia.forceTermination();
    }

    // ---- Getters ----
    public String getNombre() { return nombre; }
    public int getDia() { return dia; }
    public List<String> getVecinas() { return vecinas; }
    public List<Nido> getNidos(Especie especie) { return nidos.get(especie); }
    public PuertaMigracion getPuertaMigracion() { return puertaMigracion; }
    public TasasReproduccion getTasasReproduccion() { return tasasReproduccion; }
    public void setTasasReproduccion(TasasReproduccion tasas) { this.tasasReproduccion = tasas; }
    public void setPuertaMigracion(PuertaMigracion puerta) { this.puertaMigracion = puerta; }
    public BuferRecurso<Planta> getPlantasDisponibles() { return plantasDisponibles; }
    public BuferRecurso<Herbivoro> getBuferPresas() { return buferPresas; }
    public BuferRecurso<Agente> getBuferMateriaMuerta() { return buferMateriaMuerta; }
    public BuferRecurso<Integer> getReservaNutrientes() { return reservaNutrientes; }
    public ContadoresPoblacion getContadores() { return contadores; }
    public Bitacora getBitacora() { return bitacora; }

    // Cada animal vivo con lo que hizo en su último día y, si se reprodujo, en qué nido
    // (para el panel). Se llama entre días, cuando ningún agente está actuando.
    public List<VistaAnimal> fotoAnimales() {
        List<VistaAnimal> foto = new ArrayList<>();
        for (Agente agente : getAgentes()) {
            if (agente instanceof Animal animal && animal.estaVivo()) {
                foto.add(new VistaAnimal(animal.getId(), especieDe(animal),
                        animal.getActividad(), animal.getNidoReproduccion()));
            }
        }
        return foto;
    }

    public List<Agente> getAgentes() {
        synchronized (agentes) {
            return new ArrayList<>(agentes);
        }
    }
}

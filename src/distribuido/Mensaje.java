package distribuido;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import modelo.Actividad;
import modelo.CausaMuerte;
import modelo.Especie;
import modelo.VistaAnimal;

// Mensaje entre nodos. Viaja como una línea de JSON por TCP.
public class Mensaje {
    private final TipoMensaje tipo;
    private final String origen;
    private final String destino; // null = para el coordinador
    private final int dia;
    private final Map<String, Object> datos = new LinkedHashMap<>();

    public Mensaje(TipoMensaje tipo, String origen, String destino, int dia) {
        this.tipo = tipo;
        this.origen = origen;
        this.destino = destino;
        this.dia = dia;
    }

    // Para encadenar: new Mensaje(...).con("energia", 10).con("edad", 3)
    public Mensaje con(String clave, Object valor) {
        datos.put(clave, valor);
        return this;
    }

    public TipoMensaje getTipo() { return tipo; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public int getDia() { return dia; }

    public String getTexto(String clave) {
        Object v = datos.get(clave);
        return v == null ? null : v.toString();
    }

    public Object getMapa(String clave) {
        return datos.get(clave);
    }

    public int getEntero(String clave) {
        return ((Number) datos.get(clave)).intValue();
    }

    public Map<Especie, Integer> getPoblaciones() {
        Map<Especie, Integer> resultado = new EnumMap<>(Especie.class);
        Object v = datos.get("poblaciones");
        if (v instanceof Map<?, ?> mapa) {
            mapa.forEach((k, n) -> resultado.put(Especie.valueOf(k.toString()), ((Number) n).intValue()));
        }
        return resultado;
    }

    public Mensaje conPoblaciones(Map<Especie, Integer> poblaciones) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        poblaciones.forEach((e, n) -> mapa.put(e.name(), n));
        return con("poblaciones", mapa);
    }

    // Muertes acumuladas por especie y causa.
    public Mensaje conMuertes(Map<Especie, Map<CausaMuerte, Integer>> muertes) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        muertes.forEach((e, porCausa) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            porCausa.forEach((c, n) -> m.put(c.name(), n));
            mapa.put(e.name(), m);
        });
        return con("muertes", mapa);
    }

    public Map<Especie, Map<CausaMuerte, Integer>> getMuertes() {
        Map<Especie, Map<CausaMuerte, Integer>> resultado = new EnumMap<>(Especie.class);
        if (datos.get("muertes") instanceof Map<?, ?> mapa) {
            mapa.forEach((e, v) -> {
                Map<CausaMuerte, Integer> porCausa = new EnumMap<>(CausaMuerte.class);
                if (v instanceof Map<?, ?> m) {
                    m.forEach((c, n) -> porCausa.put(CausaMuerte.valueOf(c.toString()), ((Number) n).intValue()));
                }
                resultado.put(Especie.valueOf(e.toString()), porCausa);
            });
        }
        return resultado;
    }

    // Cada animal vivo (id, especie, actividad del día y nido donde se reprodujo), para el panel.
    public Mensaje conAnimales(List<VistaAnimal> animales) {
        List<Object> lista = new ArrayList<>();
        for (VistaAnimal v : animales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.id());
            m.put("especie", v.especie().name());
            m.put("actividad", v.actividad().name());
            m.put("nido", v.nido());
            lista.add(m);
        }
        return con("animales", lista);
    }

    public List<VistaAnimal> getAnimales() {
        List<VistaAnimal> resultado = new ArrayList<>();
        if (datos.get("animales") instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m) {
                    resultado.add(new VistaAnimal(((Number) m.get("id")).intValue(),
                            Especie.valueOf(m.get("especie").toString()),
                            Actividad.valueOf(m.get("actividad").toString()),
                            ((Number) m.get("nido")).intValue()));
                }
            }
        }
        return resultado;
    }

    // Crías nacidas en cada nido (una lista por especie).
    public Mensaje conNidos(List<Integer> herbivoros, List<Integer> depredadores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put(Especie.HERBIVORO.name(), herbivoros);
        mapa.put(Especie.DEPREDADOR.name(), depredadores);
        return con("nidos", mapa);
    }

    public Map<Especie, List<Integer>> getNidos() {
        Map<Especie, List<Integer>> resultado = new EnumMap<>(Especie.class);
        if (datos.get("nidos") instanceof Map<?, ?> mapa) {
            mapa.forEach((k, v) -> {
                List<Integer> lista = new ArrayList<>();
                if (v instanceof List<?> l) l.forEach(n -> lista.add(((Number) n).intValue()));
                resultado.put(Especie.valueOf(k.toString()), lista);
            });
        }
        return resultado;
    }

    public String aJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo.name());
        m.put("origen", origen);
        m.put("destino", destino);
        m.put("dia", dia);
        m.put("datos", datos);
        return Json.escribir(m);
    }

    @SuppressWarnings("unchecked")
    public static Mensaje desdeJson(String json) {
        Map<String, Object> m = (Map<String, Object>) Json.leer(json);
        Mensaje mensaje = new Mensaje(
                TipoMensaje.valueOf((String) m.get("tipo")),
                (String) m.get("origen"),
                (String) m.get("destino"),
                ((Number) m.get("dia")).intValue());
        Object datos = m.get("datos");
        if (datos instanceof Map<?, ?> d) {
            mensaje.datos.putAll((Map<String, Object>) d);
        }
        return mensaje;
    }

    @Override
    public String toString() {
        return aJson();
    }
}

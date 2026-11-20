package visual;

import java.util.Map;
import modelo.Especie;
import region.Region;
import region.recursos.Nido;

// Prueba de una sola región sin red: el main hace de Reloj.
// Argumentos opcionales: plantas dias pausaMs depredadores [--cerrar]
public class DemoRegion {
    public static void main(String[] args) throws Exception {
        int plantas = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int dias = args.length > 1 ? Integer.parseInt(args[1]) : 150;
        long pausaMs = args.length > 2 ? Long.parseLong(args[2]) : 200;
        int depredadores = args.length > 3 ? Integer.parseInt(args[3]) : 3;
        boolean cerrar = args.length > 4 && args[4].equals("--cerrar");

        Region region = new Region("Pradera", Map.of(
                Especie.PLANTA, 80, Especie.HERBIVORO, 60, Especie.DEPREDADOR, 15, Especie.DESCOMPONEDOR, 30));
        VentanaRegion ventana = new VentanaRegion(region.getNombre());
        ventana.mostrar();

        HistorialSimulacion historial = new HistorialSimulacion();
        poblar(region, Especie.PLANTA, plantas);
        poblar(region, Especie.HERBIVORO, 10);
        poblar(region, Especie.DEPREDADOR, depredadores);
        poblar(region, Especie.DESCOMPONEDOR, 5);

        for (int d = 1; d <= dias; d++) {
            region.meteorizarSuelo();
            region.germinarBancoDeSemillas();
            region.avanzarDia();
            ventana.actualizar(region.getContadores().foto(), d);
            ventana.actualizarRecursos(region.biomasaTotal(), region.biomasaMaxima(),
                    region.getReservaNutrientes().tamano(), region.getReservaNutrientes().getCapacidad(),
                    region.cadaveresTotales());
            ventana.actualizarNidos(Map.of(
                    Especie.HERBIVORO, region.getNidos(Especie.HERBIVORO).stream().map(Nido::getCrias).toList(),
                    Especie.DEPREDADOR, region.getNidos(Especie.DEPREDADOR).stream().map(Nido::getCrias).toList()));
            ventana.actualizarAnimales(region.fotoAnimales());
            historial.registrar(region.getNombre(), d, new HistorialSimulacion.Muestra(
                    region.getContadores().foto(), region.biomasaTotal(),
                    region.getReservaNutrientes().tamano(), region.cadaveresTotales(), 0,
                    region.getNidos(Especie.HERBIVORO).stream().mapToInt(Nido::getCrias).sum()
                            + region.getNidos(Especie.DEPREDADOR).stream().mapToInt(Nido::getCrias).sum(),
                    region.fotoMuertes()));
            System.out.printf("Día %3d  plantas=%2d  herbívoros=%2d  depred=%2d  descomp=%2d  biomasa=%3d  cadáveres=%3d  nutrientes=%3d%n", d,
                    region.getContadores().obtener(Especie.PLANTA),
                    region.getContadores().obtener(Especie.HERBIVORO),
                    region.getContadores().obtener(Especie.DEPREDADOR),
                    region.getContadores().obtener(Especie.DESCOMPONEDOR),
                    region.biomasaTotal(),
                    region.cadaveresTotales(),
                    region.getReservaNutrientes().tamano());
            Thread.sleep(pausaMs);
        }
        region.detener();

        var eventos = region.getBitacora().getEventos();
        System.out.println("Semillas: " + contar(eventos, "semilla")
                + "  plantas secas: " + contar(eventos, "se secó")
                + "  plantas comidas: " + contar(eventos, "comida por completo")
                + "  brotes del banco: " + contar(eventos, "banco de semillas")
                + "  crías: " + contar(eventos, "cría")
                + "  hambre: " + contar(eventos, "hambre")
                + "  vejez: " + contar(eventos, "viejo")
                + "  cazados: " + contar(eventos, "fue cazado")
                + "  descompuestos: " + contar(eventos, "descompuso"));
        if (cerrar) {
            ventana.dispose();
        } else {
            VentanaResultados.mostrar(historial);
        }
    }

    private static void poblar(Region region, Especie especie, int n) {
        region.poblarInicial(especie, n);
    }

    private static long contar(java.util.List<String> eventos, String palabra) {
        return eventos.stream().filter(e -> e.contains(palabra)).count();
    }
}

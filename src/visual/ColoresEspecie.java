package visual;

import java.awt.Color;
import modelo.Especie;

public final class ColoresEspecie {
    public static final Color HERBIVORO = new Color(30, 160, 40);      // verde
    public static final Color DEPREDADOR = new Color(210, 40, 40);     // rojo
    public static final Color DESCOMPONEDOR = new Color(130, 50, 170); // morado
    public static final Color PLANTA = new Color(150, 235, 150);       // verde clarito
    public static final Color BIOMASA = new Color(110, 170, 60);       // barra de biomasa
    public static final Color NUTRIENTES = new Color(150, 100, 50);    // barra de nutrientes (café)
    public static final Color CADAVER = new Color(120, 115, 110);      // gris

    // Para las gráficas: los mismos tonos, ajustados para que se distingan entre sí
    // (también con daltonismo) y sobre fondo claro. Validados con el validador de paletas.
    public static final Color GRAFICA_PLANTA = new Color(0x7FB800);
    public static final Color GRAFICA_HERBIVORO = new Color(0x156A2E);
    public static final Color GRAFICA_DEPREDADOR = new Color(0xC43C5A);
    public static final Color GRAFICA_DESCOMPONEDOR = new Color(0x7B3FB0);

    private ColoresEspecie() {}

    public static Color grafica(Especie especie) {
        return switch (especie) {
            case PLANTA -> GRAFICA_PLANTA;
            case HERBIVORO -> GRAFICA_HERBIVORO;
            case DEPREDADOR -> GRAFICA_DEPREDADOR;
            case DESCOMPONEDOR -> GRAFICA_DESCOMPONEDOR;
        };
    }

    public static Color de(Especie especie) {
        return switch (especie) {
            case HERBIVORO -> HERBIVORO;
            case DEPREDADOR -> DEPREDADOR;
            case DESCOMPONEDOR -> DESCOMPONEDOR;
            case PLANTA -> PLANTA;
        };
    }
}

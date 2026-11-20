package visual;

import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import modelo.Especie;
import modelo.VistaAnimal;

// Una ventana por región con su ecosistema.
public class VentanaRegion extends JFrame {
    private final PanelEcosistema panel;
    private final String nombreRegion;

    public VentanaRegion(String nombreRegion) {
        super("Región: " + nombreRegion);
        this.nombreRegion = nombreRegion;
        panel = new PanelEcosistema(nombreRegion.hashCode());
        add(panel);
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    // Se puede llamar desde cualquier hilo.
    public void actualizar(Map<Especie, Integer> poblaciones) {
        SwingUtilities.invokeLater(() -> panel.setPoblaciones(poblaciones));
    }

    public void actualizar(Map<Especie, Integer> poblaciones, int dia) {
        SwingUtilities.invokeLater(() -> {
            panel.setPoblaciones(poblaciones);
            setTitle("Región: " + nombreRegion + " — Día " + dia);
        });
    }

    public void actualizarBiomasa(int biomasa, int biomasaMax) {
        SwingUtilities.invokeLater(() -> panel.setBiomasa(biomasa, biomasaMax));
    }

    public void actualizarRecursos(int biomasa, int biomasaMax, int nutrientes, int nutrientesMax, int cadaveres) {
        SwingUtilities.invokeLater(() -> {
            panel.setBiomasa(biomasa, biomasaMax);
            panel.setNutrientes(nutrientes, nutrientesMax);
            panel.setCadaveres(cadaveres);
        });
    }

    public void actualizarNidos(Map<Especie, List<Integer>> criasPorNido) {
        SwingUtilities.invokeLater(() -> panel.setNidos(criasPorNido));
    }

    public void actualizarAnimales(List<VistaAnimal> animales) {
        SwingUtilities.invokeLater(() -> panel.setAnimales(animales));
    }

    public void marcarCaida() {
        SwingUtilities.invokeLater(() -> setTitle("Región: " + nombreRegion + " — CAÍDA"));
    }

    public void mostrar() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}

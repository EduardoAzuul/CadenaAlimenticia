package visual;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import distribuido.LanzadorLocal;
import distribuido.LanzadorLocal.DefinicionRegion;
import distribuido.nodos.ConfigRegion;
import modelo.Especie;
import modelo.TasasReproduccion;
import region.Region;

// Ventana de inicio: se configuran el sistema y las regiones, y se arranca la simulación.
public class PanelInicio extends JFrame {
    private static final String[] COLUMNAS = {
        "Nombre", "Vecinas (vacío = todas)", "Plantas", "Herbívoros", "Depredadores", "Descomp.",
        "Cap. plantas", "Cap. herbívoros", "Cap. depred.", "Cap. descomp.",
        "Nidos herb.", "Nidos depred.", "Nutrientes iniciales"
    };
    private static final int COL_NOMBRE = 0;
    private static final int COL_VECINAS = 1;
    private static final int PRIMERA_COL_NUMERICA = 2;

    private final JSpinner puerto = new JSpinner(new SpinnerNumberModel(5000, 1024, 65535, 1));
    private final JSpinner dias = new JSpinner(new SpinnerNumberModel(150, 1, 100_000, 10));
    private final JSpinner pausa = new JSpinner(new SpinnerNumberModel(300, 0, 10_000, 50));
    private final JCheckBox abrirPanel = new JCheckBox("Abrir ventanas de visualización", true);
    // Tasas de reproducción (generales para todo el sistema), en el orden de Especie.values().
    private final Map<Especie, JSpinner> tasas = new EnumMap<>(Especie.class);
    private final DefaultTableModel modelo;
    private final JTable tabla;

    public PanelInicio() {
        super("Simulador de cadena alimenticia");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        modelo = new DefaultTableModel(COLUMNAS, 0) {
            @Override
            public Class<?> getColumnClass(int columna) {
                return columna >= PRIMERA_COL_NUMERICA ? Integer.class : String.class;
            }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(24);
        tabla.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        tabla.getColumnModel().getColumn(COL_VECINAS).setPreferredWidth(170);
        tabla.getColumnModel().getColumn(COLUMNAS.length - 1).setPreferredWidth(140);
        restaurarRegiones();

        JPanel contenido = new JPanel(new BorderLayout(0, 12));
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contenido.add(crearTitulo(), BorderLayout.NORTH);
        contenido.add(crearCentro(), BorderLayout.CENTER);
        contenido.add(crearBotones(), BorderLayout.SOUTH);
        setContentPane(contenido);

        pack();
        setMinimumSize(new Dimension(1380, 480));
        setLocationRelativeTo(null);
    }

    private JPanel crearTitulo() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel titulo = new JLabel("Simulador distribuido de cadena alimenticia");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitulo = new JLabel("Configura el sistema y las regiones. Cada región corre como un nodo que se comunica por TCP.");
        subtitulo.setForeground(Color.GRAY);
        panel.add(titulo, BorderLayout.NORTH);
        panel.add(subtitulo, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearCentro() {
        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.add(crearSistema(), BorderLayout.NORTH);
        centro.add(crearRegiones(), BorderLayout.CENTER);
        return centro;
    }

    private JPanel crearSistema() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Sistema"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;

        agregarCampo(panel, c, 0, "Días a simular:", dias, "Cuántos ciclos manda el Reloj");
        agregarCampo(panel, c, 1, "Pausa entre días (ms):", pausa, "Solo para poder verlo; 0 = lo más rápido posible");
        agregarCampo(panel, c, 2, "Puerto del coordinador:", puerto, "Puerto TCP donde escucha el coordinador");

        c.gridy = 3;
        c.gridwidth = 1;
        c.gridx = 0;
        panel.add(new JLabel("Reproducción (%):"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(crearTasas(), c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        panel.add(abrirPanel, c);

        // Columna vacía que se queda con el espacio sobrante: los campos quedan a la izquierda.
        GridBagConstraints relleno = new GridBagConstraints();
        relleno.gridx = 3;
        relleno.gridy = 0;
        relleno.weightx = 1;
        panel.add(new JLabel(), relleno);
        return panel;
    }

    // Una casilla por especie + explicación. Probabilidad diaria de intentar reproducirse cuando ya puede.
    private JPanel crearTasas() {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        TasasReproduccion d = TasasReproduccion.porDefecto();
        for (Especie e : Especie.values()) {
            JSpinner campo = new JSpinner(new SpinnerNumberModel(d.de(e), 0, 100, 5));
            ((JSpinner.DefaultEditor) campo.getEditor()).getTextField().setColumns(3);
            tasas.put(e, campo);
            fila.add(new JLabel(VentanaResultados.nombre(e)));
            fila.add(campo);
            fila.add(Box.createHorizontalStrut(8));
        }
        JLabel nota = new JLabel("Probabilidad por día de intentar reproducirse cuando ya puede (energía, lugar, pareja)");
        nota.setForeground(Color.GRAY);
        fila.add(nota);
        return fila;
    }

    private TasasReproduccion leerTasas() {
        return new TasasReproduccion(
                (Integer) tasas.get(Especie.PLANTA).getValue(),
                (Integer) tasas.get(Especie.HERBIVORO).getValue(),
                (Integer) tasas.get(Especie.DEPREDADOR).getValue(),
                (Integer) tasas.get(Especie.DESCOMPONEDOR).getValue());
    }

    private static void agregarCampo(JPanel panel, GridBagConstraints c, int fila, String etiqueta,
                                     JSpinner campo, String ayuda) {
        c.gridy = fila;
        c.gridwidth = 1;
        c.gridx = 0;
        panel.add(new JLabel(etiqueta), c);
        c.gridx = 1;
        campo.setEditor(new JSpinner.NumberEditor(campo, "#")); // sin separador de miles
        ((JSpinner.DefaultEditor) campo.getEditor()).getTextField().setColumns(7);
        panel.add(campo, c);
        c.gridx = 2;
        JLabel nota = new JLabel(ayuda);
        nota.setForeground(Color.GRAY);
        panel.add(nota, c);
    }

    private JPanel crearRegiones() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder(
                "Regiones (cantidades iniciales, capacidades de carga y nidos para reproducirse en pareja)"));
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(1340, 140));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton agregar = new JButton("Agregar región");
        agregar.addActionListener(e -> agregarRegion());
        JButton quitar = new JButton("Quitar región");
        quitar.addActionListener(e -> quitarRegion());
        JButton restaurar = new JButton("Restaurar valores");
        restaurar.addActionListener(e -> restaurarRegiones());
        acciones.add(agregar);
        acciones.add(quitar);
        acciones.add(restaurar);
        panel.add(acciones, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearBotones() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton iniciar = new JButton("Iniciar simulación");
        iniciar.setFont(iniciar.getFont().deriveFont(Font.BOLD));
        iniciar.addActionListener(e -> iniciar());
        getRootPane().setDefaultButton(iniciar);
        panel.add(iniciar);
        return panel;
    }

    // ---------------- Regiones ----------------

    private void restaurarRegiones() {
        detenerEdicion();
        modelo.setRowCount(0);
        ConfigRegion d = ConfigRegion.porDefecto();
        agregarFila("Pradera", "Bosque", d);
        agregarFila("Bosque", "Pradera", ConfigRegion.pobre());
    }

    private void agregarRegion() {
        detenerEdicion();
        Set<String> usados = new HashSet<>(nombres());
        int n = modelo.getRowCount() + 1;
        while (usados.contains("Region" + n)) n++;
        agregarFila("Region" + n, "", ConfigRegion.porDefecto());
    }

    private void quitarRegion() {
        detenerEdicion();
        int fila = tabla.getSelectedRow();
        if (fila < 0) fila = modelo.getRowCount() - 1;
        if (fila >= 0) modelo.removeRow(fila);
    }

    private void agregarFila(String nombre, String vecinas, ConfigRegion c) {
        modelo.addRow(new Object[] {
            nombre, vecinas, c.plantas(), c.herbivoros(), c.depredadores(), c.descomponedores(),
            c.capacidadPlantas(), c.capacidadHerbivoros(), c.capacidadDepredadores(), c.capacidadDescomponedores(),
            c.nidosHerbivoros(), c.nidosDepredadores(), c.nutrientesIniciales()
        });
    }

    private List<String> nombres() {
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < modelo.getRowCount(); i++) {
            Object v = modelo.getValueAt(i, COL_NOMBRE);
            nombres.add(v == null ? "" : v.toString().trim());
        }
        return nombres;
    }

    private void detenerEdicion() {
        if (tabla.isEditing()) tabla.getCellEditor().stopCellEditing();
    }

    // ---------------- Iniciar ----------------

    private void iniciar() {
        detenerEdicion();
        List<DefinicionRegion> regiones;
        try {
            regiones = leerRegiones();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Revisa la configuración", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int p = (Integer) puerto.getValue();
        if (!puertoLibre(p)) {
            JOptionPane.showMessageDialog(this, "El puerto " + p + " ya está en uso. Elige otro.",
                    "Puerto ocupado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int d = (Integer) dias.getValue();
        long ms = ((Integer) pausa.getValue()).longValue();
        boolean conPanel = abrirPanel.isSelected();
        TasasReproduccion tasasElegidas = leerTasas();

        dispose();
        Thread arranque = new Thread(() -> {
            try {
                LanzadorLocal.lanzar(p, d, ms, conPanel, tasasElegidas, regiones);
            } catch (InterruptedException ignored) {
            }
        }, "arranque");
        arranque.start();
    }

    // Valida la tabla y la convierte en definiciones de región.
    private List<DefinicionRegion> leerRegiones() {
        int filas = modelo.getRowCount();
        if (filas == 0) {
            throw new IllegalArgumentException("Agrega al menos una región.");
        }
        List<String> nombres = nombres();
        Set<String> unicos = new HashSet<>();
        for (String n : nombres) {
            if (n.isEmpty()) throw new IllegalArgumentException("Todas las regiones necesitan nombre.");
            if (n.contains(",")) throw new IllegalArgumentException("El nombre \"" + n + "\" no puede tener comas.");
            if (!unicos.add(n)) throw new IllegalArgumentException("Hay dos regiones llamadas \"" + n + "\".");
        }

        List<DefinicionRegion> regiones = new ArrayList<>();
        for (int i = 0; i < filas; i++) {
            String nombre = nombres.get(i);
            int[] v = new int[COLUMNAS.length - PRIMERA_COL_NUMERICA];
            for (int c = PRIMERA_COL_NUMERICA; c < COLUMNAS.length; c++) {
                Object valor = modelo.getValueAt(i, c);
                if (!(valor instanceof Integer n) || n < 0) {
                    throw new IllegalArgumentException("\"" + COLUMNAS[c] + "\" de " + nombre + " debe ser un entero ≥ 0.");
                }
                v[c - PRIMERA_COL_NUMERICA] = n;
            }
            ConfigRegion config = new ConfigRegion(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10]);
            revisarCapacidad(nombre, "plantas", config.plantas(), config.capacidadPlantas());
            revisarCapacidad(nombre, "herbívoros", config.herbivoros(), config.capacidadHerbivoros());
            revisarCapacidad(nombre, "depredadores", config.depredadores(), config.capacidadDepredadores());
            revisarCapacidad(nombre, "descomponedores", config.descomponedores(), config.capacidadDescomponedores());
            if (config.nutrientesIniciales() > Region.CAPACIDAD_NUTRIENTES) {
                throw new IllegalArgumentException("Los nutrientes iniciales de " + nombre
                        + " no pueden pasar de " + Region.CAPACIDAD_NUTRIENTES + ".");
            }
            regiones.add(new DefinicionRegion(nombre, leerVecinas(i, nombre, nombres), config));
        }
        return regiones;
    }

    private List<String> leerVecinas(int fila, String nombre, List<String> nombres) {
        Object celda = modelo.getValueAt(fila, COL_VECINAS);
        String texto = celda == null ? "" : celda.toString().trim();
        if (texto.isEmpty()) {
            List<String> todas = new ArrayList<>(nombres);
            todas.remove(nombre);
            return todas;
        }
        List<String> vecinas = Arrays.stream(texto.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (String v : vecinas) {
            if (v.equals(nombre)) {
                throw new IllegalArgumentException(nombre + " no puede ser vecina de sí misma.");
            }
            if (!nombres.contains(v)) {
                throw new IllegalArgumentException("La vecina \"" + v + "\" de " + nombre + " no existe.");
            }
        }
        return vecinas;
    }

    private static void revisarCapacidad(String region, String especie, int iniciales, int capacidad) {
        if (iniciales > capacidad) {
            throw new IllegalArgumentException("En " + region + " hay más " + especie + " iniciales ("
                    + iniciales + ") que su capacidad de carga (" + capacidad + ").");
        }
    }

    private static boolean puertoLibre(int puerto) {
        try (ServerSocket s = new ServerSocket(puerto)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new PanelInicio().setVisible(true));
    }
}

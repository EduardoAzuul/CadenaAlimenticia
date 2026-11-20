package visual;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import modelo.CausaMuerte;
import modelo.Especie;

// Ventana que aparece al terminar la simulación: resumen, gráficas y tabla por día.
public class VentanaResultados extends JFrame {
    private static final Especie[] ORDEN = {
        Especie.PLANTA, Especie.HERBIVORO, Especie.DEPREDADOR, Especie.DESCOMPONEDOR
    };

    public VentanaResultados(HistorialSimulacion historial) {
        super("Resultados de la simulación");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTabbedPane pestañas = new JTabbedPane();
        JScrollPane graficas = new JScrollPane(crearGraficas(historial));
        graficas.getVerticalScrollBar().setUnitIncrement(24);
        graficas.setBorder(null);
        pestañas.addTab("Gráficas", graficas);
        pestañas.addTab("Tabla por día", new JScrollPane(crearTabla(historial)));
        pestañas.addTab("Muertes por causa", new JScrollPane(crearTablaMuertes(historial)));
        setContentPane(pestañas);

        setSize(1000, 860);
        setLocationRelativeTo(null);
    }

    public static void mostrar(HistorialSimulacion historial) {
        if (historial.estaVacio()) return;
        SwingUtilities.invokeLater(() -> new VentanaResultados(historial).setVisible(true));
    }

    // ---------------- Gráficas ----------------

    // Contenido completo de la pestaña de gráficas (también sirve para exportarlo a imagen).
    public static JPanel crearGraficas(HistorialSimulacion h) {
        JPanel contenido = new JPanel();
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBackground(GraficaLineas.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        contenido.add(alinear(titulo("Así terminó la simulación",
                "Días " + h.primerDia() + " a " + h.ultimoDia() + " · " + h.regiones().size() + " regiones")));
        contenido.add(Box.createVerticalStrut(12));
        contenido.add(alinear(crearResumen(h)));
        contenido.add(Box.createVerticalStrut(20));

        // 1) Población total por especie
        List<GraficaLineas.Serie> totales = new ArrayList<>();
        for (Especie e : ORDEN) {
            totales.add(new GraficaLineas.Serie(nombre(e), ColoresEspecie.grafica(e),
                    h.serieTotal(x -> x.poblacion(e))));
        }
        contenido.add(alinear(new GraficaLineas("Población total por especie",
                "Suma de todas las regiones, al final de cada día", h.primerDia(), totales,
                Double.NaN, 940, 340)));
        contenido.add(Box.createVerticalStrut(20));

        // 2) Muertes por causa: una gráfica por especie (cada una con su propia escala)
        Map<Especie, Map<CausaMuerte, Integer>> muertes = h.muertesTotales();
        contenido.add(alinear(titulo("Muertes por causa",
                "En toda la simulación. Cada especie con su propia escala")));
        contenido.add(Box.createVerticalStrut(6));
        JPanel rejillaMuertes = new JPanel(new GridLayout(0, 2, 12, 12));
        rejillaMuertes.setBackground(GraficaLineas.FONDO);
        for (Especie e : ORDEN) {
            rejillaMuertes.add(new GraficaMuertes(nombre(e), ColoresEspecie.grafica(e),
                    muertes.getOrDefault(e, Map.of()), 464));
        }
        contenido.add(alinear(rejillaMuertes));
        contenido.add(Box.createVerticalStrut(20));

        // 3) Una gráfica por región, con la misma escala para poder compararlas
        List<String> regiones = h.regiones();
        double maximoRegional = 0;
        for (String r : regiones) {
            for (Especie e : ORDEN) {
                for (double v : h.serie(r, x -> x.poblacion(e))) {
                    if (!Double.isNaN(v)) maximoRegional = Math.max(maximoRegional, v);
                }
            }
        }
        contenido.add(alinear(titulo("Población por región", "Misma escala en todas para poder compararlas")));
        contenido.add(Box.createVerticalStrut(6));
        JPanel rejilla = new JPanel(new GridLayout(0, 2, 12, 12));
        rejilla.setBackground(GraficaLineas.FONDO);
        for (String r : regiones) {
            List<GraficaLineas.Serie> series = new ArrayList<>();
            for (Especie e : ORDEN) {
                series.add(new GraficaLineas.Serie(nombre(e), ColoresEspecie.grafica(e),
                        h.serie(r, x -> x.poblacion(e))));
            }
            int ultimo = h.ultimoDia(r);
            String sub = ultimo < h.ultimoDia() ? "Dejó de responder después del día " + ultimo : null;
            rejilla.add(new GraficaLineas(r, sub, h.primerDia(), series, maximoRegional, 464, 270));
        }
        contenido.add(alinear(rejilla));
        contenido.add(Box.createVerticalStrut(20));

        // 4) Recursos: una gráfica por recurso (escalas distintas, nunca un doble eje)
        contenido.add(alinear(titulo("Recursos", "Totales de todas las regiones")));
        contenido.add(Box.createVerticalStrut(6));
        JPanel recursos = new JPanel(new GridLayout(1, 3, 12, 0));
        recursos.setBackground(GraficaLineas.FONDO);
        recursos.add(new GraficaLineas("Biomasa", "Suma del tamaño de las plantas", h.primerDia(),
                List.of(new GraficaLineas.Serie("Biomasa", ColoresEspecie.BIOMASA,
                        h.serieTotal(HistorialSimulacion.Muestra::biomasa))), Double.NaN, 306, 220));
        recursos.add(new GraficaLineas("Nutrientes", "En el suelo", h.primerDia(),
                List.of(new GraficaLineas.Serie("Nutrientes", ColoresEspecie.NUTRIENTES,
                        h.serieTotal(HistorialSimulacion.Muestra::nutrientes))), Double.NaN, 306, 220));
        recursos.add(new GraficaLineas("Cadáveres", "Esperando descomponerse", h.primerDia(),
                List.of(new GraficaLineas.Serie("Cadáveres", ColoresEspecie.CADAVER,
                        h.serieTotal(HistorialSimulacion.Muestra::cadaveres))), Double.NaN, 306, 220));
        contenido.add(alinear(recursos));
        return contenido;
    }

    // Tarjetas con los números clave.
    private static JPanel crearResumen(HistorialSimulacion h) {
        JPanel tarjetas = new JPanel(new GridLayout(0, 4, 10, 10));
        tarjetas.setBackground(GraficaLineas.FONDO);
        for (Especie e : ORDEN) {
            double[] total = h.serieTotal(x -> x.poblacion(e));
            double fin = ultimoValor(total);
            double max = 0, min = Double.MAX_VALUE;
            for (double v : total) {
                if (Double.isNaN(v)) continue;
                max = Math.max(max, v);
                min = Math.min(min, v);
            }
            tarjetas.add(tarjeta(nombre(e) + " al final", GraficaLineas.formatear(fin),
                    "mín " + GraficaLineas.formatear(min) + " · máx " + GraficaLineas.formatear(max),
                    ColoresEspecie.grafica(e)));
        }
        tarjetas.add(tarjeta("Días simulados", String.valueOf(h.ultimoDia() - h.primerDia() + 1), null, null));
        tarjetas.add(tarjeta("Crías nacidas en nidos", GraficaLineas.formatear(h.totalCriasEnNidos()),
                "herbívoros y depredadores", null));
        tarjetas.add(tarjeta("Migraciones", GraficaLineas.formatear(h.totalMigrantes()),
                "animales que llegaron a otra región", null));
        Map<Especie, Integer> extinciones = h.extinciones();
        tarjetas.add(tarjeta("Extinciones", extinciones.isEmpty() ? "Ninguna" : String.valueOf(extinciones.size()),
                extinciones.isEmpty() ? "todas las especies sobrevivieron"
                        : extinciones.entrySet().stream()
                            .map(x -> nombre(x.getKey()) + " (día " + x.getValue() + ")")
                            .collect(Collectors.joining(", ")),
                null));
        return tarjetas;
    }

    private static JPanel tarjeta(String etiqueta, String valor, String detalle, Color marca) {
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setBackground(Color.WHITE);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GraficaLineas.REJILLA),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fila.setOpaque(false);
        if (marca != null) {
            fila.add(new JLabel(new javax.swing.Icon() {
                public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                    g.setColor(marca);
                    g.fillRoundRect(x, y + 1, 10, 10, 4, 4);
                }
                public int getIconWidth() { return 10; }
                public int getIconHeight() { return 12; }
            }));
        }
        JLabel e = new JLabel(etiqueta);
        e.setForeground(GraficaLineas.TEXTO_SECUNDARIO);
        fila.add(e);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((FlowLayout) fila.getLayout()).setHgap(marca != null ? 6 : 0);
        t.add(fila);

        JLabel v = new JLabel(valor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 22f));
        v.setForeground(GraficaLineas.TEXTO);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.add(v);
        if (detalle != null) {
            JLabel d = new JLabel(detalle);
            d.setForeground(GraficaLineas.TEXTO_SECUNDARIO);
            d.setFont(d.getFont().deriveFont(11f));
            d.setAlignmentX(Component.LEFT_ALIGNMENT);
            t.add(d);
        }
        return t;
    }

    private static JPanel titulo(String texto, String subtitulo) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel t = new JLabel(texto);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 17f));
        t.setForeground(GraficaLineas.TEXTO);
        p.add(t, BorderLayout.NORTH);
        if (subtitulo != null) {
            JLabel s = new JLabel(subtitulo);
            s.setForeground(GraficaLineas.TEXTO_SECUNDARIO);
            p.add(s, BorderLayout.SOUTH);
        }
        return p;
    }

    private static JComponent alinear(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(c.getPreferredSize().width, c.getPreferredSize().height));
        return c;
    }

    // ---------------- Tabla ----------------

    private static JTable crearTabla(HistorialSimulacion h) {
        String[] columnas = { "Día", "Plantas", "Herbívoros", "Depredadores", "Descomponedores",
                "Biomasa", "Nutrientes", "Cadáveres", "Migrantes que llegaron" };
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int f, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return Integer.class; }
        };
        List<double[]> datos = new ArrayList<>();
        for (Especie e : ORDEN) datos.add(h.serieTotal(x -> x.poblacion(e)));
        datos.add(h.serieTotal(HistorialSimulacion.Muestra::biomasa));
        datos.add(h.serieTotal(HistorialSimulacion.Muestra::nutrientes));
        datos.add(h.serieTotal(HistorialSimulacion.Muestra::cadaveres));
        datos.add(h.serieTotal(HistorialSimulacion.Muestra::llegaron));
        int n = datos.get(0).length;
        for (int i = 0; i < n; i++) {
            Object[] fila = new Object[columnas.length];
            fila[0] = h.primerDia() + i;
            for (int c = 0; c < datos.size(); c++) {
                double v = datos.get(c)[i];
                fila[c + 1] = Double.isNaN(v) ? null : (int) Math.round(v);
            }
            modelo.addRow(fila);
        }
        JTable tabla = new JTable(modelo);
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(22);
        return tabla;
    }

    private static JTable crearTablaMuertes(HistorialSimulacion h) {
        DefaultTableModel modelo = new DefaultTableModel(new String[] { "Especie", "Causa", "Muertes" }, 0) {
            @Override public boolean isCellEditable(int f, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) { return c == 2 ? Integer.class : String.class; }
        };
        h.muertesTotales().forEach((e, porCausa) -> porCausa.forEach((c, n) -> {
            if (n > 0) modelo.addRow(new Object[] { nombre(e), c.nombre(), n });
        }));
        JTable tabla = new JTable(modelo);
        tabla.setAutoCreateRowSorter(true);
        tabla.setRowHeight(22);
        return tabla;
    }

    private static double ultimoValor(double[] v) {
        for (int i = v.length - 1; i >= 0; i--) if (!Double.isNaN(v[i])) return v[i];
        return 0;
    }

    static String nombre(Especie e) {
        return switch (e) {
            case PLANTA -> "Plantas";
            case HERBIVORO -> "Herbívoros";
            case DEPREDADOR -> "Depredadores";
            case DESCOMPONEDOR -> "Descomponedores";
        };
    }
}

package visual;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;

// Gráfica de líneas por día, dibujada a mano con Java2D.
// - Leyenda arriba cuando hay 2 o más series (si es una sola, el título la nombra).
// - Etiqueta al final de cada línea (si dos chocan, se omite una: la leyenda y el tooltip la cubren).
// - Al pasar el mouse: línea vertical en el día y un recuadro con los valores de ese día.
public class GraficaLineas extends JPanel {
    public record Serie(String nombre, Color color, double[] valores) {}

    // Colores de texto y ejes (nunca el color de la serie para el texto).
    static final Color FONDO = new Color(0xFCFCFB);
    static final Color TEXTO = new Color(0x1F1F1C);
    static final Color TEXTO_SECUNDARIO = new Color(0x6B6B66);
    static final Color REJILLA = new Color(0xE6E6E2);

    private static final int MARGEN_IZQ = 46;
    private static final int MARGEN_ABAJO = 30;

    private int margenDer = 60;      // espacio para las etiquetas al final; se mide al dibujar
    private final String titulo;
    private final String subtitulo;
    private final int primerDia;
    private final List<Serie> series;
    private final double maximoFijo; // NaN = automático
    private int hover = -1;          // índice del día bajo el mouse

    public GraficaLineas(String titulo, String subtitulo, int primerDia, List<Serie> series,
                         double maximoFijo, int ancho, int alto) {
        this.titulo = titulo;
        this.subtitulo = subtitulo;
        this.primerDia = primerDia;
        this.series = series;
        this.maximoFijo = maximoFijo;
        setPreferredSize(new Dimension(ancho, alto));
        setBackground(FONDO);

        MouseAdapter raton = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(indiceEn(e.getX()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        addMouseMotionListener(raton);
        addMouseListener(raton);
    }

    public void setHover(int indice) {
        if (indice != hover) {
            hover = indice;
            repaint();
        }
    }

    private int numeroDias() {
        return series.isEmpty() ? 0 : series.get(0).valores().length;
    }

    private int altoEncabezado(Graphics2D g2) {
        int alto = 22;                          // título
        if (subtitulo != null) alto += 16;
        if (series.size() >= 2) alto += 20;     // leyenda
        return alto + 10;
    }

    // ---------------- Escalas ----------------

    private double maximo() {
        if (!Double.isNaN(maximoFijo)) return maximoFijo;
        double max = 0;
        for (Serie s : series) for (double v : s.valores()) if (!Double.isNaN(v)) max = Math.max(max, v);
        return max;
    }

    // Paso "bonito" (1, 2, 5 × 10^n) para unas 4 divisiones.
    static double pasoBonito(double max) {
        if (max <= 0) return 1;
        double bruto = max / 4;
        double potencia = Math.pow(10, Math.floor(Math.log10(bruto)));
        double f = bruto / potencia;
        double paso = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return Math.max(1, paso * potencia);
    }

    private int xDe(int i, int x0, int ancho) {
        int n = numeroDias();
        return n <= 1 ? x0 : x0 + (int) Math.round((double) i * ancho / (n - 1));
    }

    private int indiceEn(int xPantalla) {
        int n = numeroDias();
        int ancho = getWidth() - MARGEN_IZQ - margenDer;
        if (n == 0 || ancho <= 0 || xPantalla < MARGEN_IZQ - 6 || xPantalla > MARGEN_IZQ + ancho + 6) return -1;
        int i = (int) Math.round((double) (xPantalla - MARGEN_IZQ) * (n - 1) / ancho);
        return Math.max(0, Math.min(n - 1, i));
    }

    // ---------------- Dibujo ----------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font base = getFont().deriveFont(12f);
        g2.setFont(base);
        margenDer = medirMargenDerecho(g2.getFontMetrics());

        int arriba = altoEncabezado(g2);
        int x0 = MARGEN_IZQ;
        int ancho = getWidth() - MARGEN_IZQ - margenDer;
        int y0 = getHeight() - MARGEN_ABAJO;   // línea base
        int alto = y0 - arriba;
        dibujarEncabezado(g2, base);
        if (numeroDias() == 0 || ancho <= 0 || alto <= 0) {
            g2.dispose();
            return;
        }

        double paso = pasoBonito(maximo());
        double tope = Math.max(paso, Math.ceil(maximo() / paso) * paso);

        // Rejilla y eje Y (recesivos, 1px sólido)
        FontMetrics fm = g2.getFontMetrics();
        for (double v = 0; v <= tope + 1e-9; v += paso) {
            int y = y0 - (int) Math.round(v / tope * alto);
            g2.setColor(REJILLA);
            g2.drawLine(x0, y, x0 + ancho, y);
            String t = formatear(v);
            g2.setColor(TEXTO_SECUNDARIO);
            g2.drawString(t, x0 - 8 - fm.stringWidth(t), y + fm.getAscent() / 2 - 1);
        }
        // Eje X: días
        int n = numeroDias();
        int ultimoDia = primerDia + n - 1;
        int pasoDias = (int) pasoBonito(n);
        List<Integer> marcas = new ArrayList<>();
        marcas.add(primerDia);
        for (int d = (primerDia / pasoDias + 1) * pasoDias; d < ultimoDia; d += pasoDias) {
            // sin marcas pegadas al primer o al último día
            if (d - primerDia >= pasoDias / 2 && ultimoDia - d >= pasoDias / 2) marcas.add(d);
        }
        if (ultimoDia != primerDia) marcas.add(ultimoDia);
        g2.setColor(TEXTO_SECUNDARIO);
        for (int d : marcas) {
            int x = xDe(d - primerDia, x0, ancho);
            String t = d == primerDia ? "día " + d : String.valueOf(d);
            int tx = d == primerDia ? x : x - fm.stringWidth(t) / 2; // el primero, alineado a la izquierda
            g2.drawString(t, tx, y0 + 16);
        }

        // Líneas (2px, unión redonda); se cortan donde no hay datos (NaN).
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Serie s : series) {
            Path2D.Double camino = new Path2D.Double();
            boolean abierto = false;
            for (int i = 0; i < n; i++) {
                double v = s.valores()[i];
                if (Double.isNaN(v)) {
                    abierto = false;
                    continue;
                }
                double x = xDe(i, x0, ancho);
                double y = y0 - v / tope * alto;
                if (abierto) camino.lineTo(x, y);
                else camino.moveTo(x, y);
                abierto = true;
            }
            g2.setColor(s.color());
            g2.draw(camino);
        }
        g2.setStroke(new BasicStroke());

        dibujarEtiquetasFinales(g2, x0, ancho, y0, alto, tope);
        if (hover >= 0 && hover < n) {
            dibujarHover(g2, x0, ancho, y0, alto, tope, arriba);
        }
        g2.dispose();
    }

    // Lo que mide la etiqueta más larga al final de una línea (con una serie, solo el número).
    private int medirMargenDerecho(FontMetrics fm) {
        String numero = formatear(maximo());
        int ancho = fm.stringWidth(numero);
        if (series.size() >= 2) {
            for (Serie s : series) ancho = Math.max(ancho, fm.stringWidth(s.nombre() + "  " + numero));
        }
        return ancho + 22;
    }

    private void dibujarEncabezado(Graphics2D g2, Font base) {
        int y = 18;
        g2.setFont(base.deriveFont(Font.BOLD, 14f));
        g2.setColor(TEXTO);
        g2.drawString(titulo, MARGEN_IZQ - 30, y);
        g2.setFont(base);
        if (subtitulo != null) {
            y += 16;
            g2.setColor(TEXTO_SECUNDARIO);
            g2.drawString(subtitulo, MARGEN_IZQ - 30, y);
        }
        if (series.size() >= 2) {
            y += 20;
            int x = MARGEN_IZQ - 30;
            FontMetrics fm = g2.getFontMetrics();
            for (Serie s : series) {
                g2.setColor(s.color());
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x, y - 4, x + 14, y - 4);
                g2.setStroke(new BasicStroke());
                g2.setColor(TEXTO);
                g2.drawString(s.nombre(), x + 20, y);
                x += 20 + fm.stringWidth(s.nombre()) + 18;
            }
        }
    }

    // Punto al final de cada línea + "Nombre  valor". Si chocan, se omite la de abajo (no se apilan).
    private void dibujarEtiquetasFinales(Graphics2D g2, int x0, int ancho, int y0, int alto, double tope) {
        record Fin(Serie serie, int x, int y, double valor) {}
        List<Fin> fines = new ArrayList<>();
        for (Serie s : series) {
            for (int i = s.valores().length - 1; i >= 0; i--) {
                double v = s.valores()[i];
                if (!Double.isNaN(v)) {
                    fines.add(new Fin(s, xDe(i, x0, ancho), y0 - (int) Math.round(v / tope * alto), v));
                    break;
                }
            }
        }
        for (Fin f : fines) {
            dibujarPunto(g2, f.x(), f.y(), f.serie().color());
        }
        fines.sort(Comparator.comparingInt(Fin::y));
        FontMetrics fm = g2.getFontMetrics();
        int ultimaY = -100_000; // (no Integer.MIN_VALUE: la resta se desbordaría)
        for (Fin f : fines) {
            if (f.y() - ultimaY < fm.getHeight()) continue;
            String texto = (series.size() >= 2 ? f.serie().nombre() + "  " : "") + formatear(f.valor());
            g2.setColor(TEXTO);
            g2.drawString(texto, f.x() + 9, f.y() + fm.getAscent() / 2 - 1);
            ultimaY = f.y();
        }
    }

    private void dibujarHover(Graphics2D g2, int x0, int ancho, int y0, int alto, double tope, int arriba) {
        int x = xDe(hover, x0, ancho);
        g2.setColor(TEXTO_SECUNDARIO);
        g2.drawLine(x, arriba, x, y0);

        List<String> lineas = new ArrayList<>();
        List<Color> colores = new ArrayList<>();
        for (Serie s : series) {
            double v = s.valores()[hover];
            if (Double.isNaN(v)) continue;
            dibujarPunto(g2, x, y0 - (int) Math.round(v / tope * alto), s.color());
            lineas.add(s.nombre() + ": " + formatear(v));
            colores.add(s.color());
        }

        FontMetrics fm = g2.getFontMetrics();
        String encabezado = "Día " + (primerDia + hover);
        int anchoCaja = fm.stringWidth(encabezado);
        for (String l : lineas) anchoCaja = Math.max(anchoCaja, 18 + fm.stringWidth(l));
        anchoCaja += 16;
        int altoCaja = 10 + fm.getHeight() * (lineas.size() + 1);
        int cx = x + 12 + anchoCaja > getWidth() - 4 ? x - 12 - anchoCaja : x + 12;
        int cy = arriba;

        g2.setColor(new Color(255, 255, 255, 240));
        g2.fillRoundRect(cx, cy, anchoCaja, altoCaja, 8, 8);
        g2.setColor(REJILLA.darker());
        g2.drawRoundRect(cx, cy, anchoCaja, altoCaja, 8, 8);
        int ty = cy + 5 + fm.getAscent();
        g2.setColor(TEXTO);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        g2.drawString(encabezado, cx + 8, ty);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN));
        for (int i = 0; i < lineas.size(); i++) {
            ty += fm.getHeight();
            g2.setColor(colores.get(i));
            g2.fillOval(cx + 8, ty - fm.getAscent() + 3, 9, 9);
            g2.setColor(TEXTO);
            g2.drawString(lineas.get(i), cx + 8 + 16, ty);
        }
    }

    // Punto ≥ 8px con anillo de 2px del color del fondo (se lee aunque cruce otra línea).
    private static void dibujarPunto(Graphics2D g2, int x, int y, Color color) {
        g2.setColor(FONDO);
        g2.fillOval(x - 6, y - 6, 12, 12);
        g2.setColor(color);
        g2.fillOval(x - 4, y - 4, 8, 8);
    }

    static String formatear(double v) {
        return String.format("%,d", Math.round(v));
    }
}

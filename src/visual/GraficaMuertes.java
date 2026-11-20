package visual;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import modelo.CausaMuerte;

// Barras horizontales: muertes de UNA especie por causa (de mayor a menor).
// Una sola especie por gráfica, así que todas las barras van de su color y la causa
// se lee en la etiqueta de la izquierda; el número va en la punta de cada barra.
public class GraficaMuertes extends JPanel {
    private static final int GROSOR = 18;      // ≤ 24px
    private static final int SEPARACION = 10;
    private static final int RADIO = 4;        // punta redondeada, base recta

    private final String titulo;
    private final Color color;
    private final List<Map.Entry<CausaMuerte, Integer>> barras = new ArrayList<>();
    private final int total;

    public GraficaMuertes(String titulo, Color color, Map<CausaMuerte, Integer> porCausa, int ancho) {
        this.titulo = titulo;
        this.color = color;
        porCausa.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<CausaMuerte, Integer>comparingByValue().reversed())
                .forEach(barras::add);
        this.total = barras.stream().mapToInt(Map.Entry::getValue).sum();
        int filas = Math.max(1, barras.size());
        setPreferredSize(new Dimension(ancho, 52 + filas * (GROSOR + SEPARACION)));
        setBackground(GraficaLineas.FONDO);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font base = getFont().deriveFont(12f);

        // Encabezado: nombre de la especie (con su marca de color) y total de muertes.
        g2.setColor(color);
        g2.fillRoundRect(16, 8, 10, 10, 4, 4);
        g2.setFont(base.deriveFont(Font.BOLD, 14f));
        g2.setColor(GraficaLineas.TEXTO);
        g2.drawString(titulo, 32, 18);
        g2.setFont(base);
        g2.setColor(GraficaLineas.TEXTO_SECUNDARIO);
        g2.drawString(total == 0 ? "Sin muertes" : GraficaLineas.formatear(total) + " muertes en total", 16, 36);
        if (barras.isEmpty()) {
            g2.dispose();
            return;
        }

        FontMetrics fm = g2.getFontMetrics();
        int anchoEtiquetas = 0;
        for (var b : barras) anchoEtiquetas = Math.max(anchoEtiquetas, fm.stringWidth(b.getKey().nombre()));
        int x0 = 16 + anchoEtiquetas + 10;                                // línea base
        int maxNumero = fm.stringWidth(GraficaLineas.formatear(barras.get(0).getValue()));
        int largoMax = Math.max(10, getWidth() - x0 - maxNumero - 24);
        int maximo = barras.get(0).getValue();

        int y = 50;
        for (var b : barras) {
            int largo = Math.max(2, (int) Math.round((double) b.getValue() / maximo * largoMax));
            // Etiqueta de la causa (texto, nunca del color de la serie)
            g2.setColor(GraficaLineas.TEXTO);
            g2.drawString(b.getKey().nombre(), 16, y + GROSOR / 2 + fm.getAscent() / 2 - 1);
            // Barra: base recta en x0, punta redondeada
            g2.setColor(color);
            g2.fillRoundRect(x0, y, largo, GROSOR, RADIO * 2, RADIO * 2);
            g2.fillRect(x0, y, Math.min(largo, RADIO * 2), GROSOR);
            // Valor en la punta
            g2.setColor(GraficaLineas.TEXTO);
            g2.drawString(GraficaLineas.formatear(b.getValue()), x0 + largo + 6, y + GROSOR / 2 + fm.getAscent() / 2 - 1);
            y += GROSOR + SEPARACION;
        }
        // Línea base (recesiva)
        g2.setColor(GraficaLineas.REJILLA);
        g2.drawLine(x0, 46, x0, y - SEPARACION + 4);
        g2.dispose();
    }
}

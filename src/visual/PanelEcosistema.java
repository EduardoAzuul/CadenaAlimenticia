package visual;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Graphics;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import javax.swing.JPanel;
import javax.swing.Timer;
import modelo.Actividad;
import modelo.Especie;
import modelo.VistaAnimal;

// Dibuja un ícono por individuo con la imagen de su especie y, en los animales, de lo que
// hizo hoy (nace, come/caza, descansa, duerme, se reproduce). Sin la carpeta Assets dibuja
// bolitas de colores. No hay coordenadas reales: cada individuo se coloca en un lugar al
// azar (pero estable entre repintados). Cada animal tiene su lugar fijo según su id; cuando se
// reproduce camina a su nido, se queda un momento con su pareja y regresa a su lugar.
public class PanelEcosistema extends JPanel {
    private static final int DIAMETRO = 12;
    private static final int LADO_LEYENDA = 16;
    private static final int ALTO_FILA_LEYENDA = 22;
    private static final int ALTO_BARRA = 22;
    // Orden de dibujo: plantas al fondo, depredadores encima.
    private static final Especie[] ORDEN = {
        Especie.PLANTA, Especie.DESCOMPONEDOR, Especie.HERBIVORO, Especie.DEPREDADOR
    };

    private final Map<Especie, Integer> poblaciones = new EnumMap<>(Especie.class);
    private final long semilla;
    private int biomasa = 0;
    private int biomasaMax = 0; // 0 = todavía no se sabe
    private int nutrientes = 0;
    private int nutrientesMax = 0;
    private int cadaveres = 0;
    private List<VistaAnimal> animales = List.of();
    private boolean hayAnimales = false; // false = solo se conocen los conteos (se dibujan por número)

    // Viaje al nido: ida, un rato en el nido con la pareja, y regreso (ms).
    private static final int MS_IDA = 700;
    private static final int MS_EN_NIDO = 500;
    private static final int MS_REGRESO = 700;
    private record Viaje(Especie especie, int nido, int lado, long inicio) {}  // lado: -1/0/+1 en el nido
    private final Map<Integer, Viaje> viajes = new HashMap<>();
    private final Timer animacion = new Timer(30, e -> repaint());
    private Map<Especie, List<Integer>> criasPorNido = new EnumMap<>(Especie.class);
    private static final int LADO_NIDO = 34;
    private static final Especie[] ESPECIES_CON_NIDO = { Especie.HERBIVORO, Especie.DEPREDADOR };

    public PanelEcosistema(long semilla) {
        this.semilla = semilla;
        for (Especie e : Especie.values()) poblaciones.put(e, 0);
        setPreferredSize(new Dimension(500, 400));
        setBackground(new Color(245, 240, 225));
    }

    public void setPoblaciones(Map<Especie, Integer> nuevas) {
        poblaciones.putAll(nuevas);
        repaint();
    }

    public void setBiomasa(int biomasa, int biomasaMax) {
        this.biomasa = biomasa;
        this.biomasaMax = biomasaMax;
        repaint();
    }

    public void setNutrientes(int nutrientes, int nutrientesMax) {
        this.nutrientes = nutrientes;
        this.nutrientesMax = nutrientesMax;
        repaint();
    }

    public void setNidos(Map<Especie, List<Integer>> criasPorNido) {
        this.criasPorNido = new EnumMap<>(Especie.class);
        this.criasPorNido.putAll(criasPorNido);
        repaint();
    }

    // Lo llama Swing al final de cada día. Los que se reprodujeron hoy empiezan su viaje al nido.
    public void setAnimales(List<VistaAnimal> nuevos) {
        animales = List.copyOf(nuevos);
        hayAnimales = true;
        long ahora = System.currentTimeMillis();

        Set<Integer> vivos = new HashSet<>();
        Map<String, List<VistaAnimal>> parejas = new HashMap<>();
        for (VistaAnimal a : animales) {
            vivos.add(a.id());
            if (a.nido() >= 0 && !viajes.containsKey(a.id())) {
                parejas.computeIfAbsent(a.especie() + "#" + a.nido(), k -> new ArrayList<>()).add(a);
            }
        }
        viajes.keySet().retainAll(vivos); // los que murieron a medio viaje ya no se dibujan
        // Dos en el mismo nido: uno a la izquierda y otro a la derecha, para que se vean los dos.
        for (List<VistaAnimal> pareja : parejas.values()) {
            pareja.sort(Comparator.comparingInt(VistaAnimal::id));
            for (int i = 0; i < pareja.size(); i++) {
                int lado = pareja.size() == 1 ? 0 : (i % 2 == 0 ? -1 : 1);
                VistaAnimal a = pareja.get(i);
                viajes.put(a.id(), new Viaje(a.especie(), a.nido(), lado, ahora));
            }
        }
        if (!viajes.isEmpty() && !animacion.isRunning()) {
            animacion.start();
        }
        repaint();
    }

    public void setCadaveres(int cadaveres) {
        this.cadaveres = cadaveres;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Encabezado: leyenda (una o más filas, según el ancho) + barra de biomasa + barra de nutrientes.
        int altoLeyenda = dibujarLeyenda(g2, false);
        int altoEncabezado = altoLeyenda + 2 * ALTO_BARRA;
        int ancho = Math.max(1, getWidth() - DIAMETRO);
        int alto = Math.max(1, getHeight() - altoEncabezado - DIAMETRO);

        Map<Especie, List<Rectangle>> nidos = rectangulosNidos(altoEncabezado);
        dibujarNidos(g2, nidos);
        dibujarCadaveres(g2, ancho, alto, altoEncabezado);
        for (Especie especie : ORDEN) {
            if (hayAnimales && (especie == Especie.HERBIVORO || especie == Especie.DEPREDADOR)) {
                continue; // los animales van aparte, cada uno en su lugar
            }
            dibujarPorConteo(g2, especie, altoEncabezado);
        }
        if (hayAnimales) {
            dibujarAnimales(g2, altoEncabezado, nidos);
        }
        dibujarLeyenda(g2, true);
        dibujarBarra(g2, altoLeyenda, "Biomasa", biomasa, biomasaMax, ColoresEspecie.BIOMASA);
        dibujarBarra(g2, altoLeyenda + ALTO_BARRA, "Nutrientes", nutrientes, nutrientesMax, ColoresEspecie.NUTRIENTES);
    }

    // Animales más grandes que plantas y descomponedores.
    private static int ladoIcono(Especie especie) {
        return switch (especie) {
            case PLANTA -> 26;
            case DESCOMPONEDOR -> 28;
            case HERBIVORO -> 42;
            case DEPREDADOR -> 46;
        };
    }

    // Plantas y descomponedores (y animales si solo se tienen conteos): N íconos al azar.
    private void dibujarPorConteo(Graphics2D g2, Especie especie, int altoEncabezado) {
        Random rnd = new Random(semilla * 31 + especie.ordinal());
        int lado = ladoIcono(especie);
        int anchoEsp = Math.max(1, getWidth() - lado);
        int altoEsp = Math.max(1, getHeight() - altoEncabezado - lado);
        int n = poblaciones.get(especie);
        for (int i = 0; i < n; i++) {
            int x = rnd.nextInt(anchoEsp);
            int y = altoEncabezado + rnd.nextInt(altoEsp);
            dibujarIcono(g2, especie, Actividad.DESCANSA, x, y);
        }
    }

    // Cada animal en su lugar fijo, o caminando a su nido y de regreso.
    // Los que van de viaje se dibujan al final, encima de los demás.
    private void dibujarAnimales(Graphics2D g2, int altoEncabezado, Map<Especie, List<Rectangle>> nidos) {
        long ahora = System.currentTimeMillis();
        List<Runnable> deViaje = new ArrayList<>();
        for (Especie especie : new Especie[] { Especie.HERBIVORO, Especie.DEPREDADOR }) {
            for (VistaAnimal a : animales) {
                if (a.especie() != especie) continue;
                int lado = ladoIcono(especie);
                double[] casa = lugarFijo(a, altoEncabezado);
                Viaje viaje = viajes.get(a.id());
                if (viaje == null) {
                    dibujarIcono(g2, especie, a.actividad(), (int) casa[0], (int) casa[1]);
                    continue;
                }
                long t = ahora - viaje.inicio();
                List<Rectangle> deEspecie = nidos.getOrDefault(especie, List.of());
                if (t >= MS_IDA + MS_EN_NIDO + MS_REGRESO || viaje.nido() >= deEspecie.size()) {
                    viajes.remove(a.id()); // ya regresó (o no se sabe dónde está su nido)
                    dibujarIcono(g2, especie, a.actividad(), (int) casa[0], (int) casa[1]);
                    continue;
                }
                Rectangle r = deEspecie.get(viaje.nido());
                double nidoX = r.getCenterX() + viaje.lado() * lado * 0.4 - lado / 2.0;
                double nidoY = r.getCenterY() - lado / 2.0;
                double p; // 0 = en su lugar, 1 = en el nido
                if (t < MS_IDA) p = suave((double) t / MS_IDA);
                else if (t < MS_IDA + MS_EN_NIDO) p = 1;
                else p = 1 - suave((double) (t - MS_IDA - MS_EN_NIDO) / MS_REGRESO);
                int x = (int) Math.round(casa[0] + (nidoX - casa[0]) * p);
                int y = (int) Math.round(casa[1] + (nidoY - casa[1]) * p);
                // Mientras va y viene se ve como "reproduciéndose".
                deViaje.add(() -> dibujarIcono(g2, especie, Actividad.REPRODUCE, x, y));
            }
        }
        deViaje.forEach(Runnable::run);
        if (viajes.isEmpty()) {
            animacion.stop();
        }
    }

    // Lugar de cada animal: depende solo de su id (y de la región), así siempre regresa al mismo.
    private double[] lugarFijo(VistaAnimal a, int altoEncabezado) {
        int lado = ladoIcono(a.especie());
        // SplittableRandom mezcla la semilla: con Random, ids seguidos quedaban casi en la misma x.
        SplittableRandom rnd = new SplittableRandom(semilla * 31 + a.especie().ordinal() * 1_000_003L + a.id());
        double x = rnd.nextDouble() * Math.max(1, getWidth() - lado);
        double y = altoEncabezado + rnd.nextDouble() * Math.max(1, getHeight() - altoEncabezado - lado);
        return new double[] { x, y };
    }

    // Arranca y frena despacio (en vez de moverse a velocidad constante).
    private static double suave(double t) {
        return t * t * (3 - 2 * t);
    }

    private void dibujarIcono(Graphics2D g2, Especie especie, Actividad actividad, int x, int y) {
        BufferedImage icono = ImagenesEspecie.icono(especie, actividad, ladoIcono(especie));
        if (icono != null) {
            g2.drawImage(icono, x, y, null);
        } else {
            g2.setColor(ColoresEspecie.de(especie));
            g2.fillOval(x, y, DIAMETRO, DIAMETRO);
        }
    }

    // Dónde está cada nido (en el orden de la región). Al azar pero estable, sin encimarse.
    private Map<Especie, List<Rectangle>> rectangulosNidos(int altoEncabezado) {
        Random rnd = new Random(semilla * 31 + 500);
        List<Rectangle> ocupados = new ArrayList<>();
        Map<Especie, List<Rectangle>> resultado = new EnumMap<>(Especie.class);
        int anchoArea = Math.max(1, getWidth() - LADO_NIDO - 8);
        int altoArea = Math.max(1, getHeight() - altoEncabezado - LADO_NIDO - 8);
        for (Especie especie : ESPECIES_CON_NIDO) {
            List<Rectangle> deEspecie = new ArrayList<>();
            for (int k = 0; k < criasPorNido.getOrDefault(especie, List.of()).size(); k++) {
                Rectangle r = null;
                for (int intento = 0; intento < 40; intento++) {
                    Rectangle candidato = new Rectangle(4 + rnd.nextInt(anchoArea),
                            altoEncabezado + 4 + rnd.nextInt(altoArea), LADO_NIDO, LADO_NIDO);
                    r = candidato;
                    Rectangle conMargen = new Rectangle(candidato.x - 6, candidato.y - 6,
                            candidato.width + 12, candidato.height + 12);
                    if (ocupados.stream().noneMatch(conMargen::intersects)) break;
                }
                ocupados.add(r);
                deEspecie.add(r);
            }
            resultado.put(especie, deEspecie);
        }
        return resultado;
    }

    // Cada nido es un cuadrado del color de su especie; el número son las crías nacidas ahí.
    private void dibujarNidos(Graphics2D g2, Map<Especie, List<Rectangle>> nidos) {
        Font fuenteOriginal = g2.getFont();
        g2.setFont(fuenteOriginal.deriveFont(Font.BOLD, 11f));

        for (Especie especie : ESPECIES_CON_NIDO) {
            Color color = ColoresEspecie.de(especie);
            List<Integer> crias = criasPorNido.getOrDefault(especie, List.of());
            List<Rectangle> rects = nidos.getOrDefault(especie, List.of());
            for (int k = 0; k < crias.size() && k < rects.size(); k++) {
                Rectangle r = rects.get(k);

                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRect(r.x, r.y, r.width, r.height);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(r.x, r.y, r.width, r.height);
                g2.setStroke(new BasicStroke());

                String texto = String.valueOf(crias.get(k));
                var fm = g2.getFontMetrics();
                g2.setColor(color.darker());
                g2.drawString(texto, r.x + (r.width - fm.stringWidth(texto)) / 2,
                        r.y + (r.height + fm.getAscent()) / 2 - 2);
            }
        }
        g2.setFont(fuenteOriginal);
    }

    // Cada cadáver (búfer de materia muerta) es una crucecita gris, debajo de los vivos.
    private void dibujarCadaveres(Graphics2D g2, int ancho, int alto, int altoEncabezado) {
        Random rnd = new Random(semilla * 31 + 99);
        g2.setColor(ColoresEspecie.CADAVER);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int t = DIAMETRO - 2;
        for (int i = 0; i < cadaveres; i++) {
            int x = rnd.nextInt(ancho);
            int y = altoEncabezado + rnd.nextInt(alto);
            g2.drawLine(x, y, x + t, y + t);
            g2.drawLine(x + t, y, x, y + t);
        }
        g2.setStroke(new BasicStroke());
    }

    // Barra horizontal: cuánto hay en un búfer contra su capacidad.
    private void dibujarBarra(Graphics2D g2, int yFila, String nombre, int valor, int maximo, Color color) {
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRect(0, yFila, getWidth(), ALTO_BARRA);

        String texto = nombre + ": " + valor + (maximo > 0 ? " / " + maximo : "");
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(texto, 8, yFila + 14);

        int x = 8 + g2.getFontMetrics().stringWidth("Nutrientes: 000 / 000") + 10;
        int y = yFila + 4;
        int ancho = Math.max(0, getWidth() - x - 8);
        int alto = ALTO_BARRA - 10;
        g2.setColor(new Color(225, 225, 215));
        g2.fillRoundRect(x, y, ancho, alto, alto, alto);
        if (maximo > 0) {
            int lleno = (int) Math.round(ancho * Math.min(1.0, (double) valor / maximo));
            g2.setColor(color);
            g2.fillRoundRect(x, y, lleno, alto, alto, alto);
        }
        g2.setColor(new Color(170, 170, 160));
        g2.drawRoundRect(x, y, ancho, alto, alto, alto);
    }

    // Dibuja la leyenda pasando a otra fila cuando no cabe. Regresa su alto.
    // Con dibujar=false solo mide (para saber dónde empiezan las bolitas).
    private int dibujarLeyenda(Graphics2D g2, boolean dibujar) {
        int filas = 1;
        int x = 8;
        int anchoDisponible = getWidth() - 8;
        var fm = g2.getFontMetrics();

        // Primero medir cuántas filas hacen falta para pintar el fondo.
        for (int i = 0; i < Especie.values().length + 2; i++) {
            int anchoItem = LADO_LEYENDA + 4 + fm.stringWidth(textoItem(i)) + 8;
            if (x > 8 && x + anchoItem > anchoDisponible) {
                filas++;
                x = 8;
            }
            x += anchoItem;
        }
        int altoTotal = filas * ALTO_FILA_LEYENDA + 6;
        if (!dibujar) {
            return altoTotal;
        }

        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRect(0, 0, getWidth(), altoTotal);
        x = 8;
        int fila = 0;
        for (int i = 0; i < Especie.values().length + 2; i++) {
            String texto = textoItem(i);
            int anchoItem = LADO_LEYENDA + 4 + fm.stringWidth(texto) + 8;
            if (x > 8 && x + anchoItem > anchoDisponible) {
                fila++;
                x = 8;
            }
            int y = 6 + fila * ALTO_FILA_LEYENDA;
            if (i < Especie.values().length) {
                Especie especie = Especie.values()[i];
                BufferedImage icono = ImagenesEspecie.icono(especie, Actividad.DESCANSA, LADO_LEYENDA);
                if (icono != null) {
                    g2.drawImage(icono, x, y, null);
                } else {
                    g2.setColor(ColoresEspecie.de(especie));
                    g2.fillOval(x, y + 2, DIAMETRO, DIAMETRO);
                }
            } else if (i == Especie.values().length + 1) {
                // Nidos: cuadrito
                g2.setColor(Color.GRAY);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x + 1, y + 3, DIAMETRO - 2, DIAMETRO - 2);
                g2.setStroke(new BasicStroke());
            } else {
                // Cadáveres: crucecita gris
                g2.setColor(ColoresEspecie.CADAVER);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int t = DIAMETRO - 2;
                g2.drawLine(x + 1, y + 3, x + 1 + t, y + 3 + t);
                g2.drawLine(x + 1 + t, y + 3, x + 1, y + 3 + t);
                g2.setStroke(new BasicStroke());
            }
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(texto, x + LADO_LEYENDA + 4, y + 13);
            x += anchoItem;
        }
        return altoTotal;
    }

    // Primero las especies, luego los cadáveres y al final los nidos.
    private String textoItem(int i) {
        if (i < Especie.values().length) {
            Especie e = Especie.values()[i];
            return e.name().charAt(0) + e.name().substring(1).toLowerCase() + ": " + poblaciones.get(e);
        }
        if (i == Especie.values().length) {
            return "Cadáveres: " + cadaveres;
        }
        return "Nidos (crías): " + criasPorNido.getOrDefault(Especie.HERBIVORO, List.of()).size() + " herb. / "
                + criasPorNido.getOrDefault(Especie.DEPREDADOR, List.of()).size() + " depred.";
    }
}

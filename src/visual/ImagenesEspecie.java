package visual;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import modelo.Actividad;
import modelo.Especie;

// Imágenes de la carpeta Assets (al lado de src). Cada individuo se dibuja como un
// ícono redondo con la imagen de su especie y de lo que hizo hoy, con un borde del
// color de su especie. Si no se encuentra la carpeta, el panel dibuja bolitas como antes.
// Solo se usa desde el hilo de Swing.
public final class ImagenesEspecie {
    private static final File CARPETA = buscarCarpeta();
    private static final Map<String, BufferedImage> originales = new HashMap<>();
    private static final Map<String, BufferedImage> iconos = new HashMap<>();

    private ImagenesEspecie() {}

    public static String archivo(Especie especie, Actividad actividad) {
        if (actividad == null) actividad = Actividad.DESCANSA;
        return switch (especie) {
            case PLANTA -> "Pasto.jpg";
            case DESCOMPONEDOR -> "Descomponedor.jpg";
            case HERBIVORO -> switch (actividad) {
                case NACE -> "Vaca_Nace.jpg";
                case COME -> "Vaca_Come.jpg";
                case DESCANSA -> "Vaca_Descansa.jpg";
                case DUERME -> "Vaca_Duerme.jpg";
                case REPRODUCE -> "Vaca_Reproduccion.jpg";
            };
            case DEPREDADOR -> switch (actividad) {
                case NACE -> "Lobo_Nace.jpg";
                case COME -> "Lobo_Caza.jpg";
                case DESCANSA -> "Lobo_Descansa.jpg";
                case DUERME -> "Lobo_duerme.jpg";
                case REPRODUCE -> "Lobo_Reproduccion.jpg";
            };
        };
    }

    // Ícono redondo de lado x lado (null si no hay imagen).
    public static BufferedImage icono(Especie especie, Actividad actividad, int lado) {
        String nombre = archivo(especie, actividad);
        String clave = nombre + "@" + lado;
        if (iconos.containsKey(clave)) {
            return iconos.get(clave);
        }
        BufferedImage original = cargar(nombre);
        BufferedImage icono = original == null ? null
                : recortarRedondo(original, lado, ColoresEspecie.de(especie), seVeEntera(especie));
        iconos.put(clave, icono);
        return icono;
    }

    // El pasto y el descomponedor tienen fondo blanco: se ven enteros sobre un círculo blanco.
    // Las fotos de animales llenan el círculo (se recortan las orillas).
    private static boolean seVeEntera(Especie especie) {
        return especie == Especie.PLANTA || especie == Especie.DESCOMPONEDOR;
    }

    private static BufferedImage cargar(String nombre) {
        if (originales.containsKey(nombre)) {
            return originales.get(nombre);
        }
        BufferedImage img = null;
        if (CARPETA != null) {
            try {
                img = ImageIO.read(new File(CARPETA, nombre));
            } catch (IOException e) {
                System.err.println("No pude leer " + nombre + ": " + e.getMessage());
            }
        }
        originales.put(nombre, img);
        return img;
    }

    private static BufferedImage recortarRedondo(BufferedImage original, int lado, Color borde, boolean entera) {
        // Primero se reduce en pasos (mejor calidad que escalar de golpe una foto grande).
        double escala = entera
                ? Math.min((double) lado / original.getWidth(), (double) lado / original.getHeight())
                : Math.max((double) lado / original.getWidth(), (double) lado / original.getHeight());
        int w = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage reducida = reducir(original, w, h);

        BufferedImage icono = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icono.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Ellipse2D circulo = new Ellipse2D.Double(1, 1, lado - 2, lado - 2);
        g.setColor(Color.WHITE);
        g.fill(circulo);
        g.setClip(circulo);
        g.drawImage(reducida, (lado - w) / 2, (lado - h) / 2, null);
        g.setClip(null);
        g.setColor(borde);
        g.setStroke(new BasicStroke(2f));
        g.draw(circulo);
        g.dispose();
        return icono;
    }

    private static BufferedImage reducir(BufferedImage img, int w, int h) {
        BufferedImage actual = img;
        int cw = img.getWidth();
        int ch = img.getHeight();
        do {
            cw = Math.max(w, cw / 2);
            ch = Math.max(h, ch / 2);
            BufferedImage siguiente = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = siguiente.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(actual, 0, 0, cw, ch, null);
            g.dispose();
            actual = siguiente;
        } while (cw != w || ch != h);
        return actual;
    }

    // Busca la carpeta Assets subiendo desde el directorio de trabajo y desde donde están
    // las clases compiladas (out/production/...). También se puede indicar con -Dassets=ruta.
    private static File buscarCarpeta() {
        String propiedad = System.getProperty("assets");
        if (propiedad != null && new File(propiedad).isDirectory()) {
            return new File(propiedad);
        }
        File[] inicios = { new File(System.getProperty("user.dir")), carpetaDeClases() };
        for (File inicio : inicios) {
            for (File dir = inicio; dir != null; dir = dir.getParentFile()) {
                File assets = new File(dir, "Assets");
                if (new File(assets, "Pasto.jpg").isFile()) {
                    return assets;
                }
            }
        }
        System.err.println("No encontré la carpeta Assets: se dibujan bolitas de colores");
        return null;
    }

    private static File carpetaDeClases() {
        try {
            return new File(ImagenesEspecie.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getAbsoluteFile();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return null;
        }
    }
}

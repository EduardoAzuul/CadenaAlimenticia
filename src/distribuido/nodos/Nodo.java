package distribuido.nodos;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import distribuido.ConexionTCP;
import distribuido.Mensaje;

// Base de todos los nodos. Topología en estrella: todos se conectan al coordinador,
// y él reenvía los mensajes que van de una región a otra.
public abstract class Nodo implements Runnable {
    public static final String COORDINADOR = "COORDINADOR";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    protected final String id;
    protected final String host;
    protected final int puerto;
    protected final Map<String, ConexionTCP> conexiones = new ConcurrentHashMap<>();
    protected volatile boolean activo = true;

    protected Nodo(String id, String host, int puerto) {
        this.id = id;
        this.host = host;
        this.puerto = puerto;
    }

    public boolean enviarMensaje(String destino, Mensaje mensaje) {
        ConexionTCP conexion = conexiones.get(destino);
        return conexion != null && conexion.enviar(mensaje);
    }

    public abstract void procesarMensaje(Mensaje mensaje);

    // Lee mensajes de una conexión hasta que se cierre.
    protected void escuchar(ConexionTCP conexion) {
        try {
            Mensaje m;
            while (activo && (m = conexion.recibir()) != null) {
                try {
                    procesarMensaje(m);
                } catch (RuntimeException e) {
                    log("Error procesando " + m.getTipo() + ": " + e);
                }
            }
        } catch (IOException e) {
            // conexión cerrada
        }
    }

    protected void log(String texto) {
        System.out.println(LocalTime.now().format(HORA) + " [" + id + "] " + texto);
    }

    public String getId() { return id; }
}

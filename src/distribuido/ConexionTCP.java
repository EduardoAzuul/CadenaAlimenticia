package distribuido;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Un mensaje JSON por línea sobre un socket TCP.
public class ConexionTCP {
    private final String host;
    private final int puerto;
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;

    // Cliente: se conecta con conectar().
    public ConexionTCP(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    // Servidor: socket ya aceptado.
    public ConexionTCP(Socket socket) throws IOException {
        this.host = socket.getInetAddress().getHostAddress();
        this.puerto = socket.getPort();
        this.socket = socket;
        abrirFlujos();
    }

    public void conectar() throws IOException {
        socket = new Socket(host, puerto);
        abrirFlujos();
    }

    private void abrirFlujos() throws IOException {
        socket.setTcpNoDelay(true);
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    // Varios hilos pueden enviar por la misma conexión: synchronized para no mezclar líneas.
    public synchronized boolean enviar(Mensaje mensaje) {
        if (salida == null) return false;
        salida.println(mensaje.aJson());
        return !salida.checkError();
    }

    // Bloquea hasta el siguiente mensaje. null = la otra punta cerró.
    public Mensaje recibir() throws IOException {
        String linea = entrada.readLine();
        return linea == null ? null : Mensaje.desdeJson(linea);
    }

    public void cerrar() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean estaAbierta() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public String toString() {
        return host + ":" + puerto;
    }
}

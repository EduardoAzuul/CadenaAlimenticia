package region.recursos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

// Sección crítica (búfer = 1): escritura exclusiva para conservar el orden.
public class Bitacora {
    private final List<String> eventos = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean imprimir;

    public void registrar(int dia, String evento) {
        String linea = "[Día " + dia + "] " + evento;
        lock.lock();
        try {
            eventos.add(linea);
            if (imprimir) {
                System.out.println(linea);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<String> getEventos() {
        lock.lock();
        try {
            return new ArrayList<>(eventos);
        } finally {
            lock.unlock();
        }
    }

    public void setImprimir(boolean imprimir) { this.imprimir = imprimir; }

    public void guardarEnArchivo(String ruta) throws IOException {
        Files.write(Path.of(ruta), getEventos());
    }
}

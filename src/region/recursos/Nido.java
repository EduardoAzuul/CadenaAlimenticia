package region.recursos;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import agentes.Animal;
import modelo.Especie;

// Nido de una sola especie, para una pareja (búfer = 2).
// El primero que llega lo ocupa y espera (con timeout); el segundo se une y lo despierta.
public class Nido {
    public record Pareja(Animal animal, int aporte) {}

    private final int id;
    private final Especie especie;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition llegoPareja = lock.newCondition();
    private Animal ocupante;   // el que espera
    private Animal pareja;     // el que llegó
    private int aportePareja;  // energía que aporta la pareja a la cría
    private final AtomicInteger crias = new AtomicInteger();

    public Nido(int id, Especie especie) {
        this.id = id;
        this.especie = especie;
    }

    // Ocupa el nido si está vacío. No bloquea.
    public boolean ocupar(Animal animal) {
        if (!lock.tryLock()) return false;
        try {
            if (ocupante != null) return false;
            ocupante = animal;
            pareja = null;
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Se une al animal que espera aquí. No bloquea. true = ya son pareja.
    public boolean unirse(Animal animal, int aporte) {
        if (!lock.tryLock()) return false;
        try {
            if (ocupante == null || ocupante == animal || pareja != null) return false;
            pareja = animal;
            aportePareja = aporte;
            llegoPareja.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Lo llama el ocupante: espera a su pareja hasta timeoutMs y deja el nido libre.
    // null = nadie llegó a tiempo.
    public Pareja esperarPareja(long timeoutMs) {
        lock.lock();
        try {
            long restante = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (pareja == null && restante > 0) {
                try {
                    restante = llegoPareja.awaitNanos(restante);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // Se revisa bajo el lock: si la pareja llegó justo al acabarse el tiempo, cuenta.
            Pareja resultado = pareja != null ? new Pareja(pareja, aportePareja) : null;
            ocupante = null;
            pareja = null;
            return resultado;
        } finally {
            lock.unlock();
        }
    }

    public boolean hayAlguienEsperando() {
        lock.lock();
        try {
            return ocupante != null && pareja == null;
        } finally {
            lock.unlock();
        }
    }

    public void registrarCria() { crias.incrementAndGet(); }
    public int getCrias() { return crias.get(); }
    public int getId() { return id; }
    public Especie getEspecie() { return especie; }
}

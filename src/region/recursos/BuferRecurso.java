package region.recursos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

// Zona crítica (búfer > 1): productor-consumidor.
// espaciosLibres cuenta huecos, elementosDisponibles cuenta lo que hay.
// Los semáforos son justos (fair) para evitar inanición.
public class BuferRecurso<T> {
    private final int capacidad;
    private final Deque<T> elementos = new ArrayDeque<>();
    private final Semaphore espaciosLibres;
    private final Semaphore elementosDisponibles;
    private final ReentrantLock lock = new ReentrantLock();

    public BuferRecurso(int capacidad) {
        this.capacidad = capacidad;
        this.espaciosLibres = new Semaphore(capacidad, true);
        this.elementosDisponibles = new Semaphore(0, true);
    }

    // Bloquea hasta que haya espacio.
    public void producir(T elemento) throws InterruptedException {
        espaciosLibres.acquire();
        agregar(elemento);
    }

    // Espera hasta timeoutMs a que haya espacio. false = siguió lleno.
    public boolean producirConTimeout(T elemento, long timeoutMs) {
        try {
            if (!espaciosLibres.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        agregar(elemento);
        return true;
    }

    // No bloquea: false si está lleno.
    public boolean intentarProducir(T elemento) {
        if (!espaciosLibres.tryAcquire()) {
            return false;
        }
        agregar(elemento);
        return true;
    }

    // Espera hasta timeoutMs; regresa null si no llegó nada.
    public T consumirConTimeout(long timeoutMs) {
        try {
            if (!elementosDisponibles.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        T elemento;
        lock.lock();
        try {
            elemento = elementos.pollFirst();
        } finally {
            lock.unlock();
        }
        espaciosLibres.release();
        return elemento;
    }

    // Saca un elemento específico (p. ej. una presa que murió de hambre).
    public boolean remover(T elemento) {
        if (!elementosDisponibles.tryAcquire()) {
            return false;
        }
        boolean quitado;
        lock.lock();
        try {
            quitado = elementos.remove(elemento);
        } finally {
            lock.unlock();
        }
        if (quitado) {
            espaciosLibres.release();
        } else {
            elementosDisponibles.release(); // no estaba: regresar el permiso
        }
        return quitado;
    }

    public int tamano() {
        lock.lock();
        try {
            return elementos.size();
        } finally {
            lock.unlock();
        }
    }

    public int getCapacidad() { return capacidad; }

    private void agregar(T elemento) {
        lock.lock();
        try {
            elementos.addLast(elemento);
        } finally {
            lock.unlock();
        }
        elementosDisponibles.release();
    }
}

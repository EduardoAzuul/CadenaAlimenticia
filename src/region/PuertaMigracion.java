package region;

import agentes.Animal;

// La implementa el nodo de la región (NodoRegion). La región no sabe nada de red.
public interface PuertaMigracion {
    // true = el animal ya salió hacia otra región.
    boolean migrar(Animal animal);
}

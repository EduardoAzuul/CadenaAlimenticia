package modelo;

// Lo que el panel necesita saber de cada animal vivo al terminar el día.
// nido = índice del nido de su especie donde se reprodujo hoy, o -1.
public record VistaAnimal(int id, Especie especie, Actividad actividad, int nido) {}

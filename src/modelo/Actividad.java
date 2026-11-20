package modelo;

// Lo que hizo un animal en su último día; el panel lo dibuja con la imagen que le toca.
//   NACE       su primer día (todavía no actúa)
//   COME       comió una planta (herbívoro) o cazó (depredador)
//   DESCANSA   no comió: el depredador digiere tras cazar, o no encontró comida
//   DUERME     no comió y le queda poca energía
//   REPRODUCE  tuvo una cría en el nido
public enum Actividad { NACE, COME, DESCANSA, DUERME, REPRODUCE }

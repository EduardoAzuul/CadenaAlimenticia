package distribuido;

public enum TipoMensaje {
    REGISTRO,            // nodo -> coordinador: "soy la región X" / "soy un panel"
    CONFIGURACION,       // coordinador -> región: parámetros generales (tasas de reproducción)
    HEARTBEAT,           // región -> coordinador: sigo viva
    TICK,                // coordinador -> regiones: empieza el día N
    TICK_DONE,           // región -> coordinador: terminé el día N (barrera)
    STATS,               // región -> coordinador -> paneles: poblaciones y búferes
    MIGRATION_REQUEST,   // origen -> destino: ¿tienes lugar? (fase 1)
    MIGRATION_ACK,       // destino -> origen: sí, te reservé lugar
    MIGRATION_NACK,      // destino -> origen: no hay lugar (o la región está caída)
    MIGRATION_TRANSFER,  // origen -> destino: aquí va el estado del animal (fase 2)
    MIGRATION_ARRIVED,   // destino -> origen: ya llegó (fase 3)
    MIGRATION_CANCEL,    // origen -> destino: ya no va, libera la reserva
    REGION_CAIDA,        // coordinador -> todos: una región dejó de responder
    FIN                  // coordinador -> todos: terminó la simulación
}

package modelo;

// Por qué murió un agente. El texto es el que se escribe en la bitácora.
public enum CausaMuerte {
    VEJEZ("murió de viejo", "Vejez"),
    HAMBRE("murió de hambre", "Hambre"),
    CAZADO("fue cazado", "Cazado"),
    COMIDA("fue comida por completo", "Comida entera"),
    SECA("se secó por falta de nutrientes", "Falta de nutrientes"),
    OTRA("fue eliminado", "Otra");

    private final String textoBitacora;
    private final String nombre;

    CausaMuerte(String textoBitacora, String nombre) {
        this.textoBitacora = textoBitacora;
        this.nombre = nombre;
    }

    public String textoBitacora() { return textoBitacora; }
    public String nombre() { return nombre; }
}

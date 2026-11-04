package br.unipar.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CelulaPDF {
    private String texto;
    private float x;
    private float y;
    private float largura;
    private float altura;
    private int pagina;

    public CelulaPDF(String texto, float x, float y,
                       float largura, float altura, int pagina) {
        this.texto = texto;
        this.x = x;
        this.y = y;
        this.largura = largura;
        this.altura = altura;
        this.pagina = pagina;
    }
}

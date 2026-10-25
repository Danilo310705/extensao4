package br.unipar;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import technology.tabula.TextStripper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        //aqui eu estou pegando o arquivo pdf que eu quero converter
        File arquivo = new File(
            "C:\\Users\\Usuario\\Desktop\\codigos da univer\\periodo 4\\Back-end\\TEXTE.pdf");

        //aqui eu irei usar o Loader para ler o arquivo, e já verifico se ele é pdf com try
        try(PDDocument documentoPDF = Loader.loadPDF(arquivo)){
            // o PDFTextStripper eu uso para retirar o texto do pdf,
            // IMPORTANTE: se a escrita estiver numa imagem ele NÃO consegue ler
            MeuRemovedorDeTexto removedorDeTexto = new MeuRemovedorDeTexto();

            //aqui ele vai tentar ordenar o texto da esquerda para a direita e de cima para baixo
            removedorDeTexto.setSortByPosition(true);

            // remove o texto
            String textoRemovido = removedorDeTexto.getText(documentoPDF);

            for (ArrayList<String> linha : removedorDeTexto.getLinhas()) {
                System.out.println(linha);
            }

        }catch (Exception e){
            System.out.printf("Deu erro irmão, foi esse aqui "+ e);
        }
    }



    static class MeuRemovedorDeTexto extends PDFTextStripper {

        public MeuRemovedorDeTexto() throws IOException {
            super();
        }

        //tive que colocar a variavel fora, pois writeString reseta o valor dela
        float yAnterior = -1;
        private float xFinalAnterior = -1;
        private StringBuilder celula = new StringBuilder();
        private ArrayList<String> tabelaDeCelulas = new ArrayList<>();
        private ArrayList<ArrayList<String>> tabelaLinhaDeCelulas = new ArrayList<>();


        @Override
        protected void endPage(PDPage pagina) throws IOException {

            finalizarLinha();

            xFinalAnterior = -1;
            yAnterior = -1;

            super.endPage(pagina);
        }

        @Override
        protected void writeString(
                String texto,
                List<TextPosition> posicoes
        ) throws IOException {


            for (TextPosition textPosition : posicoes) {

                String caractere = textPosition.getUnicode();//getUnicode pega o valor

                float yAtual = textPosition.getYDirAdj();//getYDirAdj vai pegar o Y
                float x = textPosition.getXDirAdj();//getYDirAdj vai pegar o x

                float largura = textPosition.getWidthDirAdj();

                if(yAnterior !=-1 && Math.abs(yAtual - yAnterior) > 2){
//                    System.out.println("Célula: " + celula);
                    finalizarLinha();

                    //System.out.println("----nova linha----");
                    xFinalAnterior = -1;
                }

                // Verifica se existe um espaço grande entre os caracteres
                if (xFinalAnterior != -1) {

                    float distancia = x - xFinalAnterior;
                    //System.out.println("distancia" + distancia);

                    if (distancia > 10) {
//                        System.out.println("Célula: " + celula);
                        finalizarCelula();

                    }
                }

                // Adiciona o caractere à célula atual
                celula.append(caractere);

                //System.out.println("Texto: " + caractere + " X: " + x + " Y: " + yAtual );
                //System.out.println("Texto: " + caractere + " Y: " + yAtual );

                //vao salvar os valores atual para o proximo
                xFinalAnterior = x + largura;
                yAnterior = yAtual;
            }

            super.writeString(texto, posicoes);
        }

        private void finalizarCelula() {

            if (celula.length() > 0) {
                tabelaDeCelulas.add(celula.toString());
                celula.setLength(0);
            }

        }

        private void finalizarLinha() {

            finalizarCelula();

            if (!tabelaDeCelulas.isEmpty()) {
                tabelaLinhaDeCelulas.add(new ArrayList<>(tabelaDeCelulas));
                tabelaDeCelulas.clear();
            }
        }

        public ArrayList<ArrayList<String>> getLinhas() {
            return tabelaLinhaDeCelulas;
        }
    }
}//teste linha 69

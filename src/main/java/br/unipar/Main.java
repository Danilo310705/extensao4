package br.unipar;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import technology.tabula.TextStripper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        //aqui eu estou pegando o arquivo pdf que eu quero converter
        File arquivo = new File(
            "C:\\Users\\Usuario\\Desktop\\codigos da univer\\periodo 4\\Back-end\\TEXTE.pdf");

        //aqui eu estou usando o Loader para ler o arquivo, e ja verifico se ele é pdf com try
        try(PDDocument documentoPDF = Loader.loadPDF(arquivo)){
            // o PDFTextStripper eu uso para retirar o texto do pdf,
            // IMPORTANTE: se a escrita estiver em uma imagem ele NÃO consegue ler
            PDFTextStripper removedorDeTexto = new PDFTextStripper();

            //aqui ele vai tentar ordenar o texto da esquerda para a direita e de cima para baixo
            removedorDeTexto.setSortByPosition(true);

            String textoRemovido = removedorDeTexto.getText(documentoPDF);

            float y = textPosition.getYDirAdj();



            System.out.println(textoRemovido);


        }catch (Exception e){
            System.out.printf("Deu erro irmão, foi esse aqui "+ e);
        }
    }

    static class MeuRemovedorDeTexto extends PDFTextStripper {

        public MeuRemovedorDeTexto() throws IOException {
            super();
        }

        @Override
        protected void writeString(
                String texto,
                List<TextPosition> posicoes
        ) throws IOException {

            for (TextPosition textPosition : posicoes) {

                float y = textPosition.getYDirAdj();

                System.out.println(
                        textPosition.getUnicode() + " | Y = " + y
                );
            }

            super.writeString(texto, posicoes);
        }
    }
}
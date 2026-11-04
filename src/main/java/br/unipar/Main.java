package br.unipar;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import br.unipar.model.CelulaPDF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;


import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import java.io.FileOutputStream;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        //aqui eu estou pegando o arquivo pdf que eu quero converter
        JFileChooser seletor = new JFileChooser();

        seletor.setDialogTitle("Selecione o PDF que deseja converter");

        seletor.setFileFilter(
                new FileNameExtensionFilter("Arquivos PDF", "pdf")
        );
        int resultado = seletor.showOpenDialog(null);

        if (resultado != JFileChooser.APPROVE_OPTION) {
            System.out.println("Nenhum arquivo foi selecionado.");
            return;
        }

        File arquivo = seletor.getSelectedFile();

        System.out.println("Arquivo selecionado: " + arquivo.getAbsolutePath());

        //aqui eu irei usar o Loader para ler o arquivo, e já verifico se ele é pdf com try
        try(PDDocument documentoPDF = Loader.loadPDF(arquivo)){
            // o PDFTextStripper eu uso para retirar o texto do pdf,
            // IMPORTANTE: se a escrita estiver numa imagem ele NÃO consegue ler
            MeuRemovedorDeTexto removedorDeTexto = new MeuRemovedorDeTexto();

            //aqui ele vai tentar ordenar o texto da esquerda para a direita e de cima para baixo
            removedorDeTexto.setSortByPosition(true);

            // remove o texto
            String textoRemovido = removedorDeTexto.getText(documentoPDF);
            for (int i = 0; i < removedorDeTexto.getLinhas().size(); i++) {

                System.out.println(
                        "Textos: " + removedorDeTexto.getLinhas().get(i)
                );

                System.out.println(
                        "Posições: " + removedorDeTexto.getLinhasDePosicoes().get(i)
                );
            }
            for (ArrayList<String> linha : removedorDeTexto.getLinhas()) {
                System.out.println(linha);
            }
            ArrayList<Float> colunas = removedorDeTexto.descobrirColunas();

            System.out.println("Colunas encontradas: " + colunas);

            System.out.println("Quantidade de colunas: " + colunas.size());

            System.out.println("LINHAS ORGANIZADAS:");

            for (ArrayList<String> linha : removedorDeTexto.organizarLinhas()) {
                System.out.println(linha);
            }

            try (XSSFWorkbook excel = new XSSFWorkbook()) {

                Sheet aba = excel.createSheet("Dados do PDF");

                ArrayList<ArrayList<String>> linhasOrganizadas = removedorDeTexto.organizarLinhas();

                for (int i = 0; i < linhasOrganizadas.size(); i++) {

                    ArrayList<String> dadosLinha = linhasOrganizadas.get(i);

                    Row linhaExcel = aba.createRow(i);

                    for (int j = 0; j < dadosLinha.size(); j++) {

                        Cell celulaExcel = linhaExcel.createCell(j);

                        String valor = dadosLinha.get(j).trim();

                        if (verificardorDeNumero(valor)) {

                            double numero = Double.parseDouble(valor.replace(",", "."));

                            celulaExcel.setCellValue(numero);

                        } else {

                            celulaExcel.setCellValue(valor);
                        }
                    }
                }
                // Ajusta automaticamente a largura das colunas
                ArrayList<Float> larguras = removedorDeTexto.calcularLargurasCelulas();

                for (int i = 0; i < larguras.size(); i++) {

                    float larguraPDF = larguras.get(i);

                    int larguraExcel = (int) (larguraPDF * 256 / 7);

                    aba.setColumnWidth(i, Math.min(larguraExcel, 255 * 256));
                }
                // Salva o arquivo Excel
                JFileChooser seletorSalvar = new JFileChooser();

                seletorSalvar.setDialogTitle("Escolha onde salvar o Excel");

                seletorSalvar.setSelectedFile(new File("convertido.xlsx"));

                seletorSalvar.setFileFilter(
                        new FileNameExtensionFilter("Planilhas Excel", "xlsx")
                );

                int resultadoSalvar = seletorSalvar.showSaveDialog(null);

                if (resultadoSalvar != JFileChooser.APPROVE_OPTION) {
                    System.out.println("Salvamento cancelado.");
                    return;
                }

                File arquivoExcel = seletorSalvar.getSelectedFile();

                if (!arquivoExcel.getName().toLowerCase().endsWith(".xlsx")) {
                    arquivoExcel = new File(arquivoExcel.getAbsolutePath() + ".xlsx");
                }

                try (FileOutputStream saida = new FileOutputStream(arquivoExcel)) {
                    excel.write(saida);
                }

                System.out.println("Excel salvo em: " + arquivoExcel.getAbsolutePath());
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
        private ArrayList<Float> posicoesDasCelulas = new ArrayList<>();
        private ArrayList<ArrayList<Float>> linhasDePosicoes = new ArrayList<>();
        private float xInicioCelula = -1;
        private ArrayList<CelulaPDF> celulasPDF = new ArrayList<>();


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

            int paginaAtual = getCurrentPageNo();

            System.out.println("Processando texto da página: " + paginaAtual);

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


                if (celula.length() == 0) {
                    xInicioCelula = x;
                }
                celula.append(caractere);// Adiciona o caractere à célula atual

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
                posicoesDasCelulas.add(xInicioCelula);

                celula.setLength(0);
                xInicioCelula = -1;
            }

        }

        private void finalizarLinha() {

            finalizarCelula();

            if (!tabelaDeCelulas.isEmpty()) {

                tabelaLinhaDeCelulas.add(new ArrayList<>(tabelaDeCelulas));

                linhasDePosicoes.add(new ArrayList<>(posicoesDasCelulas));

                tabelaDeCelulas.clear();
                posicoesDasCelulas.clear();
            }
        }

        public ArrayList<ArrayList<String>> getLinhas() {
            return tabelaLinhaDeCelulas;
        }

        public ArrayList<Float> descobrirColunas() {

            ArrayList<Float> colunas = new ArrayList<>();

            float tolerancia = 10;

            for (ArrayList<Float> linha : linhasDePosicoes) {

                for (float x : linha) {

                    boolean encontrouColuna = false;

                    for (float coluna : colunas) {

                        if (Math.abs(x - coluna) <= tolerancia) {
                            encontrouColuna = true;
                            break;
                        }
                    }

                    if (!encontrouColuna) {
                        colunas.add(x);
                    }
                }
            }

            colunas.sort(Float::compareTo);

            return colunas;
        }

        public ArrayList<ArrayList<String>> organizarLinhas() {

            ArrayList<Float> colunas = descobrirColunas();

            ArrayList<ArrayList<String>> linhasOrganizadas = new ArrayList<>();

            for (int i = 0; i < tabelaLinhaDeCelulas.size(); i++) {

                ArrayList<String> textos = tabelaLinhaDeCelulas.get(i);

                ArrayList<Float> posicoes = linhasDePosicoes.get(i);

                ArrayList<String> novaLinha = new ArrayList<>();

                for (int j = 0; j < colunas.size(); j++) {
                    novaLinha.add("");
                }

                for (int j = 0; j < textos.size(); j++) {

                    float x = posicoes.get(j);

                    int colunaMaisProxima = -1;

                    float menorDistancia = Float.MAX_VALUE;

                    for (int k = 0; k < colunas.size(); k++) {

                        float distancia = Math.abs(x - colunas.get(k));

                        if (distancia < menorDistancia) {

                            menorDistancia = distancia;

                            colunaMaisProxima = k;
                        }
                    }

                    if (colunaMaisProxima != -1) {
                        novaLinha.set(colunaMaisProxima, textos.get(j));
                    }
                }

                linhasOrganizadas.add(novaLinha);
            }

            return linhasOrganizadas;
        }

        public ArrayList<ArrayList<Float>> getLinhasDePosicoes() {
            return linhasDePosicoes;
        }

        public ArrayList<Float> calcularLargurasCelulas() {

            ArrayList<Float> colunas = descobrirColunas();

            ArrayList<Float> larguras = new ArrayList<>();

            for (int i = 0; i < colunas.size() - 1; i++) {

                float largura = colunas.get(i + 1) - colunas.get(i);

                larguras.add(largura);
            }

            if (!larguras.isEmpty()) {
                larguras.add(larguras.get(larguras.size() - 1));
            } else if (!colunas.isEmpty()) {
                larguras.add(60f);
            }

            return larguras;
        }
        public ArrayList<CelulaPDF> getCelulasPDF() {
            return celulasPDF;
        }
    }

    private static boolean verificardorDeNumero(String texto) {

        if (!texto.matches("[+-]?\\d+([.,]\\d+)?")) {
            return false;
        }

        try {
            Double.parseDouble(texto.replace(",", "."));
            return true;

        } catch (NumberFormatException e) {
            return false;
        }
    }



}//teste linha 69

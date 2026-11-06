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

            ArrayList<CelulaPDF> blocos = removedorDeTexto.agruparEmBlocos();

            try (XSSFWorkbook excel = new XSSFWorkbook()) {

                Sheet aba = excel.createSheet("Dados do PDF");
                gerarExcelLayout(blocos, aba);

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
        private ArrayList<CelulaPDF> caracteresPDF = new ArrayList<>();

        private float yInicioCelula = -1;
        private float xFimCelula = -1;
        private float alturaCelula = 0;
        private int paginaCelula = 1;


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

                // Guarda o caractere com sua posição original
                CelulaPDF novoCaractere = new CelulaPDF(
                        caractere,
                        textPosition.getXDirAdj(),
                        textPosition.getYDirAdj(),
                        textPosition.getWidthDirAdj(),
                        textPosition.getHeightDir(),
                        getCurrentPageNo()
                );

                caracteresPDF.add(novoCaractere);

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

                    if (distancia > 10 || distancia < -2) {
//                        System.out.println("Célula: " + celula);
                        finalizarCelula();

                    }
                }


                if (celula.length() == 0) {

                    xInicioCelula = x;
                    yInicioCelula = yAtual;
                    paginaCelula = getCurrentPageNo();

                    alturaCelula = 0;
                    xFimCelula = x;
                }

                celula.append(caractere);// Adiciona o caractere à célula atual

                xFimCelula = Math.max(xFimCelula, x + largura);

                alturaCelula = Math.max(
                        alturaCelula,
                        textPosition.getHeightDir()
                );

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

                // Calcula a largura ocupada pelo texto
                float larguraTotal = xFimCelula - xInicioCelula;

                // Cria o objeto com todas as informações
                CelulaPDF novaCelula = new CelulaPDF(
                    celula.toString(),
                    xInicioCelula,
                    yInicioCelula,
                    larguraTotal,
                    alturaCelula,
                    paginaCelula
                );

                // Guardo o objeto na lista
                celulasPDF.add(novaCelula);

                // Limpo as variáveis para a próxima célula
                celula.setLength(0);
                xInicioCelula = -1;
                yInicioCelula = -1;
                xFimCelula = -1;
                alturaCelula = 0;

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
        public ArrayList<ArrayList<CelulaPDF>> agruparPorLinha() {

            ArrayList<ArrayList<CelulaPDF>> linhas = new ArrayList<>();

            float toleranciaY = 2f;

            for (CelulaPDF caractere : caracteresPDF) {

                boolean encontrouLinha = false;

                for (ArrayList<CelulaPDF> linha : linhas) {

                    float yReferencia = linha.get(0).getY();

                    if (Math.abs(caractere.getY() - yReferencia) <= toleranciaY) {

                        linha.add(caractere);
                        encontrouLinha = true;
                        break;
                    }
                }

                if (!encontrouLinha) {

                    ArrayList<CelulaPDF> novaLinha = new ArrayList<>();

                    novaLinha.add(caractere);

                    linhas.add(novaLinha);
                }
            }

            // Organiza as linhas por página e posição vertical
            linhas.sort((linha1, linha2) -> {

                CelulaPDF primeiro1 = linha1.get(0);
                CelulaPDF primeiro2 = linha2.get(0);

                int comparacaoPagina = Integer.compare(
                        primeiro1.getPagina(),
                        primeiro2.getPagina()
                );

                if (comparacaoPagina != 0) {
                    return comparacaoPagina;
                }

                return Float.compare(primeiro1.getY(), primeiro2.getY());
            });

            // Organiza os elementos de cada linha da esquerda para a direita
            for (ArrayList<CelulaPDF> linha : linhas) {

                linha.sort((a, b) ->
                        Float.compare(a.getX(), b.getX())
                );
            }

            return linhas;
        }

        public ArrayList<CelulaPDF> agruparEmBlocos() {

            ArrayList<CelulaPDF> blocos = new ArrayList<>();

            ArrayList<ArrayList<CelulaPDF>> linhas = agruparPorLinha();

            float toleranciaEspaco = 10f;

            for (ArrayList<CelulaPDF> linha : linhas) {

                StringBuilder textoBloco = new StringBuilder();

                float xInicio = -1;
                float xFim = -1;
                float y = -1;
                float altura = 0;
                int pagina = 1;

                for (CelulaPDF caractere : linha) {

                    float xAtual = caractere.getX();

                    if (textoBloco.length() > 0) {

                        float distancia = xAtual - xFim;

                        if (distancia > toleranciaEspaco) {

                            blocos.add(new CelulaPDF(
                                    textoBloco.toString(),
                                    xInicio,
                                    y,
                                    xFim - xInicio,
                                    altura,
                                    pagina
                            ));

                            textoBloco.setLength(0);
                        }
                    }

                    if (textoBloco.length() == 0) {
                        xInicio = xAtual;
                        y = caractere.getY();
                        altura = 0;
                        pagina = caractere.getPagina();
                        xFim = xAtual;
                    }

                    textoBloco.append(caractere.getTexto());

                    xFim = Math.max(
                        xFim,
                        xAtual + caractere.getLargura()
                    );

                    altura = Math.max(altura, caractere.getAltura());
                }

                if (textoBloco.length() > 0) {

                    blocos.add(new CelulaPDF(
                        textoBloco.toString(),
                        xInicio,
                        y,
                        xFim - xInicio,
                        altura,
                        pagina
                    ));
                }
            }

            return blocos;
        }

        public ArrayList<ArrayList<Float>> getLinhasDePosicoes() {
            return linhasDePosicoes;
        }
        public ArrayList<CelulaPDF> getCelulasPDF() {
            return celulasPDF;
        }
        public ArrayList<CelulaPDF> getCaracteresPDF() { return caracteresPDF; }
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

    public static void gerarExcelLayout(
            ArrayList<CelulaPDF> blocos,
            Sheet aba) {

        float larguraColuna = 10f;
        float alturaLinha = 14f;

        // Ajusta a largura das colunas para aproximadamente 10 pontos
        int larguraExcel = (int) ((larguraColuna / 7f) * 256);

        for (int i = 0; i < 100; i++) {
            aba.setColumnWidth(i, larguraExcel);
        }

        // Ajusta a altura das linhas para 14 pontos
        aba.setDefaultRowHeightInPoints(alturaLinha);

        for (CelulaPDF bloco : blocos) {

            int coluna = (int) (bloco.getX() / larguraColuna);
            int linha = (int) (bloco.getY() / alturaLinha);

            Row linhaExcel = aba.getRow(linha);

            if (linhaExcel == null) {
                linhaExcel = aba.createRow(linha);
            }

            Cell celulaExcel = linhaExcel.getCell(coluna);

            if (celulaExcel == null) {
                celulaExcel = linhaExcel.createCell(coluna);
            }

            String texto = bloco.getTexto().trim();

            if (celulaExcel.getStringCellValue().isEmpty()) {
                celulaExcel.setCellValue(texto);
            } else {
                celulaExcel.setCellValue(
                        celulaExcel.getStringCellValue() + " " + texto
                );
            }
        }
    }



}//teste linha 69

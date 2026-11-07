package br.unipar;

import lombok.NonNull;
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
            ArrayList<Float> colunasLayout = removedorDeTexto.descobrirColunasLayout();

            try (XSSFWorkbook excel = new XSSFWorkbook()) {

                Sheet aba = excel.createSheet("Dados do PDF");
                gerarExcelLayout(blocos, colunasLayout, aba);

                ajustarLarguraColunas(aba, colunasLayout, blocos);

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


        private ArrayList<CelulaPDF> caracteresPDF = new ArrayList<>();

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
            }
            super.writeString(texto, posicoes);
        }

        public ArrayList<ArrayList<CelulaPDF>> agruparPorLinha() {

            ArrayList<ArrayList<CelulaPDF>> linhas = new ArrayList<>();

            float toleranciaY = 2f;

            for (CelulaPDF caractere : caracteresPDF) {

                boolean encontrouLinha = false;

                for (ArrayList<CelulaPDF> linha : linhas) {

                    float yReferencia = linha.get(0).getY();

                    if (linha.get(0).getPagina() == caractere.getPagina()
                            && Math.abs(caractere.getY() - yReferencia) <= toleranciaY) {

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
        public ArrayList<Float> descobrirColunasLayout() {

            ArrayList<CelulaPDF> blocos = agruparEmBlocos();
            ArrayList<Float> colunas = new ArrayList<>();

            float toleranciaX = 15f;

            for (CelulaPDF bloco : blocos) {

                float xAtual = bloco.getX();
                boolean colunaEncontrada = false;

                for (Float xColuna : colunas) {

                    if (Math.abs(xAtual - xColuna) <= toleranciaX) {
                        colunaEncontrada = true;
                        break;
                    }
                }

                if (!colunaEncontrada) {
                    colunas.add(xAtual);
                }
            }

            colunas.sort(Float::compareTo);

            return colunas;
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

    private static int colunaMaisProxima(float x, ArrayList<Float> colunas) {
        int melhor = 0;
        float menor = Float.MAX_VALUE;
        for (int i = 0; i < colunas.size(); i++) {
            float d = Math.abs(x - colunas.get(i));
            if (d < menor) { menor = d; melhor = i; }
        }
        return melhor;
    }

    // largura de cada coluna proporcional à distância real no PDF
    private static void ajustarLarguraColunas(Sheet aba, ArrayList<Float> colunas,
                                              ArrayList<CelulaPDF> blocos) {
        for (int i = 0; i < colunas.size(); i++) {
            float larguraPt;
            if (i < colunas.size() - 1) {
                larguraPt = colunas.get(i + 1) - colunas.get(i);
            } else {
                // última coluna: pega o bloco mais largo que começa nela
                larguraPt = 40f;
                for (CelulaPDF b : blocos) {
                    if (colunaMaisProxima(b.getX(), colunas) == i) {
                        larguraPt = Math.max(larguraPt, b.getLargura());
                    }
                }
            }
            // pt -> pixels (96/72) -> caracteres do Excel (~7 px cada)
            double caracteres = (larguraPt * 96.0 / 72.0) / 7.0;
            int largura = (int) Math.min(255 * 256, Math.max(2 * 256, caracteres * 256));
            aba.setColumnWidth(i, largura);
        }
    }

    public static void gerarExcelLayout(ArrayList<CelulaPDF> blocos,
                                        ArrayList<Float> colunasLayout, Sheet aba) {
        ArrayList<CelulaPDF> ordenados = new ArrayList<>(blocos);
        ordenados.sort((a, b) -> {
            int p = Integer.compare(a.getPagina(), b.getPagina());
            return p != 0 ? p : Float.compare(a.getY(), b.getY());
        });

        int linhaAtual = -1;
        float yLinha = -1;
        int paginaAnterior = -1;

        for (CelulaPDF bloco : ordenados) {
            boolean novaPagina = bloco.getPagina() != paginaAnterior;
            boolean novaLinha = novaPagina || Math.abs(bloco.getY() - yLinha) > 3f;

            if (novaLinha) {
                if (linhaAtual == -1) {
                    linhaAtual = 0;
                } else if (novaPagina) {
                    linhaAtual += 2; // uma linha em branco entre páginas
                } else {
                    // 1 linha normal + linhas em branco proporcionais ao vão do PDF
                    float vao = bloco.getY() - yLinha;
                    linhaAtual += Math.max(1, Math.round(vao / 14f));
                }
                yLinha = bloco.getY();
                paginaAnterior = bloco.getPagina();
            }

            int coluna = colunaMaisProxima(bloco.getX(), colunasLayout);

            Row linhaExcel = aba.getRow(linhaAtual);
            if (linhaExcel == null) linhaExcel = aba.createRow(linhaAtual);

            Cell celula = linhaExcel.getCell(coluna);
            if (celula == null) celula = linhaExcel.createCell(coluna);

            // tira espaços repetidos (ex.: "R$            404,00")
            String texto = bloco.getTexto().trim().replaceAll("\\s+", " ");

            if (celula.getStringCellValue().isEmpty()) {
                celula.setCellValue(texto);
            } else {
                celula.setCellValue(celula.getStringCellValue() + " " + texto);
            }
        }
    }



}//teste linha 69

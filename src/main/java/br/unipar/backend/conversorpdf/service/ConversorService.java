package br.unipar.backend.conversorpdf.service;

import br.unipar.backend.conversorpdf.dtos.ExcelGeradoDTO;
import br.unipar.backend.conversorpdf.exception.ArquivoInvalidoException;
import br.unipar.backend.conversorpdf.model.CelulaPDF;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;


@Service
public class ConversorService {
    // Extensão que o arquivo enviado precisa ter. Fica numa constante para não repetir o texto ".pdf".
    private static final String EXTENSAO_PDF = ".pdf";
    // aqui ele vai recebe o PDF enviado pelo usuário e devolve o Excel pronto.
    public ExcelGeradoDTO converterPdfParaExcel(MultipartFile arquivo) {

        // confere se o arquivo existe e se é um .pdf.
        // Se não servir, lança ArquivoInvalidoException e o metodo para por aqui.
        validar(arquivo);
        try (PDDocument documentoPDF = Loader.loadPDF(arquivo.getBytes())) {
            // Cria um extrator novo a cada pedido: ele guarda os caracteres do PDF que está sendo lido,
            // então não pode ser compartilhado entre duas conversões ao mesmo tempo.
            ExtratorDeTexto extrator = new ExtratorDeTexto();

            // O texto que getText devolve não é usado, mas sem ela, o PDF nunca é percorrido e o Excel sairia vazio.
            extrator.getText(documentoPDF);

            // aqui ele vai juntar os caracteres guardados em blocos de texto entao "o" "l" "l" vira ola.
            ArrayList<CelulaPDF> blocos = extrator.agruparEmBlocos();

            // Se não existe nenhum bloco, o PDF não tem texto: provavelmente é uma foto/scan
            // (o texto está dentro de uma imagem, que o PDFBox não consegue ler).
            if (blocos.isEmpty()) {
                throw new ArquivoInvalidoException(
                    "Não encontrei texto neste PDF. Se ele for uma imagem/scan, lamento, " +
                    "nao posso ajudar no momento, espere pelas proximas atualizações.");
            }
            // Descobre as colunas do layout: as posições X (esquerda para a direita)
            // onde os blocos de texto costumam começar.
            ArrayList<Float> colunas = extrator.descobrirColunasLayout(blocos);
            // Monta o Excel na memória e devolve o arquivo como um vetor de bytes.
            byte[] conteudo = gerarExcel(blocos, colunas);

            // Empacota nome + conteúdo no DTO para o Controller devolver ao usuário.
            return new ExcelGeradoDTO(gerarNomeSaida(arquivo.getOriginalFilename()), conteudo);

        } catch (IOException e) {
            // Loader.loadPDF e getBytes lançam IOException quando o arquivo está corrompido,
            // não é um PDF de verdade ou tem senha.
            throw new ArquivoInvalidoException(
                "Não foi possível processar o PDF. Verifique se o arquivo é um PDF válido e sem senha."
            );
        }
    }

    // Confere se o upload é aceitável. Se não for, lança ArquivoInvalidoException com o motivo.
    private void validar(MultipartFile arquivo) {

        // nenhum arquivo enviado (campo ausente ou arquivo com 0 bytes).
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Envie um arquivo PDF no campo 'arquivo'.");
        }

        // Nome que o arquivo tinha na máquina do usuário
        String nome = arquivo.getOriginalFilename();

        //sem nome, ou o nome não termina em ".pdf".
        // toLowerCase() faz "ARQUIVO.PDF" também ser aceito.
        if (nome == null || !nome.toLowerCase().endsWith(EXTENSAO_PDF)) {
            throw new ArquivoInvalidoException("O arquivo precisa ter a extensão .pdf");
        }
    }

    // Cria o nome do Excel a partir do nome do PDF, trocando a extensão.
    // Ex: "EuLindao.pdf" -> "EuLindao.xlsx"
    private String gerarNomeSaida(String nomeOriginal) {

        // substring(0, tamanho - 4) corta fora os últimos 4 caracteres (o ".pdf").
        String semExtensao = nomeOriginal.substring(0, nomeOriginal.length() - EXTENSAO_PDF.length());
        return semExtensao + ".xlsx";
    }

    // Cria a planilha inteira na memória e devolve o arquivo .xlsx como bytes.

    private byte[] gerarExcel(ArrayList<CelulaPDF> blocos, ArrayList<Float> colunas) throws IOException {

        // Os dois objetos abaixo são fechados sozinhos no final do bloco:
        //  - excel: o arquivo Excel (.xlsx) que está sendo montado;
        //  - saida: um arquivo na memória que recebe os bytes do arquivo quando ele é gravado.
        try (XSSFWorkbook excel = new XSSFWorkbook();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {

            // Cria a aba com este nome.
            Sheet aba = excel.createSheet("Dados do PDF");

            // primeiro escreve os textos nas linhas e colunas certas
            gerarExcelLayout(blocos, colunas, aba);

            //aqui vai ajusta a largura das colunas para ficar parecido com o PDF.
            ajustarLarguraColunas(aba, colunas, blocos);

            // Grava o Excel dentro do arquivo e devolve o conteúdo dele como vetor de bytes.
            excel.write(saida);
            return saida.toByteArray();
        }
    }

    // Descobre em qual coluna um bloco de texto deve ficar: devolve o índice
    // da coluna cuja posição X está mais perto do X do bloco.
    private static int colunaMaisProxima(float x, ArrayList<Float> colunas) {

        int melhor = 0;
        float menor = Float.MAX_VALUE;

        // Testa todas as colunas, guardando a que ficou mais perto.
        for (int i = 0; i < colunas.size(); i++) {

            // Distância entre o bloco e a coluna i .
            float d = Math.abs(x - colunas.get(i));

            // Achou uma coluna mais perto do que a melhor até agora: passa a ser a melhor.
            if (d < menor) {
                menor = d;
                melhor = i;
            }
        }
        return melhor;
    }

    // Define a largura de cada coluna do Excel proporcional ao espaço que ela ocupa no PDF.
    // Sem isso, todas as colunas teriam a mesma largura e o Excel ficaria muito mais largo que o PDF.
    private static void ajustarLarguraColunas(Sheet aba, ArrayList<Float> colunas,
                                              ArrayList<CelulaPDF> blocos) {

        // Repete para cada coluna, uma de cada vez.
        for (int i = 0; i < colunas.size(); i++) {

            // Largura da coluna no PDF, em pontos.
            float larguraPt;

            if (i < colunas.size() - 1) {
                // quando a colunao n é a ultina, a largura é a distância até o começo da próxima coluna.
                larguraPt = colunas.get(i + 1) - colunas.get(i);
            } else {
                // Última coluna: não existe "próxima" para medir a distância.
                // Então usamos o maior bloco de texto que começa nela.
                larguraPt = 40f;
                for (CelulaPDF b : blocos) {

                    // Só interessa o bloco que pertence a esta coluna.
                    if (colunaMaisProxima(b.getX(), colunas) == i) {
                        larguraPt = Math.max(larguraPt, b.getLargura());
                    }
                }
            }

            //isso é ia, n tava conseguindo chegar nessa conta, nem pensar em algo parecido
            // Converte pontos do PDF para a unidade do Excel, em duas etapas:
            //  1) pontos -> pixels: multiplica por 96/72 (a tela tem 96 pixels por polegada, o ponto é 1/72);
            //  2) pixels -> "caracteres": divide por 7, que é mais ou menos a largura de um caractere no Excel.
            double caracteres = (larguraPt * 96.0 / 72.0) / 7.0;

            // O Excel guarda a largura em 1/256 de caractere, por isso multiplicamos por 256.
            // Math.max(2 * 256, ...) garante pelo menos 2 caracteres de largura (coluna nunca some);
            // Math.min(255 * 256, ...) respeita o limite do Excel, de 255 caracteres.
            int largura = (int) Math.min(255 * 256, Math.max(2 * 256, caracteres * 256));

            // Aplica a largura na coluna i da aba.
            aba.setColumnWidth(i, largura);
        }
    }

    // Escreve cada bloco de texto na linha e na coluna certas da planilha
    // A coluna vem da posição X do bloco; a linha vem da posição Y
    private static void gerarExcelLayout(ArrayList<CelulaPDF> blocos,
                                         ArrayList<Float> colunasLayout, Sheet aba) {

        // Trabalha numa cópia da lista, para não bagunçar a ordem da lista original.
        // Depois ordena por página e, dentro da mesma página, de cima para baixo.
        // Em "p != 0 ? p : ...": se as páginas forem diferentes, a página decide; se forem iguais, o Y decide.
        ArrayList<CelulaPDF> ordenados = new ArrayList<>(blocos);
        ordenados.sort((a, b) -> {
            int p = Integer.compare(a.getPagina(), b.getPagina());
            return p != 0 ? p : Float.compare(a.getY(), b.getY());
        });

        int linhaAtual = -1;
        float yLinha = -1;
        int paginaAnterior = -1;

        // Percorre os blocos já na ordem de leitura .
        for (CelulaPDF bloco : ordenados) {

            // É o primeiro bloco de uma página nova?
            boolean novaPagina = bloco.getPagina() != paginaAnterior;

            // Este bloco pertence a outra linha do Excel se mudou a página
            // ou se o Y dele está a mais de 3 pontos do Y da linha atual

            boolean novaLinha = novaPagina || Math.abs(bloco.getY() - yLinha) > 3f;

            if (novaLinha) {
                if (linhaAtual == -1) {
                    // Primeiro bloco do PDF
                    linhaAtual = 0;
                } else if (novaPagina) {
                    // Mudou de página: pula 2 linhas, o que deixa uma linha em branco separando as páginas.
                    linhaAtual += 2;
                } else {
                    // Mesma página, linha nova: desce pelo menos 1 linha no Excel.
                    // "vao" é a distância vertical (em pontos) entre esta linha e a anterior.
                    // Dividindo por 14 (altura média de uma linha de texto) sabemos quantas linhas
                    // cabem nesse espaço, então um espaço grande no PDF vira linhas em branco no Excel.
                    float vao = bloco.getY() - yLinha;
                    linhaAtual += Math.max(1, Math.round(vao / 14f));
                }

                // Guarda a posição Y e a página desta linha para comparar com os próximos blocos.
                yLinha = bloco.getY();
                paginaAnterior = bloco.getPagina();
            }

            // Descobre a coluna do Excel olhando a posição X do bloco.
            int coluna = colunaMaisProxima(bloco.getX(), colunasLayout);

            // Pega a linha do Excel; se ela ainda não existe, cria.
            Row linhaExcel = aba.getRow(linhaAtual);
            if (linhaExcel == null) linhaExcel = aba.createRow(linhaAtual);

            // Pega a célula e se ainda não existe, cria.
            Cell celula = linhaExcel.getCell(coluna);
            if (celula == null) celula = linhaExcel.createCell(coluna);


            String texto = bloco.getTexto().trim().replaceAll("\\s+", " ");

            if (celula.getStringCellValue().isEmpty()) {
                // Célula vazia: só escreve o texto.
                celula.setCellValue(texto);
            } else {
                // se ja tem texto nesta celula :
                // junta os dois com um espaço, em vez de apagar o que já estava lá.
                celula.setCellValue(celula.getStringCellValue() + " " + texto);
            }
        }
    }
}

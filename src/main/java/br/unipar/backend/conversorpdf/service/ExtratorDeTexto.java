package br.unipar.backend.conversorpdf.service;

import br.unipar.backend.conversorpdf.model.CelulaPDF;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Esta classe lê o PDF e transforma o texto dele em "blocos" com posição X, Y.
// Ela herda de PDFTextStripper, a classe do PDFBox que percorre o PDF e extrai o texto.
public class ExtratorDeTexto extends PDFTextStripper {


    private final ArrayList<CelulaPDF> caracteresPDF = new ArrayList<>();

    // Construtor: roda quando fazemos "new ExtratorDeTexto()".
    // Tem "throws IOException" porque o construtor da classe mãe (PDFTextStripper) pode lançar esse erro.
    public ExtratorDeTexto() throws IOException {
        super();

        // false = o PDFBox lê os caracteres na mesma ordem em que o PDF os escreveu.
        // Com true ele reordenaria os caracteres por posição, e quando dois ficam a frações de ponto
        // um do outro
        setSortByPosition(false);
    }


    @Override
    protected void writeString(String texto, List<TextPosition> posicoes) throws IOException {

        // Percorre os caracteres do trecho, um de cada vez.
        for (TextPosition textPosition : posicoes) {


            caracteresPDF.add(new CelulaPDF(
                    textPosition.getUnicode(),
                    textPosition.getXDirAdj(),
                    textPosition.getYDirAdj(),
                    textPosition.getWidthDirAdj(),
                    textPosition.getHeightDir(),
                    getCurrentPageNo()
            ));
        }

        // Chama o metodo original da classe mãe, para o PDFBox seguir o trabalho normal dele.
        super.writeString(texto, posicoes);
    }

    // Aqui ele junta os caracteres que estão na mesma altura (e na mesma página) numa linha.
    // Devolve uma lista de linhas, e cada linha é uma lista de caracteres.
    public ArrayList<ArrayList<CelulaPDF>> agruparPorLinha() {

        ArrayList<ArrayList<CelulaPDF>> linhas = new ArrayList<>();

        // caracteres com até 2 pontos de diferença no Y
        // são considerados da mesma linha. Não pode ser exatamente igual porque letras da mesma
        // linha visual podem ter o Y um pouco diferente
        float toleranciaY = 2f;

        // Esse for recebe cada caracter do pdf, um de cada vez, e analiza a qual linha pertence
        for (CelulaPDF caractere : caracteresPDF) {

            // Começa assumindo que este caractere ainda não achou uma linha.
            boolean encontrouLinha = false;

            // Testa as linhas que já foram criadas, uma por uma.
            for (ArrayList<CelulaPDF> linha : linhas) {

                // O Y do primeiro caractere da linha é a referência pra poder comparar.
                float yReferencia = linha.get(0).getY();


                //  o primeiro verifica se está na mesma página que a linha assim se tiver uma segunda pagina eles não se misturam;
                //  o segundo verifica se a diferença de altura (Math.abs tira o sinal) é de até 2 pontos.
                if (linha.get(0).getPagina() == caractere.getPagina()
                        && Math.abs(caractere.getY() - yReferencia) <= toleranciaY) {

                    // coloca o caractere nesta linha, marca que achou
                    // e para de procurar (break), porque não precisa testar as outras linhas.
                    linha.add(caractere);
                    encontrouLinha = true;
                    break;
                }
            }

            // Se passou por todas as linhas e nenhuma serviu, este caractere é o primeiro de uma linha nova:
            // cria uma lista só com ele e adiciona às linhas.
            if (!encontrouLinha) {

                ArrayList<CelulaPDF> novaLinha = new ArrayList<>();
                novaLinha.add(caractere);
                linhas.add(novaLinha);
            }
        }

        // Coloca as linhas na ordem de leitura: página 1 de cima para baixo, depois página 2, e assim por diante.
        // Isso é necessário porque as linhas foram criadas na ordem em que o PDF escreveu os caracteres,
        // e essa ordem nem sempre é de cima para baixo.

        linhas.sort((linha1, linha2) -> {

            // Cada linha é representada pelo seu primeiro caractere.
            CelulaPDF primeiro1 = linha1.get(0);
            CelulaPDF primeiro2 = linha2.get(0);

            // Compara as páginas: negativo se a página da linha1 é menor, positivo se é maior, 0 se é a mesma.
            int comparacaoPagina = Integer.compare(
                    primeiro1.getPagina(),
                    primeiro2.getPagina()
            );

            // Se as páginas são diferentes, a página já decide quem vem primeiro e terminamos aqui.
            if (comparacaoPagina != 0) {
                return comparacaoPagina;
            }

            // Se chegou aqui, as duas linhas estão na mesma página, então desempata pela altura:
            // o menor Y vem primeiro.
            return Float.compare(primeiro1.getY(), primeiro2.getY());
        });

        return linhas;
    }

    // dentro de cada linha, junta os caracteres próximos num "bloco" de texto.
    // Quando o espaço até o próximo caractere passa de 10, começa outro bloco
    public ArrayList<CelulaPDF> agruparEmBlocos() {

        // Lista que vai guardar todos os blocos encontrados.
        ArrayList<CelulaPDF> blocos = new ArrayList<>();

        // Pega as linhas já montadas e ordenadas
        ArrayList<ArrayList<CelulaPDF>> linhas = agruparPorLinha();

        // Se o vazio entre dois caracteres for maior que 10 pontos, são blocos diferentes.
        float toleranciaEspaco = 10f;

        // Trata uma linha de cada vez.
        for (ArrayList<CelulaPDF> linha : linhas) {


            StringBuilder textoBloco = new StringBuilder();

            float xInicio = -1;
            float xFim = -1;
            float y = -1;
            float altura = 0;
            int pagina = 1;

            // Percorre os caracteres da linha, na ordem em que foram lidos.
            for (CelulaPDF caractere : linha) {

                float xAtual = caractere.getX();

                // Só faz sentido medir o espaço se já existe um bloco em andamento.
                if (textoBloco.length() > 0) {

                    // Espaço entre o fim do bloco e o começo deste caractere.
                    float distancia = xAtual - xFim;

                    // distancia > 10  -> buraco grande: é outra coluna;
                    // distancia < -2  -> o X voltou para trás: é outro texto (outra célula).
                    if (distancia > toleranciaEspaco || distancia < -2f) {

                        // Fecha o bloco atual: guarda o texto, onde começou xInicio, o Y,
                        // a largura (xFim - xInicio), a altura e a página.
                        blocos.add(new CelulaPDF(
                                textoBloco.toString(),
                                xInicio,
                                y,
                                xFim - xInicio,
                                altura,
                                pagina
                        ));

                        // Esvazia o texto acumulado para começar um bloco novo.
                        textoBloco.setLength(0);
                    }
                }

                // Se não há bloco em andamento ,
                // este caractere é o primeiro do bloco novo: guarda os dados de partida dele.
                if (textoBloco.length() == 0) {
                    xInicio = xAtual;
                    y = caractere.getY();
                    altura = 0;
                    pagina = caractere.getPagina();
                    xFim = xAtual;
                }

                // Adiciona a letra ao bloco.
                textoBloco.append(caractere.getTexto());

                // Atualiza o fim do bloco: a borda direita deste caractere (X + largura),
                // se ela estiver mais à direita do que o fim que já tínhamos.
                xFim = Math.max(xFim, xAtual + caractere.getLargura());

                // A altura do bloco é a do caractere mais alto dele.
                altura = Math.max(altura, caractere.getAltura());
            }

            // Terminaram os caracteres da linha, mas pode sobrar um bloco ainda aberto
            // o último da linha, que nunca foi fechado dentro do for. Guarda ele também.
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

    // Aqui ele vai descobrir as colunas lógicas, as posições X onde os blocos começam.
    // Blocos a menos de 15 pontos de uma coluna que já existe entram nessa coluna.
    // Os que ficam mais longe criam uma coluna nova.
    // Recebe como parâmetro a lista de blocos que o agruparEmBlocos já montou.
    public ArrayList<Float> descobrirColunasLayout(ArrayList<CelulaPDF> blocos) {

        // Lista com o X de cada coluna encontrada.
        ArrayList<Float> colunas = new ArrayList<>();

        // Blocos com até 15 pontos de diferença no X contam como a mesma coluna.
        float toleranciaX = 15f;

        // Vê cada bloco e decide se ele cai numa coluna que já existe ou cria uma nova.
        for (CelulaPDF bloco : blocos) {

            float xAtual = bloco.getX();
            boolean colunaEncontrada = false;

            // Compara o X do bloco com o X de cada coluna já criada.
            for (Float xColuna : colunas) {

                // Está perto o bastante: ele pertence a esta coluna, então para de procurar.
                if (Math.abs(xAtual - xColuna) <= toleranciaX) {
                    colunaEncontrada = true;
                    break;
                }
            }

            // Nenhuma coluna existente servia: o X deste bloco vira uma coluna nova.
            if (!colunaEncontrada) {
                colunas.add(xAtual);
            }
        }

        // Ordena as colunas da esquerda para a direita.
        // Assim a coluna 0 do Excel é a mais à esquerda do PDF.
        colunas.sort(Float::compareTo);

        return colunas;
    }
}

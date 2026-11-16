package br.unipar.backend.conversorpdf.dtos;


// Aqui o Service entrega o Excel pronto para o Controller, que só precisa devolvê-lo ao usuário.
public record ExcelGeradoDTO(

        // Nome sugerido para o arquivo baixado .
        String nomeArquivo,

        // O arquivo .xlsx inteiro, em bytes. Ele só existe na memória: nada é salvo em disco.
        byte[] conteudo) {
}

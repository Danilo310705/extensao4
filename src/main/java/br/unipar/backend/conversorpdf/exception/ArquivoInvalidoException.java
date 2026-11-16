package br.unipar.backend.conversorpdf.exception;


public class ArquivoInvalidoException extends RuntimeException {

    // Recebe a mensagem do erro e a repassa para a classe mãe .
    // Depois ela pode ser lida de volta com getMessage(), o que o TratadorDeErros faz.
    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}

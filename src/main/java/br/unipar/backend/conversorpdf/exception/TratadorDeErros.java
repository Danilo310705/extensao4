package br.unipar.backend.conversorpdf.exception;

import br.unipar.backend.conversorpdf.dtos.ErroDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;


@RestControllerAdvice
public class TratadorDeErros {


    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErroDTO> tratarArquivoInvalido(ArquivoInvalidoException e) {
        return ResponseEntity.badRequest().body(new ErroDTO(e.getMessage()));
    }

    // Roda quando o upload passa do limite definido no application.properties (20 MB).
    // Responde com status 413 (Payload Too Large = o arquivo enviado é grande demais).
    // Essa exceção é lançada pelo próprio Spring, antes mesmo de chegar no nosso controller.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroDTO> tratarArquivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413)
                .body(new ErroDTO("Arquivo muito grande. O limite é de 20 MB."));
    }
}

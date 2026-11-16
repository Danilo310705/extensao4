package br.unipar.backend.conversorpdf.controller;

import br.unipar.backend.conversorpdf.dtos.ExcelGeradoDTO;
import br.unipar.backend.conversorpdf.service.ConversorService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;


@RestController
@RequestMapping("/conversor")
public class ConversorController {


    private static final MediaType TIPO_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    // O service que faz o trabalho de verdade. "final" = é definido uma vez, no construtor, e não troca mais.
    private final ConversorService conversorService;

    // Construtor com injeção de dependência: o Spring cria o ConversorService
    // e entrega aqui sozinho, sem precisar de escrever "new ConversorService()".
    public ConversorController(ConversorService conversorService) {
        this.conversorService = conversorService;
    }

    // GET http://localhost:8080/conversor/status
    // Serve só para conferir se a API subiu. Responde com um texto simples.
    @GetMapping("/status")
    public String status() {
        return "API ta funfando, hehehe!";
    }

    // POST http://localhost:8080/conversor/pdf-para-excel
    // Recebe o PDF como upload e devolve o .xlsx para download.
    //  - @RequestParam("arquivo"): pega do formulário o campo chamado "arquivo"
    //    (o nome da chave no Postman precisa ser exatamente este);
    @PostMapping(value = "/pdf-para-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> converter(@RequestParam("arquivo") MultipartFile arquivo) {

        // Entrega o PDF ao service, que valida, converte e devolve o Excel pronto.
        // Se o arquivo não servir, o service lança uma exceção e o TratadorDeErros responde com o erro,
        // então o código abaixo nem chega a rodar.
        ExcelGeradoDTO excel = conversorService.converterPdfParaExcel(arquivo);


        ContentDisposition disposicao = ContentDisposition.attachment()
            .filename(excel.nomeArquivo(), StandardCharsets.UTF_8)
            .build();


        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
            .contentType(TIPO_XLSX)
            .body(excel.conteudo());
    }
}

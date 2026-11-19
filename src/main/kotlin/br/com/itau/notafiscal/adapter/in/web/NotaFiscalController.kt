package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.application.port.`in`.EmitirNotaFiscalUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notas-fiscais")
class NotaFiscalController(
    private val emitirNotaFiscal: EmitirNotaFiscalUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun emitir(
        @Valid @RequestBody request: EmitirNotaFiscalRequest,
    ): NotaFiscalResponse = emitirNotaFiscal.emitir(request.paraDominio()).paraResposta()
}

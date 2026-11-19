package br.com.itau.notafiscal.application.usecase

import br.com.itau.notafiscal.application.port.`in`.EmitirNotaFiscalUseCase
import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.Pedido
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class EmitirNotaFiscalService(
    private val calculadoraTributo: CalculadoraTributo,
    private val calculadoraFrete: CalculadoraFrete,
    private val geradorNumeroNota: GeradorNumeroNota,
    private val relogio: Clock,
) : EmitirNotaFiscalUseCase {
    override fun emitir(pedido: Pedido): NotaFiscal {
        val tributacao = calculadoraTributo.calcular(pedido)
        val frete = calculadoraFrete.calcular(pedido)
        return NotaFiscal(
            numero = geradorNumeroNota.proximo(),
            dataEmissao = OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS),
            pedido = pedido,
            tributacao = tributacao,
            frete = frete,
        )
    }
}

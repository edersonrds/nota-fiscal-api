package br.com.itau.notafiscal.adapter.out.metricas

import br.com.itau.notafiscal.application.port.out.MetricasNotaFiscal
import br.com.itau.notafiscal.domain.model.NotaFiscal
import br.com.itau.notafiscal.domain.model.PessoaFisica
import br.com.itau.notafiscal.domain.model.PessoaJuridica
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class MetricasMicrometer(
    private val registro: MeterRegistry,
) : MetricasNotaFiscal {
    override fun notaEmitida(nota: NotaFiscal) {
        val tipoPessoa =
            when (nota.pedido.cliente) {
                is PessoaFisica -> "FISICA"
                is PessoaJuridica -> "JURIDICA"
            }
        registro
            .counter("notafiscal.emitidas", "tipo_pessoa", tipoPessoa, "regiao_entrega", nota.frete.regiao.name)
            .increment()
    }

    override fun emissaoRecusada(codigo: String) {
        registro.counter("notafiscal.recusadas", "codigo", codigo).increment()
    }

    override fun falhaDeNotificacao(sistema: String) {
        registro.counter("notafiscal.notificacoes.falhas", "sistema", sistema).increment()
    }
}

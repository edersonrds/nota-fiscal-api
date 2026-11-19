package br.com.itau.notafiscal.adapter.out.integracao

import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.config.IntegracoesProperties
import br.com.itau.notafiscal.domain.model.ItemTributado
import br.com.itau.notafiscal.domain.model.NotaFiscal
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EstoqueSimulado(
    private val latencias: IntegracoesProperties,
) : SistemaEstoque {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun baixar(
        numeroNota: Long,
        item: ItemTributado,
    ) {
        Thread.sleep(latencias.estoque)
        log.info("Estoque: baixa de {} unidade(s) do item {} pela nota {}", item.item.quantidade, item.item.idItem, numeroNota)
    }
}

@Component
class RegistroSimulado(
    private val latencias: IntegracoesProperties,
) : SistemaRegistro {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun registrar(nota: NotaFiscal) {
        Thread.sleep(latencias.registro)
        log.info("Registro: nota {} do pedido {} registrada", nota.numero, nota.pedido.idPedido)
    }
}

@Component
class EntregaSimulada(
    private val latencias: IntegracoesProperties,
) : SistemaEntrega {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun agendar(nota: NotaFiscal) {
        Thread.sleep(latencias.entrega)
        log.info("Entrega: nota {} agendada para a região {}", nota.numero, nota.frete.regiao)
    }
}

@Component
class FinanceiroSimulado(
    private val latencias: IntegracoesProperties,
) : SistemaFinanceiro {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun lancar(nota: NotaFiscal) {
        Thread.sleep(latencias.financeiro)
        log.info("Financeiro: nota {} lançada com total {}", nota.numero, nota.totalNota)
    }
}

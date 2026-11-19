package br.com.itau.notafiscal.config

import br.com.itau.notafiscal.application.port.`in`.EmitirNotaFiscalUseCase
import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import br.com.itau.notafiscal.application.port.out.NotaFiscalRepository
import br.com.itau.notafiscal.application.port.out.SistemaEntrega
import br.com.itau.notafiscal.application.port.out.SistemaEstoque
import br.com.itau.notafiscal.application.port.out.SistemaFinanceiro
import br.com.itau.notafiscal.application.port.out.SistemaRegistro
import br.com.itau.notafiscal.application.service.NotificadorSistemas
import br.com.itau.notafiscal.application.usecase.EmitirNotaFiscalService
import br.com.itau.notafiscal.domain.service.CalculadoraFrete
import br.com.itau.notafiscal.domain.service.CalculadoraTributo
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FreteProperties::class, IntegracoesProperties::class)
class NotaFiscalConfig {
    @Bean
    fun calculadoraTributo() = CalculadoraTributo()

    @Bean
    fun calculadoraFrete(properties: FreteProperties) = CalculadoraFrete(properties.modo)

    @Bean
    fun relogio(): Clock = Clock.system(ZoneId.of("America/Sao_Paulo"))

    @Bean
    fun notificadorSistemas(
        estoque: SistemaEstoque,
        registro: SistemaRegistro,
        entrega: SistemaEntrega,
        financeiro: SistemaFinanceiro,
    ) = NotificadorSistemas(estoque, registro, entrega, financeiro)

    @Bean
    fun emitirNotaFiscalUseCase(
        calculadoraTributo: CalculadoraTributo,
        calculadoraFrete: CalculadoraFrete,
        geradorNumeroNota: GeradorNumeroNota,
        notaFiscalRepository: NotaFiscalRepository,
        notificadorSistemas: NotificadorSistemas,
        relogio: Clock,
    ): EmitirNotaFiscalUseCase =
        EmitirNotaFiscalService(
            calculadoraTributo,
            calculadoraFrete,
            geradorNumeroNota,
            notaFiscalRepository,
            notificadorSistemas,
            relogio,
        )
}

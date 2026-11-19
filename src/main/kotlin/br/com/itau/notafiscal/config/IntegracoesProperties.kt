package br.com.itau.notafiscal.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("nota-fiscal.integracoes.latencia")
data class IntegracoesProperties(
    val estoque: Duration,
    val registro: Duration,
    val entrega: Duration,
    val financeiro: Duration,
)

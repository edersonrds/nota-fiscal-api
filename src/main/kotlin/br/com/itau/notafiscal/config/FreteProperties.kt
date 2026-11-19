package br.com.itau.notafiscal.config

import br.com.itau.notafiscal.domain.model.ModoFrete
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("nota-fiscal.frete")
data class FreteProperties(
    val modo: ModoFrete,
)

package br.com.itau.notafiscal.adapter.out.persistence

import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
class GeradorNumeroNotaPostgres(
    private val jdbc: JdbcClient,
) : GeradorNumeroNota {
    override fun proximo(): Long =
        jdbc
            .sql("select nextval('nota_fiscal_numero_seq')")
            .query(Long::class.javaObjectType)
            .single()
}

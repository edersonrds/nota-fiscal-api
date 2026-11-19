package br.com.itau.notafiscal

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PostgresDeTeste::class)
class NotaFiscalApiApplicationTests {
    @Test
    fun contextLoads() {
    }
}

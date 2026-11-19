package br.com.itau.notafiscal.adapter.`in`.web

import br.com.itau.notafiscal.PostgresDeTeste
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDeTeste::class, NotaFiscalControllerTest.RelogioFixo::class)
class NotaFiscalControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @TestConfiguration
    class RelogioFixo {
        @Bean
        @Primary
        fun relogioFixo(): Clock = Clock.fixed(Instant.parse("2026-11-18T18:30:00Z"), ZoneId.of("America/Sao_Paulo"))
    }

    @Test
    fun `emite a nota do exemplo do contrato`() {
        val corpo =
            mockMvc
                .post("/notas-fiscais") {
                    contentType = MediaType.APPLICATION_JSON
                    content = PEDIDO_DO_CONTRATO
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.numero_nota") { isNumber() }
                    content { json(RESPOSTA_DO_CONTRATO) }
                }.andReturn()
                .response.contentAsString

        assertThat(corpo).contains("\"total_nota\":3532.40", "\"percentual\":4.80", "\"aliquota_percentual\":16.00")
    }

    @Test
    fun `pessoa física que envia regime de tributação tem o regime ignorado`() {
        val corpo =
            mockMvc
                .post("/notas-fiscais") {
                    contentType = MediaType.APPLICATION_JSON
                    content = PEDIDO_PESSOA_FISICA_COM_REGIME
                }.andExpect {
                    status { isCreated() }
                }.andReturn()
                .response.contentAsString

        assertThat(corpo).contains("\"regime_tributacao\":null", "\"aliquota_percentual\":0.00")
    }

    private companion object {
        const val PEDIDO_DO_CONTRATO = """
            {
              "id_pedido": "PED-1001",
              "data": "2026-11-18",
              "cliente": {
                "tipo_pessoa": "JURIDICA",
                "documento": "12345678000199",
                "nome": "Empresa Exemplo Ltda",
                "regime_tributacao": "LUCRO_PRESUMIDO",
                "enderecos": [
                  { "finalidade": "COBRANCA_ENTREGA", "regiao": "SUDESTE", "logradouro": "Av. Paulista", "numero": "1000", "cidade": "São Paulo", "estado": "SP", "cep": "01310100" }
                ]
              },
              "itens": [
                { "id_item": "SKU-1", "descricao": "Notebook", "quantidade": 2, "valor_unitario": 1500.00 }
              ],
              "valor_frete": 50.00
            }
        """

        const val RESPOSTA_DO_CONTRATO = """
            {
              "id_pedido": "PED-1001",
              "data_emissao": "2026-11-18T15:30:00-03:00",
              "cliente": { "tipo_pessoa": "JURIDICA", "documento": "12345678000199", "nome": "Empresa Exemplo Ltda", "regime_tributacao": "LUCRO_PRESUMIDO" },
              "aliquota_percentual": 16.00,
              "itens": [
                { "id_item": "SKU-1", "descricao": "Notebook", "quantidade": 2, "valor_unitario": 1500.00, "valor_total": 3000.00, "valor_tributo": 480.00 }
              ],
              "frete": { "regiao": "SUDESTE", "valor_base": 50.00, "percentual": 4.80, "valor": 52.40 },
              "total_itens": 3000.00,
              "total_tributos": 480.00,
              "total_nota": 3532.40
            }
        """

        const val PEDIDO_PESSOA_FISICA_COM_REGIME = """
            {
              "id_pedido": "PED-2001",
              "data": "2026-11-18",
              "cliente": {
                "tipo_pessoa": "FISICA",
                "documento": "12345678901",
                "nome": "Maria Silva",
                "regime_tributacao": "LUCRO_REAL",
                "enderecos": [
                  { "finalidade": "ENTREGA", "regiao": "SUL", "logradouro": "Rua A", "numero": "10", "cidade": "Curitiba", "estado": "PR", "cep": "80000000" }
                ]
              },
              "itens": [
                { "id_item": "SKU-9", "descricao": "Livro", "quantidade": 1, "valor_unitario": 100.00 }
              ],
              "valor_frete": 10.00
            }
        """
    }
}

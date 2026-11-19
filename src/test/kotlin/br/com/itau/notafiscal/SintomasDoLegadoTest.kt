package br.com.itau.notafiscal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlin.time.toKotlinDuration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresDeTeste::class)
class SintomasDoLegadoTest(
    @LocalServerPort private val porta: Int,
    @Value("\${nota-fiscal.integracoes.latencia.estoque}") private val latenciaEstoque: java.time.Duration,
    @Value("\${nota-fiscal.integracoes.latencia.entrega}") private val latenciaMaisLenta: java.time.Duration,
) {
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    private val json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()

    private fun emitir(corpo: String): HttpResponse<String> =
        http.send(
            HttpRequest
                .newBuilder(URI.create("http://localhost:$porta/notas-fiscais"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun HttpResponse<String>.corpo(): JsonNode = json.readTree(body())

    private fun <T> emParalelo(
        quantidade: Int,
        tarefa: (Int) -> T,
    ): List<T> =
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            List(quantidade) { indice -> executor.submit(Callable { tarefa(indice) }) }.map { it.get() }
        }

    private fun pedido(
        idPedido: String = "PED-${UUID.randomUUID()}",
        quantidadeDeItens: Int = 1,
        valorUnitario: String = "100.00",
    ): String {
        val itens =
            (1..quantidadeDeItens).joinToString(",") {
                """{ "id_item": "SKU-$it", "descricao": "Produto $it", "quantidade": 1, "valor_unitario": $valorUnitario }"""
            }
        return """
            {
              "id_pedido": "$idPedido",
              "data": "2026-11-18",
              "cliente": {
                "tipo_pessoa": "FISICA",
                "documento": "12345678901",
                "nome": "Maria Silva",
                "enderecos": [
                  { "finalidade": "ENTREGA", "regiao": "SUDESTE", "logradouro": "Rua A", "numero": "10", "cidade": "São Paulo", "estado": "SP", "cep": "01000000" }
                ]
              },
              "itens": [ $itens ],
              "valor_frete": 50.00
            }
        """
    }

    private fun aquecer() = repeat(3) { emitir(pedido()) }

    private fun mediana(tempos: List<Duration>) = tempos.sorted()[tempos.size / 2]

    @Nested
    inner class `a 1ª execução funciona e nas seguintes os itens de execuções anteriores se acumulam` {
        @Test
        fun `cada emissão devolve só os próprios itens`() {
            val primeira = emitir(pedido(quantidadeDeItens = 2))
            val segunda = emitir(pedido(quantidadeDeItens = 1))
            val terceira = emitir(pedido(quantidadeDeItens = 3))

            assertThat(listOf(primeira, segunda, terceira).map { it.statusCode() }).containsOnly(201)
            assertThat(primeira.corpo()["itens"].size()).isEqualTo(2)
            assertThat(segunda.corpo()["itens"].size()).isEqualTo(1)
            assertThat(terceira.corpo()["itens"].size()).isEqualTo(3)
        }
    }

    @Nested
    inner class `pedidos com mais de 6 itens demoram muito` {
        @Test
        fun `pedido com 10 itens demora o mesmo que pedido com 1 item`() {
            aquecer()

            val comUmItem = measureTime { assertThat(emitir(pedido(quantidadeDeItens = 1)).statusCode()).isEqualTo(201) }
            val comDezItens = measureTime { assertThat(emitir(pedido(quantidadeDeItens = 10)).statusCode()).isEqualTo(201) }

            val estoqueEmSequencia = latenciaEstoque.multipliedBy(10).toKotlinDuration()
            assertThat(comDezItens).isLessThan(estoqueEmSequencia)
            assertThat(comDezItens - comUmItem).isLessThan(latenciaEstoque.toKotlinDuration())
            assertThat(comDezItens).isGreaterThanOrEqualTo(latenciaMaisLenta.toKotlinDuration())
        }
    }

    @Nested
    inner class `após algumas execuções o retorno fica lento` {
        @Test
        fun `latência continua estável ao longo de várias execuções`() {
            aquecer()

            val tempos = List(30) { measureTime { emitir(pedido(quantidadeDeItens = 3)) } }

            val inicio = mediana(tempos.take(10))
            val fim = mediana(tempos.takeLast(10))
            assertThat(fim).isLessThan(inicio * 1.5 + 100.milliseconds)
        }
    }

    @Nested
    inner class `requisições simultâneas são processadas uma por vez` {
        @Test
        fun `20 requisições simultâneas terminam em paralelo`() {
            aquecer()
            val umaRequisicao = measureTime { emitir(pedido()) }

            val (respostas, total) = measureTimedValue { emParalelo(20) { emitir(pedido()) } }

            assertThat(respostas.map { it.statusCode() }).containsOnly(201)
            assertThat(total).isLessThan(umaRequisicao * 5)
        }
    }

    @Nested
    inner class `centavos sumindo ou sobrando em nota, tributos, frete e total` {
        @Test
        fun `nota do contrato fecha em 3532,40`() {
            val corpo =
                emitir(
                    """
                    {
                      "id_pedido": "PED-${UUID.randomUUID()}",
                      "data": "2026-11-18",
                      "cliente": {
                        "tipo_pessoa": "JURIDICA", "documento": "12345678000199", "nome": "Empresa Exemplo Ltda", "regime_tributacao": "LUCRO_PRESUMIDO",
                        "enderecos": [ { "finalidade": "COBRANCA_ENTREGA", "regiao": "SUDESTE", "logradouro": "Av. Paulista", "numero": "1000", "cidade": "São Paulo", "estado": "SP", "cep": "01310100" } ]
                      },
                      "itens": [ { "id_item": "SKU-1", "descricao": "Notebook", "quantidade": 2, "valor_unitario": 1500.00 } ],
                      "valor_frete": 50.00
                    }
                    """,
                ).corpo()

            assertThat(corpo["total_nota"].decimalValue()).isEqualTo(BigDecimal("3532.40"))
        }

        @Test
        fun `totais fecham com a soma das partes arredondadas`() {
            val corpo = emitir(pedido(quantidadeDeItens = 3, valorUnitario = "166.71")).corpo()

            val itens = corpo["itens"].toList()
            val somaDosItens = itens.sumOf { it["valor_total"].decimalValue() }
            val somaDosTributos = itens.sumOf { it["valor_tributo"].decimalValue() }
            val frete = corpo["frete"]["valor"].decimalValue()

            assertThat(corpo["total_itens"].decimalValue()).isEqualTo(somaDosItens).isEqualTo(BigDecimal("500.13"))
            assertThat(corpo["total_tributos"].decimalValue()).isEqualTo(somaDosTributos).isEqualTo(BigDecimal("60.03"))
            assertThat(corpo["total_nota"].decimalValue()).isEqualTo(somaDosItens + somaDosTributos + frete)
            assertThat(
                listOf(corpo["total_itens"], corpo["total_tributos"], corpo["total_nota"], corpo["frete"]["valor"])
                    .map { it.decimalValue().scale() },
            ).containsOnly(2)
        }
    }

    @Nested
    inner class `notas com numeração duplicada` {
        @Test
        fun `30 emissões simultâneas recebem números distintos`() {
            val numeros = emParalelo(30) { emitir(pedido()).corpo()["numero_nota"].asLong() }

            assertThat(numeros).hasSize(30).doesNotHaveDuplicates()
        }

        @Test
        fun `mesmo pedido enviado 10 vezes ao mesmo tempo gera uma única nota`() {
            val corpoDoPedido = pedido()

            val respostas = emParalelo(10) { emitir(corpoDoPedido) }

            assertThat(respostas.count { it.statusCode() == 201 }).isEqualTo(1)
            assertThat(respostas.count { it.statusCode() == 409 }).isEqualTo(9)
            assertThat(respostas.map { it.corpo()["numero_nota"].asLong() }.distinct()).hasSize(1)
        }
    }

    @Nested
    inner class `dados incompletos geram 500 sem explicação, ou notas sem itens ou sem frete` {
        @Test
        fun `pedido sem itens é recusado com 400 explicando o campo`() {
            val resposta = emitir(pedido().replace(Regex("\"itens\": \\[.*?]"), "\"itens\": []"))

            assertThat(resposta.statusCode()).isEqualTo(400)
            assertThat(resposta.corpo()["campos"][0]["campo"].asString()).isEqualTo("itens")
        }

        @Test
        fun `pedido sem frete é recusado com 400 explicando o campo`() {
            val resposta = emitir(pedido().replace(Regex(",\\s*\"valor_frete\": 50.00"), ""))

            assertThat(resposta.statusCode()).isEqualTo(400)
            assertThat(resposta.corpo()["campos"][0]["campo"].asString()).isEqualTo("valor_frete")
        }

        @Test
        fun `corpo vazio é recusado com 400, nunca 500`() {
            val resposta = emitir("{}")

            assertThat(resposta.statusCode()).isEqualTo(400)
            assertThat(resposta.headers().firstValue("Content-Type")).hasValueSatisfying {
                assertThat(it).startsWith("application/problem+json")
            }
        }

        @Test
        fun `pedido recusado por regra de negócio não grava nota`() {
            val idPedido = "PED-${UUID.randomUUID()}"
            val semEnderecoDeEntrega = pedido(idPedido = idPedido).replace("\"ENTREGA\"", "\"COBRANCA\"")

            assertThat(emitir(semEnderecoDeEntrega).statusCode()).isEqualTo(422)
            assertThat(emitir(pedido(idPedido = idPedido)).statusCode()).isEqualTo(201)
        }
    }
}

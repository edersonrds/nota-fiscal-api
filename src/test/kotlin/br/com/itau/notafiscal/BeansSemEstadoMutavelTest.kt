package br.com.itau.notafiscal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.util.ClassUtils
import java.lang.reflect.Field
import java.lang.reflect.Modifier

@SpringBootTest
@Import(PostgresDeTeste::class)
class BeansSemEstadoMutavelTest(
    @Autowired private val contexto: ApplicationContext,
) {
    private val pacoteDaAplicacao = "br.com.itau.notafiscal"

    private val tiposQueGuardamEstado =
        listOf(
            Collection::class.java,
            Map::class.java,
            java.util.concurrent.atomic.AtomicLong::class.java,
            java.util.concurrent.atomic.AtomicInteger::class.java,
            java.util.concurrent.atomic.AtomicReference::class.java,
            ThreadLocal::class.java,
        )

    private fun classesDosBeansDaAplicacao(): List<Class<*>> =
        contexto.beanDefinitionNames
            .map { ClassUtils.getUserClass(contexto.getBean(it)) }
            .filter { it.packageName.startsWith(pacoteDaAplicacao) }
            .distinct()

    private fun camposDeInstancia(classe: Class<*>): List<Field> =
        generateSequence(classe) { it.superclass }
            .takeWhile { it.packageName.startsWith(pacoteDaAplicacao) }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .toList()

    @Test
    fun `beans da aplicação foram encontrados`() {
        assertThat(classesDosBeansDaAplicacao().map { it.simpleName })
            .contains("EmitirNotaFiscalService", "NotificadorSistemas", "NotaFiscalController", "NotaFiscalRepositoryJdbc")
    }

    @Test
    fun `nenhum bean tem campo reatribuível`() {
        val camposMutaveis =
            classesDosBeansDaAplicacao()
                .flatMap { camposDeInstancia(it) }
                .filterNot { Modifier.isFinal(it.modifiers) }
                .map { "${it.declaringClass.simpleName}.${it.name}" }

        assertThat(camposMutaveis).isEmpty()
    }

    @Test
    fun `nenhum bean guarda coleção, mapa, atômico ou ThreadLocal`() {
        val camposComEstado =
            classesDosBeansDaAplicacao()
                .flatMap { camposDeInstancia(it) }
                .filter { campo -> tiposQueGuardamEstado.any { it.isAssignableFrom(campo.type) } || campo.type.isArray }
                .map { "${it.declaringClass.simpleName}.${it.name}: ${it.type.simpleName}" }

        assertThat(camposComEstado).isEmpty()
    }
}

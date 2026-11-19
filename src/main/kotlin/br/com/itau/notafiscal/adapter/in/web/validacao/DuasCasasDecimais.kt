package br.com.itau.notafiscal.adapter.`in`.web.validacao

import br.com.itau.notafiscal.domain.model.temNoMaximoDuasCasas
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.math.BigDecimal
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DuasCasasDecimaisValidator::class])
annotation class DuasCasasDecimais(
    val message: String = "deve ter no máximo 2 casas decimais",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DuasCasasDecimaisValidator : ConstraintValidator<DuasCasasDecimais, BigDecimal> {
    override fun isValid(
        valor: BigDecimal?,
        context: ConstraintValidatorContext,
    ): Boolean = valor == null || valor.temNoMaximoDuasCasas()
}

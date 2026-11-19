package br.com.itau.notafiscal.adapter.`in`.web.validacao

import br.com.itau.notafiscal.adapter.`in`.web.ClienteRequest
import br.com.itau.notafiscal.adapter.`in`.web.TipoPessoa
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DocumentoCompativelValidator::class])
annotation class DocumentoCompativel(
    val message: String = "deve ter só dígitos: 11 (CPF) para FISICA ou 14 (CNPJ) para JURIDICA",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class DocumentoCompativelValidator : ConstraintValidator<DocumentoCompativel, ClienteRequest> {
    override fun isValid(
        cliente: ClienteRequest?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (cliente == null || cliente.documentoCompativel()) return true
        context.disableDefaultConstraintViolation()
        context
            .buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
            .addPropertyNode("documento")
            .addConstraintViolation()
        return false
    }

    private fun ClienteRequest.documentoCompativel(): Boolean {
        val tamanhoEsperado =
            when (tipoPessoa) {
                TipoPessoa.FISICA -> 11
                TipoPessoa.JURIDICA -> 14
            }
        return documento.length == tamanhoEsperado && documento.all { it.isDigit() }
    }
}

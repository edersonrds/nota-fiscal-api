package br.com.itau.notafiscal.adapter.out.persistence

import br.com.itau.notafiscal.application.port.out.GeradorNumeroNota
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class GeradorNumeroNotaEmMemoria : GeradorNumeroNota {
    private val ultimo = AtomicLong()

    override fun proximo(): Long = ultimo.incrementAndGet()
}

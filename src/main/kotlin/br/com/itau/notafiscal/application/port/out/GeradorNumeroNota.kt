package br.com.itau.notafiscal.application.port.out

fun interface GeradorNumeroNota {
    fun proximo(): Long
}

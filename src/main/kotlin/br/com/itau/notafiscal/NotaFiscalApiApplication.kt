package br.com.itau.notafiscal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NotaFiscalApiApplication

fun main(args: Array<String>) {
	runApplication<NotaFiscalApiApplication>(*args)
}

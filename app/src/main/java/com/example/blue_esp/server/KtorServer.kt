package com.example.blue_esp.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun startKtorServer() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/") {
                call.respondText("ESP32 Bridge Server is running")
            }
            get("/status") {
                // Returns the current state as JSON
                call.respond(EspDataRepository.state.value)
            }
        }
    }.start(wait = true)
}

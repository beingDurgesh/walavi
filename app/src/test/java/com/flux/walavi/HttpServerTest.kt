package com.flux.walavi

import com.flux.walavi.gateway.MiniHttpServer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket

class HttpServerTest {

    @Test
    fun servesBufferedAndChunkedResponses() {
        val server = MiniHttpServer(0, handler = { exchange ->
            when (exchange.request.path) {
                "/json" -> exchange.respondText(200, """{"ok":true}""", "application/json")
                "/echo" -> exchange.respondText(200, exchange.request.bodyText(), "text/plain")
                "/stream" -> exchange.stream(
                    200,
                    mapOf("Content-Type" to "text/event-stream"),
                ) { sink ->
                    sink.write("data: one\n\n".toByteArray())
                    sink.write("data: [DONE]\n\n".toByteArray())
                }
                else -> exchange.respondText(404, "missing")
            }
        })
        server.start()
        try {
            val port = server.localPort()

            val json = raw(port, "GET /json HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
            assertTrue(json.startsWith("HTTP/1.1 200"))
            assertTrue(json.contains("""{"ok":true}"""))

            val echo = raw(
                port,
                "POST /echo HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" +
                    "Content-Length: 11\r\nConnection: close\r\n\r\n{\"hello\":1}",
            )
            assertTrue(echo.contains("""{"hello":1}"""))

            val stream = raw(port, "GET /stream HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
            assertTrue(stream.contains("Transfer-Encoding: chunked"))
            assertTrue(stream.contains("data: one"))
            assertTrue(stream.contains("data: [DONE]"))
            assertTrue(stream.trimEnd().endsWith("0"))
        } finally {
            server.stop()
        }
    }

    private fun raw(port: Int, request: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            return socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }
}

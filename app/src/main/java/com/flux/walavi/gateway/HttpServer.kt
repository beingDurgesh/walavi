package com.flux.walavi.gateway

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun bodyText(): String = String(body, Charsets.UTF_8)
}

private val CRLF = "\r\n".toByteArray(Charsets.US_ASCII)
private val ZERO_CHUNK = "0\r\n\r\n".toByteArray(Charsets.US_ASCII)

class ChunkSink(private val out: OutputStream) {    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (length <= 0) return
        out.write(Integer.toHexString(length).toByteArray(Charsets.US_ASCII))
        out.write(CRLF)
        out.write(bytes, offset, length)
        out.write(CRLF)
        out.flush()
    }

    fun close() {
        out.write(ZERO_CHUNK)
        out.flush()
    }
}

class HttpExchange internal constructor(
    val request: HttpRequest,
    private val out: OutputStream,
) {
    var responded: Boolean = false
        private set

    fun respond(
        status: Int,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray = ByteArray(0),
    ) {
        if (responded) return
        responded = true
        val merged = headers.toMutableMap()
        merged["Content-Length"] = body.size.toString()
        merged["Connection"] = "close"
        writeHead(out, status, merged)
        out.write(body)
        out.flush()
    }

    fun respondText(
        status: Int,
        text: String,
        contentType: String = "text/plain; charset=utf-8",
        headers: Map<String, String> = emptyMap(),
    ) {
        respond(status, headers + mapOf("Content-Type" to contentType), text.toByteArray(Charsets.UTF_8))
    }

    fun stream(status: Int, headers: Map<String, String>, block: (ChunkSink) -> Unit) {
        if (responded) return
        responded = true
        val merged = headers.toMutableMap()
        merged["Transfer-Encoding"] = "chunked"
        merged["Connection"] = "close"
        writeHead(out, status, merged)
        val sink = ChunkSink(out)
        try {
            block(sink)
        } finally {
            runCatching { sink.close() }
        }
    }
}

class RequestTooLarge : IOException("request body too large")

class MiniHttpServer(
    private val port: Int,
    private val handler: (HttpExchange) -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val permits = Semaphore(MAX_CONNECTIONS)
    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "walavi-conn").apply { isDaemon = true }
    }

    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        running.set(true)
        thread(name = "walavi-accept", isDaemon = true) {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (error: IOException) {
                    if (running.get()) log("accept failed: ${error.message}")
                    break
                }
                if (!permits.tryAcquire()) {
                    runCatching {
                        client.getOutputStream().write(OVERLOADED)
                        client.getOutputStream().flush()
                    }
                    runCatching { client.close() }
                    continue
                }
                pool.execute {
                    try {
                        handle(client)
                    } catch (error: Exception) {
                        log("connection error: ${error.message}")
                    } finally {
                        permits.release()
                        runCatching { client.close() }
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool.shutdownNow()
    }

    fun localPort(): Int = serverSocket?.localPort ?: port

    private fun handle(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
        val output = BufferedOutputStream(socket.getOutputStream(), 16 * 1024)

        val request = try {
            readRequest(input, output) ?: return
        } catch (_: RequestTooLarge) {
            writeRaw(output, 413, "Payload Too Large", """{"error":{"message":"Request body exceeds 16 MB","type":"invalid_request_error"}}""")
            return
        } catch (_: IOException) {
            return
        }

        val exchange = HttpExchange(request, output)
        try {
            handler(exchange)
        } catch (error: Exception) {
            log("handler error: ${error.message}")
            if (!exchange.responded) {
                exchange.respondText(
                    500,
                    """{"error":{"message":"Gateway error: ${error.message?.take(200)}","type":"server_error"}}""",
                    "application/json",
                )
            }
        }
        runCatching { output.flush() }
    }

    private fun readRequest(input: InputStream, output: OutputStream): HttpRequest? {
        val requestLine = readLine(input, 8 * 1024) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null

        val headers = LinkedHashMap<String, String>()
        var headerBytes = 0
        while (true) {
            val line = readLine(input, 16 * 1024) ?: return null
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > 64 * 1024) return null
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            headers[name] = headers[name]?.let { "$it, $value" } ?: value
        }

        if (headers["expect"]?.lowercase()?.contains("100-continue") == true) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        val body = readBody(input, headers)
        val target = parts[1]
        val question = target.indexOf('?')
        val path = if (question >= 0) target.substring(0, question) else target
        val query = if (question >= 0) parseQuery(target.substring(question + 1)) else emptyMap()

        return HttpRequest(
            method = parts[0].uppercase(),
            path = path,
            query = query,
            headers = headers,
            body = body,
        )
    }

    private fun readBody(input: InputStream, headers: Map<String, String>): ByteArray {
        val transferEncoding = headers["transfer-encoding"]?.lowercase()
        if (transferEncoding?.contains("chunked") == true) return readChunked(input)

        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        if (length <= 0L) return ByteArray(0)
        if (length > MAX_BODY_BYTES) throw RequestTooLarge()
        return readExactly(input, length.toInt())
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input, 1024) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) {
                readLine(input, 1024)
                break
            }
            if (out.size().toLong() + size > MAX_BODY_BYTES) throw RequestTooLarge()
            out.write(readExactly(input, size))
            readLine(input, 8)
        }
        return out.toByteArray()
    }

    private fun readExactly(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var read = 0
        while (read < size) {
            val count = input.read(buffer, read, size - read)
            if (count < 0) throw IOException("unexpected end of stream")
            read += count
        }
        return buffer
    }

    private fun readLine(input: InputStream, max: Int): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(byte.toChar())
            if (builder.length > max) throw IOException("header line too long")
        }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val index = pair.indexOf('=')
            val key = if (index >= 0) pair.substring(0, index) else pair
            val value = if (index >= 0) pair.substring(index + 1) else ""
            runCatching {
                result[URLDecoder.decode(key, "UTF-8")] = URLDecoder.decode(value, "UTF-8")
            }
        }
        return result
    }

    private fun writeRaw(output: OutputStream, status: Int, reason: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writeHead(
            output,
            status,
            mapOf(
                "Content-Type" to "application/json",
                "Content-Length" to bytes.size.toString(),
                "Connection" to "close",
            ),
            reason,
        )
        output.write(bytes)
        output.flush()
    }

    private companion object {
        const val MAX_CONNECTIONS = 48
        const val MAX_BODY_BYTES = 16L * 1024 * 1024
        const val SOCKET_TIMEOUT_MS = 60_000
        private val OVERLOAD_BODY =
            """{"error":{"message":"Gateway is at connection limit","type":"server_error"}}"""
                .toByteArray(Charsets.US_ASCII)
        val OVERLOADED: ByteArray =
            (
                "HTTP/1.1 503 Service Unavailable\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${OVERLOAD_BODY.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.US_ASCII) + OVERLOAD_BODY
    }
}

internal fun writeHead(
    out: OutputStream,
    status: Int,
    headers: Map<String, String>,
    reasonOverride: String? = null,
) {
    val merged = LinkedHashMap<String, String>()
    merged["Access-Control-Allow-Origin"] = "*"
    merged["Access-Control-Allow-Headers"] =
        "Authorization, Content-Type, x-api-key, anthropic-version, anthropic-beta"
    merged["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    merged["Access-Control-Max-Age"] = "86400"
    merged["Server"] = "Walavi"
    merged.putAll(headers)

    val reason = reasonOverride ?: reason(status)
    out.write("HTTP/1.1 $status $reason\r\n".toByteArray(Charsets.US_ASCII))
    for ((name, rawValue) in merged) {
        val value = rawValue.replace("\r", "").replace("\n", "")
        out.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
    }
    out.write("\r\n".toByteArray(Charsets.US_ASCII))
    out.flush()
}

internal fun reason(status: Int): String = when (status) {
    100 -> "Continue"
    200 -> "OK"
    201 -> "Created"
    204 -> "No Content"
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    408 -> "Request Timeout"
    413 -> "Payload Too Large"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    501 -> "Not Implemented"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    else -> "OK"
}

package com.grapsee.gsai

import com.grapsee.gsai.data.remote.ApiClient
import com.grapsee.gsai.di.gsHttpClient
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local HTTP server that speaks the app's ACTUAL wire protocol.
 *
 * WHY THIS EXISTS. Two of the operator's requirements need a backend that
 * behaves, and neither could be met with the harnesses that were there before:
 *
 *   * "prefer-local OFF, network UP -> the app must use the network path". There
 *     was no such test. Every routing test pointed ApiClient at a dead port, so
 *     the network path was never once observed succeeding.
 *   * "a mid-stream cut produces a different classification than a refused
 *     port". That needs a server which accepts, streams something, and then dies
 *     MID-STREAM -- a real protocol behaviour, not a connection refusal.
 *
 * And a third thing it makes possible, which is the part that matters most: the
 * three routing sources can be told apart BY WHAT THEY ARE, because each one has
 * a marker no other can produce.
 *
 * THE SHAPES ARE READ FROM THE CLIENT, NOT REMEMBERED. From ApiClient:
 *
 *   createConversation   POST $root/conversations
 *                        -> ConversationDto {id,title,modelId,pinned,archived,
 *                                          createdAt,updatedAt}
 *   sendMessageStream    POST $root/conversations/{id}/messages
 *                        -> SSE, and THE EVENT NAME IS INSIDE THE JSON:
 *                             data: {"event":"delta","data":"..."}
 *                        ApiClient.kt:96 documents this exact wire format, and
 *                        there is no `event:` SSE line in this parser. That has
 *                        cost two runs already.
 *   messages             GET  $root/conversations/{id}   -> [MessageDto]
 *
 * MessageDto needs id, conversationId, role, content, createdAt; the rest
 * default. So a `done` event is small and exact.
 *
 * NO TEST DEPENDENCY. ktor-server is not on the classpath -- build.gradle.kts has
 * only the ktor CLIENT artifacts (lines 199-203) -- and adding a server to the
 * app's dependency graph to satisfy a test is the wrong trade. A ServerSocket
 * writing a response whose shape is already known is a few dozen lines and
 * depends on nothing.
 *
 * THE MARKER, and why it is a string nothing else can produce. Every message
 * stream in [MODE_FULL] emits [NETWORK_MARKER] as its delta. The on-device model
 * cannot produce it (it is asked about Paris and says "Paris"), and neither can
 * the canned responder. So "did this turn go to the network" is a question with an
 * exact answer rather than a guess from context.
 */
class WireServer private constructor(
    private val socket: ServerSocket,
) {
    /**
     * The accept loop, assigned by [start].
     *
     * It is NOT a constructor parameter. The first version took a `Thread` and
     * passed `Thread()` as a placeholder, because the loop is only created once
     * the socket exists and then has to be stored -- so the placeholder was a
     * dummy that existed only to make the type checker happy, and a reader had to
     * work out that it was never used. A `var` with no initial value says the
     * same thing without the lie.
     */
    private var acceptLoop: Thread? = null
    companion object {
        /** A complete stream: status, a delta, a done event, and the terminating chunk. */
        const val MODE_FULL = "full"

        /** One event, then the connection dies with the chunked body UNTERMINATED. */
        const val MODE_CUT_MID_STREAM = "cut-mid-stream"

        /** A 200 with no events and a clean close: the "clean break" path. */
        const val MODE_CLEAN_BREAK = "clean-break"

        /**
         * A 500 with a sanitized body. The THIRD distinct failure arm: the server
         * was reachable and said no.
         *
         * This is the one that must never be dressed up as "offline". From
         * send()'s FORENSIC AUDIT [5]: stamping every failure "backend unreachable"
         * was a retired false label, and a 500 is the clearest case of it -- the
         * provider answered, so telling the user their network is down is a lie
         * about a reachable server.
         */
        const val MODE_HTTP_500 = "http-500"

        /** The delta a full stream sends. Nothing else in the app can produce it. */
        const val NETWORK_MARKER = "NETWORK ANSWER"

        private const val CONV_ID = "srv-conv-1"

        /**
         * The loopback address to BIND, resolved deliberately.
         *
         * `InetAddress.getLoopbackAddress()` returns IPv6 `::1` on a great many
         * Android devices, and `ServerSocket(0, 8, that)` then binds `[::1]:port`
         * while the client dials `127.0.0.1:port`. Those are different addresses,
         * so the socket exists, the bind succeeds, and no connection ever arrives.
         *
         * That is exactly what runs 36770334063 and 36772124600 showed:
         *
         *     Matrix b2 server saw 0 message request(s)
         *     Classify: server saw 0 request(s)
         *
         * with no exception from the bind and no line from the request handler --
         * a server that is running, bound, and never asked. And it is
         * indistinguishable from the case it was mistaken for, because a refused
         * connect and a connect to the wrong family both surface as
         * `GenuineUnreachable`.
         *
         * So the address is named rather than asked for, and the address actually
         * bound is PRINTED at startup. A server that cannot be reached must say
         * where it is listening, or the next person repeats this.
         */
        private val LOOPBACK_V4: InetAddress = InetAddress.getByName("127.0.0.1")

        fun start(mode: String): WireServer {
            val sock = ServerSocket(0, 8, LOOPBACK_V4)
            println("WireServer[$mode] listening on ${sock.localAddress}:${sock.localPort}")
            val server = WireServer(sock)
            val loop = Thread {
                while (!sock.isClosed) {
                    val s = try {
                        sock.accept()
                    } catch (_: Exception) {
                        return@Thread
                    }
                    Thread {
                        try {
                            server.handle(s, mode)
                        } catch (_: Throwable) {
                            // A socket closing mid-write is the NORMAL way two of
                            // these three modes end. The assertions are about what
                            // the APP produced, never about the socket.
                        } finally {
                            runCatching { s.close() }
                        }
                    }.apply { isDaemon = true }.start()
                }
            }
            loop.isDaemon = true
            loop.start()
            return server.also { it.acceptLoop = loop }
        }

        fun apiFor(server: WireServer, sessionId: String): ApiClient =
            ApiClient(gsHttpClient(sessionId), "http://127.0.0.1:${server.port()}")

        /** One SSE frame. The event name is inside the JSON, per ApiClient.kt:96. */
        private fun frame(event: String, data: String): String =
            "data: {\"event\":\"$event\",\"data\":${quote(data)}}\n\n"

        private fun quote(s: String): String = buildString {
            append('"')
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    else -> append(c)
                }
            }
            append('"')
        }

        private fun readRequestHead(input: InputStream): String {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b == -1) break
                head.append(b.toChar())
            }
            // Drain the body if there is one, so the client sees a real response
            // rather than a reset. Content-Length is in the head already read.
            val m = Regex("(?i)content-length:\\s*(\\d+)").find(head.toString())
            m?.groupValues?.get(1)?.toIntOrNull()?.let { n -> repeat(n) { input.read() } }
            return head.toString()
        }
    }

    /** How many times a message stream was requested. Zero means no turn ever
     *  reached the network, which is how "prefer-local short-circuited" is
     *  MEASURED rather than inferred from the answer's text. */
    val messageRequests = AtomicInteger(0)
    val createRequests = AtomicInteger(0)

    fun port(): Int = socket.localPort

    fun stop() {
        runCatching { socket.close() }
        runCatching { acceptLoop?.interrupt() }
    }

    private fun handle(s: Socket, mode: String) {
        val input: InputStream = s.getInputStream()
        val out = BufferedOutputStream(s.getOutputStream())
        val head = readRequestHead(input)
        val isPost = head.startsWith("POST")
        val path = head.lineSequence().firstOrNull()?.split(" ")?.getOrNull(1) ?: "/"
        println("WireServer[$mode] <- ${if (isPost) "POST" else "GET "} $path")

        when {
            isPost && path.endsWith("/conversations") -> {
                createRequests.incrementAndGet()
                val body = """{"id":"$CONV_ID","title":"t","modelId":null,""" +
                    """"pinned":false,"archived":false,""" +
                    """"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}"""
                writeJson(out, body)
                println("WireServer[$mode] -> ConversationDto (${body.length} bytes)")
            }

            isPost && path.contains("/messages") && mode == MODE_HTTP_500 -> {
                messageRequests.incrementAndGet()
                // The sanitized shape the client decodes: ErrorResponseDto {error}.
                // An EMPTY error string is deliberately wrong on purpose, because
                // classifySendFailure must key on the STATUS, and a message here
                // would let a wrong arm pass.
                writeJson(out, """{"error":"upstream model unavailable"}""", status = "500 Internal Server Error")
                println("WireServer[$mode] -> 500 with a sanitized body")
            }

            isPost && path.contains("/messages") -> {
                messageRequests.incrementAndGet()
                out.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                        "Connection: close\r\n" +
                        (if (mode == MODE_CLEAN_BREAK) "Content-Length: 0\r\n\r\n"
                         else "Transfer-Encoding: chunked\r\n\r\n")).toByteArray()
                )
                out.flush()
                when (mode) {
                    MODE_FULL -> {
                        chunk(out, frame("status", "composing"), mode)
                        chunk(out, frame("delta", NETWORK_MARKER), mode)
                        val done = """{"id":"srv-msg-1","conversationId":"$CONV_ID",""" +
                            """"role":"assistant","content":"$NETWORK_MARKER",""" +
                            """"createdAt":"2026-01-01T00:00:01Z"}"""
                        chunk(out, frame("done", done), mode)
                        // The terminating zero-length chunk: a COMPLETE stream, as
                        // opposed to a cut.
                        out.write("0\r\n\r\n".toByteArray())
                        out.flush()
                        println("WireServer[$mode] -> status, delta, done, terminating chunk")
                    }

                    // A real mid-stream cut: one event, then the connection dies
                    // with the chunked body UNTERMINATED. This is the case
                    // MidStreamCut exists for, and it differs from a refusal in the
                    // way that matters: the provider WAS reachable.
                    MODE_CUT_MID_STREAM -> {
                        chunk(out, frame("delta", "HALF A SE"), mode)
                        println("WireServer[$mode] -> one delta, then CLOSING WITH NO TERMINATING CHUNK")
                    }

                    else -> println("WireServer[$mode] -> empty 200, clean close")
                }
            }

            else -> {
                // Message history, for recoverLatestAssistant.
                writeJson(out, "[]")
                println("WireServer[$mode] -> [] for $path")
            }
        }
    }

    /** `mode` is passed in because the log line is the only thing that needs it,
     *  and it is a local of handle() -- a companion function cannot see it. The
     *  first version referenced it from here and kotlinc said
     *  `Unresolved reference 'mode'`. */
    private fun chunk(out: BufferedOutputStream, text: String, mode: String) {
        val b = text.toByteArray()
        // Integer.toHexString, not Python's "%x" % n -- Kotlin has no % format
        // operator, and `Unresolved reference 'rem' for operator '%'` is what that
        // typo looks like when it reaches kotlinc.
        out.write((Integer.toHexString(b.size) + "\r\n").toByteArray())
        out.write(b)
        out.write("\r\n".toByteArray())
        out.flush()
        println("WireServer[$mode] -> ${text.trim()}")
    }

    private fun writeJson(
        out: BufferedOutputStream,
        body: String,
        status: String = "200 OK",
    ) {
        val b = body.toByteArray()
        out.write(
            ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray()
        )
        out.write(b)
        out.flush()
    }
}

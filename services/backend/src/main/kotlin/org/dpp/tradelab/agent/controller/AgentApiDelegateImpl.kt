package org.dpp.tradelab.agent.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.dpp.tradelab.agent.generated.api.AgentApiDelegate
import org.dpp.tradelab.agent.generated.model.AgentQueryRequest
import org.dpp.tradelab.agent.generated.model.AgentQueryResponse
import org.dpp.tradelab.agent.service.AgentService
import org.dpp.tradelab.user.exception.InvalidTokenException
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Service
class AgentApiDelegateImpl(
    private val agentService: AgentService
) : AgentApiDelegate {

    private val objectMapper = jacksonObjectMapper()

    override fun queryAgent(agentQueryRequest: AgentQueryRequest): ResponseEntity<Resource> {
        val userId = SecurityContextHolder.getContext().authentication?.principal as? UUID
            ?: throw InvalidTokenException("Authentication required")

        val reply = agentService.query(
            userId = userId,
            accountId = agentQueryRequest.accountId,
            conversationId = agentQueryRequest.conversationId,
            message = agentQueryRequest.message
        )

        return if (acceptsEventStream()) {
            streamReply(reply)
        } else {
            bufferedReply(agentQueryRequest.conversationId, reply)
        }
    }

    private fun acceptsEventStream(): Boolean {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return false
        val acceptHeader = request.getHeader(HttpHeaders.ACCEPT) ?: return false

        return try {
            MediaType.parseMediaTypes(acceptHeader)
                .any { it.isCompatibleWith(MediaType.TEXT_EVENT_STREAM) }
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun streamReply(reply: Flowable<String>): ResponseEntity<Resource> {
        val subscriptionRef = AtomicReference<Disposable?>()
        val inputStream = object : PipedInputStream() {
            override fun close() {
                subscriptionRef.get()?.dispose()
                super.close()
            }
        }
        val outputStream = PipedOutputStream(inputStream)
        val writer = outputStream.bufferedWriter(StandardCharsets.UTF_8)

        val subscription = reply
            .subscribeOn(Schedulers.io())
            .subscribe(
                { chunk ->
                    writeDataEvent(writer, chunk)
                },
                { ex ->
                    if (!isClientDisconnect(ex)) {
                        writeErrorEvent(writer)
                    }
                    closeQuietly(writer)
                },
                {
                    closeQuietly(writer)
                }
            )
        subscriptionRef.set(subscription)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(InputStreamResource(inputStream))
    }

    private fun writeDataEvent(writer: java.io.Writer, chunk: String) {
        synchronized(writer) {
            writer.write("data: ")
            writer.write(chunk.replace("\n", "\ndata: "))
            writer.write("\n\n")
            writer.flush()
        }
    }

    private fun writeErrorEvent(writer: java.io.Writer) {
        try {
            synchronized(writer) {
                writer.write("event: error\n")
                writer.write("data: The AI assistant is temporarily unavailable.\n\n")
                writer.flush()
            }
        } catch (_: IOException) {
        }
    }

    private fun closeQuietly(writer: java.io.Writer) {
        try {
            writer.close()
        } catch (_: IOException) {
        }
    }

    private fun isClientDisconnect(ex: Throwable): Boolean =
        ex is IOException && ex.message?.lowercase()?.let { message ->
            "pipe closed" in message || "broken pipe" in message
        } == true

    private fun bufferedReply(conversationId: UUID, reply: Flowable<String>): ResponseEntity<Resource> {
        val response = AgentQueryResponse(
            conversationId = conversationId,
            reply = reply.collectInto(StringBuilder()) { builder, chunk -> builder.append(chunk) }
                .map { it.toString() }
                .blockingGet()
        )
        val body = objectMapper.writeValueAsBytes(response)

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_JSON)
            .contentLength(body.size.toLong())
            .body(ByteArrayResource(body))
    }
}

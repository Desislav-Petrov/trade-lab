package org.dpp.tradelab.agent.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.adk.events.Event
import com.google.adk.sessions.State
import org.dpp.tradelab.agent.exception.SessionPersistenceException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class AdkJsonSerializer {
    private val objectMapper = jacksonObjectMapper()

    fun serializeEvent(event: Event): String =
        try {
            serializableEvent(event).toJson().also {
                if (it.isBlank()) throw IllegalStateException("ADK event serialization returned no JSON.")
            }
        } catch (ex: Exception) {
            throw SessionPersistenceException("Could not serialize ADK event.", ex)
        }

    fun deserializeEvent(json: String): Event =
        try {
            val event = Event.fromJson(json)
            val actions = event.actions()
            val restoredDelta = actions.stateDelta().mapValues { (_, value) ->
                if (value == REMOVED_STATE_MARKER) State.REMOVED else value
            }
            if (restoredDelta == actions.stateDelta()) {
                event
            } else {
                event.toBuilder()
                    .actions(actions.toBuilder().stateDelta(restoredDelta).build())
                    .build()
            }
        } catch (ex: Exception) {
            throw SessionPersistenceException("Could not deserialize ADK event.", ex)
        }

    fun serializeState(state: Map<String, *>): String =
        try {
            objectMapper.writeValueAsString(sanitizeState(state))
        } catch (ex: Exception) {
            throw SessionPersistenceException("Could not serialize ADK state.", ex)
        }

    fun deserializeState(json: String): ConcurrentHashMap<String, Any> =
        try {
            val values: Map<String, Any?> = objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
            ConcurrentHashMap(sanitizeState(values))
        } catch (ex: Exception) {
            throw SessionPersistenceException("Could not deserialize ADK state.", ex)
        }

    fun sanitizeState(state: Map<String, *>): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        val visiting = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

        state.forEach { (key, value) ->
            if (key.startsWith(State.TEMP_PREFIX) || value == null || value === State.REMOVED) return@forEach
            normalize(value, visiting, 0).takeUnless { it === UNSAFE_STATE_VALUE }?.let { result[key] = it }
        }
        return result
    }

    private fun serializableEvent(event: Event): Event {
        val actions = event.actions()
        val stateDelta = LinkedHashMap<String, Any>()
        actions.stateDelta().forEach { (key, value) ->
            if (value === State.REMOVED) {
                stateDelta[key] = REMOVED_STATE_MARKER
            } else {
                sanitizeState(mapOf(key to value))[key]?.let { stateDelta[key] = it }
            }
        }
        return event.toBuilder()
            .actions(actions.toBuilder().stateDelta(stateDelta).build())
            .build()
    }

    private fun normalize(value: Any, visiting: MutableSet<Any>, depth: Int): Any? {
        if (depth > MAX_STATE_DEPTH) return null
        return when (value) {
            is String, is Boolean, is Byte, is Short, is Int, is Long, is BigInteger, is BigDecimal,
            is UUID, is Instant -> if (value is UUID || value is Instant) value.toString() else value
            is Float -> value.takeIf(Float::isFinite)
            is Double -> value.takeIf(Double::isFinite)
            is Map<*, *> -> {
                if (!visiting.add(value)) return null
                try {
                    val normalized = LinkedHashMap<String, Any?>()
                    value.forEach { (key, child) ->
                        if (key is String && child !== State.REMOVED) {
                            if (child == null) {
                                normalized[key] = null
                            } else {
                                val normalizedChild = normalize(child, visiting, depth + 1)
                                if (normalizedChild !== UNSAFE_STATE_VALUE) normalized[key] = normalizedChild
                            }
                        }
                    }
                    normalized
                } finally {
                    visiting.remove(value)
                }
            }
            is Iterable<*> -> {
                if (!visiting.add(value)) return null
                try {
                    val normalized = ArrayList<Any?>()
                    value.forEach { child ->
                        if (child == null) {
                            normalized.add(null)
                        } else if (child !== State.REMOVED) {
                            val normalizedChild = normalize(child, visiting, depth + 1)
                            if (normalizedChild !== UNSAFE_STATE_VALUE) normalized.add(normalizedChild)
                        }
                    }
                    normalized
                } finally {
                    visiting.remove(value)
                }
            }
            is Array<*> -> normalize(value.asList(), visiting, depth + 1)
            else -> null
        }
    }

    private companion object {
        const val MAX_STATE_DEPTH = 64
        val REMOVED_STATE_MARKER = mapOf("__trade_lab_adk_removed_state" to "v1")
        val UNSAFE_STATE_VALUE = Any()
    }
}

package com.sportshub.app.data.espn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Small JSON helpers that never throw, shared by the ESPN parsers.

internal val EspnJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
internal fun JsonObject.double(key: String): Double? = str(key)?.toDoubleOrNull()
internal fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
internal fun JsonElement.primitiveString(): String? = (this as? JsonPrimitive)?.contentOrNull

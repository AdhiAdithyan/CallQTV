package com.softland.callqtv.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/** Gson adapter for API fields that may be JSON numbers or numeric strings (e.g. `"1"`). */
class FlexibleIntDeserializer : JsonDeserializer<Int?> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Int? {
        if (json == null || json.isJsonNull) return null
        if (!json.isJsonPrimitive) return null
        val primitive = json.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asInt
            primitive.isString -> primitive.asString.trim().toIntOrNull()
            else -> null
        }
    }
}

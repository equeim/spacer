package org.equeim.spacer.retrofit

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializerOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

@OptIn(ExperimentalSerializationApi::class)
open class JsonConverterFactory(private val json: Json) : Converter.Factory() {
    private val jsonContentType by lazy(LazyThreadSafetyMode.PUBLICATION) { "application/json".toMediaType() }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val deserializer = json.serializersModule.serializerOrNull(type) ?: return null
        return Converter<ResponseBody, Any> { body ->
            body.byteStream().use { json.decodeFromStream(deserializer, it) }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = json.serializersModule.serializerOrNull(type) ?: return null
        return Converter<Any, RequestBody> {
            object : RequestBody() {
                override fun contentType() = jsonContentType
                override fun writeTo(sink: BufferedSink) {
                    json.encodeToStream(serializer, it, sink.outputStream())
                }
            }
        }
    }
}

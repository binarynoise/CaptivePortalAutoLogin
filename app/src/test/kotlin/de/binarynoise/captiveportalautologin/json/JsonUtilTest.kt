package de.binarynoise.captiveportalautologin.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.json.JSONArray as OrgJSONArray
import org.json.JSONObject as OrgJSONObject

class JsonUtilTest {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }
    
    @ParameterizedTest
    @MethodSource("toJsonObjectTestCases")
    fun `toJsonObject should retain structural equality`(jsonString: String) {
        val original = OrgJSONObject(jsonString)
        val converted = OrgJSONObject(json.encodeToString(original.toJsonObject()))
        assertEquals(original.toString(), converted.toString(), "Failed for: $jsonString")
    }
    
    @ParameterizedTest
    @MethodSource("toJsonArrayTestCases")
    fun `toJsonArray should retain structural equality`(jsonString: String) {
        val original = OrgJSONArray(jsonString)
        val converted = OrgJSONArray(json.encodeToString(original.toJsonArray()))
        assertEquals(original.toString(), converted.toString(), "Failed for: $jsonString")
    }
    
    @ParameterizedTest
    @MethodSource("convertJsonElementTestCases")
    fun `convertJsonElement should retain structural equality`(arg: Any?, element: JsonElement) {
        val converted = convertJsonElement(arg)
        assertEquals(element, converted)
    }
    
    companion object {
        @JvmStatic
        fun toJsonObjectTestCases(): List<Arguments> {
            return listOf(
                Arguments.of("{}"),
                Arguments.of("""{"key":"value"}"""),
                Arguments.of("""{"key":123}"""),
                Arguments.of("""{"key":true}"""),
                Arguments.of("""{"key":false}"""),
                Arguments.of("""{"key":null}"""),
                Arguments.of("""{"key1":"value1","key2":"value2"}"""),
                Arguments.of("""{"nested":{"key":"value"}}"""),
                Arguments.of("""{"array":[1,2,3]}"""),
                Arguments.of("""{"complex":{"nested":{"array":[{"a":1},{"b":2}]},"key":"value"}}"""),
                Arguments.of("""{"number":42.5}"""),
                Arguments.of("""{"emptyString":""}"""),
                Arguments.of("""{"escaped":"\"quoted\""}"""),
                Arguments.of("""{"unicode":"\u0048\u0065\u006c\u006c\u006f"}""")
            )
        }
        
        @JvmStatic
        fun toJsonArrayTestCases(): List<Arguments> {
            return listOf(
                Arguments.of("[]"),
                Arguments.of("[1]"),
                Arguments.of("[1,2,3]"),
                Arguments.of("""["a","b","c"]"""),
                Arguments.of("[true,false]"),
                Arguments.of("[null]"),
                Arguments.of("""[1,"two",true,false,null]"""),
                Arguments.of("""[{"key":"value"}]"""),
                Arguments.of("[[1,2],[3,4]]"),
                Arguments.of("""[{"nested":{"key":"value"}},[1,2,3]]"""),
                Arguments.of("[42.5]"),
                Arguments.of("""[""]"""),
                Arguments.of("""["\"quoted\""]"""),
                Arguments.of("""[{"a":1},{"b":2},{"c":3}]""")
            )
        }
        
        @JvmStatic
        fun convertJsonElementTestCases(): List<Arguments> {
            return listOf(
                Arguments.of(1, JsonPrimitive(1)),
                Arguments.of("test", JsonPrimitive("test")),
                Arguments.of(true, JsonPrimitive(true)),
                Arguments.of(false, JsonPrimitive(false)),
                Arguments.of(null, JsonNull),
                Arguments.of(OrgJSONObject.NULL, JsonNull),
                Arguments.of("null", JsonPrimitive("null")),
                Arguments.of(42.5, JsonPrimitive(42.5)),
                Arguments.of("", JsonPrimitive("")),
                Arguments.of("\"quoted\"", JsonPrimitive("\"quoted\"")),
                Arguments.of("""{"key":"value"}""", JsonPrimitive("""{"key":"value"}""")),
                Arguments.of(OrgJSONObject(), JsonObject(emptyMap())),
            )
        }
    }
}

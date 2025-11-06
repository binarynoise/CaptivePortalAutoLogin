package de.binarynoise.rhino

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class RhinoParserTest {
    private val parser = RhinoParser(debug = true)
    
    @ParameterizedTest
    @MethodSource("assignmentTestCases")
    fun `findAssignment should find correct values`(js: String, path: String, expected: String?, count: Int) {
        val assignments = parser.parseAssignments(js)
        assertEquals(count, assignments.size, "Expected $count assignments but got ${assignments.size} in JS: $js")
        assertEquals(expected, assignments[path], "Expected '$expected' for path '$path' in JS: $js")
    }
    
    companion object {
        @JvmStatic
        fun assignmentTestCases(): List<Arguments> {
            return buildList {
                // non-existent
                +Arguments.of("var a = 1;", "non.existent", null, 1)
                
                // Simple assignments
                +Arguments.of("var a = 1;", "a", "1", 1)
                +Arguments.of("var a = 'test';", "a", "test", 1)
                +Arguments.of("let a = null;", "a", "null", 1)
                
                // Object literals
                +Arguments.of("var a = { b: 2 };", "a.b", "2", 2)
                +Arguments.of("var a = { b: { c: 'test' } };", "a.b.c", "test", 3)
                
                // Arrays
                +Arguments.of("var a = [1, 2, 3];", "a", "[1, 2, 3]", 1)
                
                // Null/undefined handling
                +Arguments.of("var a = null;", "a", "null", 1)
                +Arguments.of("var a = undefined;", "a", "undefined", 1)
                
                // Conditional assignment
                +Arguments.of("var a = b || {};", "a", "b || {}", 1)
                
                // String keys with spaces
                +Arguments.of("var a = { 'key with spaces': 'value' };", "a.key with spaces", "value", 2)
                
                // Multiple assignments
                +multiArgs(
                    """
                        var x = 1;
                        var y = { z: 2 };
                    """,
                    3,
                    "x" to "1",
                    "y.z" to "2",
                )
                
                // Nested objects
                +multiArgs(
                    """
                    var config = {
                        db: {
                            host: 'localhost',
                            port: 5432
                        }
                    };
                    """,
                    4,
                    "config.db.host" to "localhost",
                    "config.db.port" to "5432",
                )
                
                +multiArgs(
                    """
                        var config = {
                            api: {
                                baseUrl: 'https://api.example.com',
                                endpoints: {
                                    users: '/users',
                                    posts: '/posts'
                                },
                                version: 1
                            },
                            debug: false
                        };
                    """,
                    8,
                    "config.api.baseUrl" to "https://api.example.com",
                    "config.api.endpoints.users" to "/users",
                    "config.api.endpoints.posts" to "/posts",
                    "config.api.version" to "1",
                    "config.debug" to "false"
                )
                
                // indirect assignments
                +multiArgs(
                    """
                        var a = 1;
                        var b = a;
                        var d = b;
                    """,
                    3,
                    "a" to "1",
                    "b" to "a",
                    "d" to "b",
                )
                
                // function calls
                +Arguments.of("""a("test");""", "a.0", "test", 1)
                +Arguments.of("""a.b("test");""", "a.b.0", "test", 1)
            }
        }
        
        private fun multiArgs(js: String, count: Int, vararg expected: Pair<String, String>): List<Arguments> {
            return expected.map {
                Arguments.of(js.trimIndent(), it.first, it.second, count)
            }
        }
    }
}

context(list: MutableList<T>)
operator fun <T> T.unaryPlus() {
    list.add(this)
}

context(list: MutableList<T>)
operator fun <T, L : List<T>> L.unaryPlus() {
    list.addAll(this)
}

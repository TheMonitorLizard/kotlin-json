/*
 * Copyright 2018 Kaidan Gustave
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("MemberVisibilityCanBePrivate", "LiftReturnOrAssignment", "Unused")

package me.kgustave.json.internal

import me.kgustave.json.exceptions.JSException
import me.kgustave.json.options.JSParsingOptions
import java.io.*

/**
 * Tokener used to read various [InputStreams][InputStream] and [Readers][Reader].
 *
 * This is only exposed publicly for the usage of other modules that are part
 * of the kotlin-json library, and has no use outside of said libraries.
 *
 * @author Kaidan Gustave
 * @since  1.0
 */
internal class JSTokener(
    reader: Reader,
    private val options: JSParsingOptions = JSParsingOptions
): AutoCloseable by reader, Iterator<Char> {
    @Deprecated(
        message = "Deprecated to maintain binary compatibility.",
        replaceWith = ReplaceWith("JSTokener(InputStream, JSParsingOptions)"),
        level = DeprecationLevel.HIDDEN
    )
    constructor(reader: Reader): this(reader, JSParsingOptions)

    @Deprecated(
        message = "Deprecated to maintain binary compatibility.",
        replaceWith = ReplaceWith("JSTokener(InputStream, JSParsingOptions)"),
        level = DeprecationLevel.HIDDEN
    )
    constructor(inputStream: InputStream): this(InputStreamReader(inputStream), JSParsingOptions)

    @Deprecated(
        message = "Deprecated to maintain binary compatibility.",
        replaceWith = ReplaceWith("JSTokener(String, JSParsingOptions)"),
        level = DeprecationLevel.HIDDEN
    )
    constructor(string: String): this(StringReader(string), JSParsingOptions)

    constructor(inputStream: InputStream, options: JSParsingOptions = JSParsingOptions):
        this(InputStreamReader(inputStream), options)
    constructor(string: String, options: JSParsingOptions = JSParsingOptions):
        this(StringReader(string), options)

    companion object {
        fun dehexchar(c: Char) = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c.toInt() - ('A'.toInt() - 10)
            in 'a'..'f' -> c.toInt() - ('a'.toInt() - 10)
            else -> -1
        }
    }

    private val reader = if(reader.markSupported()) reader else BufferedReader(reader)

    private var character = 1L          // The character we are at on the current line
    private var eof = false             // If we are at the end of the file yet
    private var index = 0L              // The index of the entire reader we are at
    private var line = 1L               // The line we are on
    private var previous = 0.toChar()   // The previous character read
    private var usePrevious = false     // Whether we are using the previous character

    val isAtEnd get() = eof && !usePrevious

    fun back() {
        checkJson(!usePrevious) { "Stepping back two steps is not supported" }

        index -= 1
        character -= 1
        usePrevious = true
        eof = false
    }

    fun next(c: Char): Char {
        val n = next()
        if(n != c) syntaxError("Expected '$c' and instead saw '$n'")
        return n
    }

    fun next(n: Int): String {
        if(n == 0) return ""
        val chars = CharArray(n) {
            val next = next()
            if(isAtEnd) syntaxError("Substring bounds error")
            return@CharArray next
        }
        return String(chars)
    }

    fun nextClean(): Char {
        var c: Char
        while(true) {
            c = next()
            if(c.toInt() == 0 || c > ' ')
                return c
        }
    }

    fun nextString(quote: Char): String {
        return buildString {
            var c: Char
            while(true) {
                c = next()
                when(c) {
                    0.toChar(), '\n', '\r' -> syntaxError("Unterminated string")

                    '\\' -> {
                        c = next()
                        when(c) {
                            'b' -> append('\b')
                            't' -> append('\t')
                            'n' -> append('\n')
                            'f' -> append('\u000C') // Escape for \f in kotlin isn't supported
                            'r' -> append('\r')

                            'u' -> try {
                                append(Integer.parseInt(next(4), 16).toChar())
                            } catch (e: NumberFormatException) {
                                syntaxError("Illegal escape", e)
                            }

                            '"', '\'', '\\', '/' -> append(c)

                            else -> syntaxError("Illegal escape")
                        }
                    }

                    else -> {
                        if(c == quote)
                            return@buildString
                        append(c)
                    }
                }
            }
        }
    }

    fun nextTo(delimiter: Char): String {
        val str = buildString {
            var c: Char
            while(true) {
                c = next()

                if(c == delimiter || c.toInt() == 0 || c == '\n' || c == '\r') {
                    if(c.toInt() != 0)
                        back()
                    break
                }

                append(c)
            }
        }
        return str.trim()
    }

    fun nextTo(delimiters: String): String {
        val str = buildString {
            var c: Char
            while(true) {
                c = next()
                if(delimiters.indexOf(c) >= 0 || c.toInt() == 0 || c == '\n' || c == '\r') {
                    if(c.toInt() != 0) {
                        back()
                    }
                    break
                }
                append(c)
            }
        }

        return str.trim()
    }

    fun nextValue(): Any? {
        var c = nextClean()
        val string: String

        when(c) {
            '"', '\'' -> return nextString(c)

            '{' -> {
                back()
                return JSObjectImpl(this)
            }

            '[' -> {
                back()
                return JSArrayImpl(this)
            }
        }

        val s = buildString {
            while(c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
                append(c)
                c = next()
            }
        }

        back()

        string = s.trim()

        if(string.isEmpty()) syntaxError("Missing value")

        return stringToValue(string)
    }

    fun skipTo(to: Char): Char {
        var c: Char
        try {
            val startIndex = index
            val startCharacter = character
            val startLine = line

            reader.mark(1000000)

            do {
                c = this.next()
                if(c.toInt() == 0) {
                    reader.reset()
                    index = startIndex
                    character = startCharacter
                    line = startLine
                    return c
                }
            } while(c != to)

        } catch(exception: IOException) {
            throw JSException(cause = exception)
        }

        back()
        return c
    }

    override operator fun hasNext(): Boolean {
        next()
        if(isAtEnd) {
            return false
        } else {
            back()
            return true
        }
    }

    override operator fun next(): Char {
        var c: Int
        if(usePrevious) {
            usePrevious = false
            c = previous.toInt()
        } else {
            c = tryWrap { reader.read() }

            if(c <= 0) {
                eof = true
                c = 0
            }
        }

        // Next index
        index += 1

        when {
            // newline
            previous == '\r' -> {
                line += 1
                character = (if (c == '\n'.toInt()) 0 else 1).toLong()
            }
            // newline
            c == '\n'.toInt() -> {
                line += 1
                character = 0
            }
            // next character
            else -> character += 1
        }

        // set previous
        previous = c.toChar()

        // return previous
        return previous
    }

    override fun toString() = "at $index [character $character line $line]"
}
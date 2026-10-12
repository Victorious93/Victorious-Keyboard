/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.florisboard.lib.kotlin

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


fun String.safeSubstring(startIndex: Int): String {
    return try {
        this.substring(startIndex)
    } catch (_: IndexOutOfBoundsException) {
        ""
    }
}

fun String.safeSubstring(startIndex: Int, endIndex: Int): String {
    return try {
        this.substring(startIndex, endIndex)
    } catch (_: IndexOutOfBoundsException) {
        ""
    }
}

private const val CURLY_ARG_OPEN = '{'
private const val CURLY_ARG_CLOSE = '}'

typealias CurlyArg = Pair<String, Any?>

fun String.curlyFormat(argValueFactory: (argName: String) -> String?): String {
    contract {
        callsInPlace(argValueFactory, InvocationKind.UNKNOWN)
    }
    val sb = StringBuilder(this)
    var curlyOpenIndex = sb.indexOf(CURLY_ARG_OPEN)
    while (curlyOpenIndex >= 0) {
        val nextCurlyOpenIndex = sb.indexOf(CURLY_ARG_OPEN, curlyOpenIndex + 1)
        val nextCurlyCloseIndex = sb.indexOf(CURLY_ARG_CLOSE, curlyOpenIndex + 1)
        if (nextCurlyCloseIndex < 0) break
        if (nextCurlyOpenIndex in 0 until nextCurlyCloseIndex) {
            curlyOpenIndex = nextCurlyOpenIndex
        }
        val argName = sb.substring(curlyOpenIndex + 1, nextCurlyCloseIndex)
        val argValue = argValueFactory(argName)
        if (argValue != null) {
            sb.replace(curlyOpenIndex, nextCurlyCloseIndex + 1, argValue)
            // Continue after the inserted text: a value is data and must never be scanned for placeholders again
            // (a value containing its own placeholder would otherwise grow the buffer forever).
            curlyOpenIndex = sb.indexOf(CURLY_ARG_OPEN, curlyOpenIndex + argValue.length)
        } else {
            curlyOpenIndex = sb.indexOf(CURLY_ARG_OPEN, curlyOpenIndex + 1)
        }
    }
    return sb.toString()
}

fun String.curlyFormat(vararg args: CurlyArg): String {
    return this.curlyFormat(args.asList())
}

fun String.curlyFormat(args: List<CurlyArg>): String {
    if (args.isEmpty()) return this
    // Same precedence as before: the first argument that claims a name wins; arg n is also addressable as {n}.
    val values = HashMap<String, String>()
    for ((n, arg) in args.withIndex()) {
        val (argName, argValue) = arg
        val text = argValue.toString()
        values.putIfAbsent(n.toString(), text)
        if (argName.isNotBlank()) values.putIfAbsent(argName, text)
    }
    return curlyFormat { name -> values[name] }
}

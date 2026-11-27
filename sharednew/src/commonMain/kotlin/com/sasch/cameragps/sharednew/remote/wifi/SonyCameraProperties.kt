package com.sasch.cameragps.sharednew.remote.wifi

internal data class SonyCameraProperty(val enabled: Boolean, val value: Long?, val stringValue: String? = null) {
    override fun toString() = "SonyCameraProperty(enabled=$enabled, value=$value, stringValue=redacted)"
}

/** Unselected strings and arrays are consumed without decoding. */
internal object SonyCameraProperties {
    fun parse(
        data: ByteArray,
        controlCodes: Set<Int>,
        selected: Set<Int>,
        selectedStrings: Set<Int> = emptySet(),
        describe: ((String) -> Unit)? = null,
    ): Map<Int, SonyCameraProperty> {
        var offset = 0
        fun take(size: Int): Int {
            require(size >= 0 && size <= data.size - offset) { "Truncated camera properties" }
            return offset.also { offset += size }
        }

        fun number(size: Int): Long {
            val start = take(size)
            return (0 until size).fold(0L) { value, i -> value or ((data[start + i].toLong() and 255) shl (8 * i)) }
        }

        fun scalarSize(kind: Int) = when (kind) {
            1, 2 -> 1
            3, 4 -> 2
            5, 6 -> 4
            7, 8 -> 8
            9, 10 -> 16
            else -> error("Unsupported camera property type")
        }

        fun stringValue(): String {
            val count = number(1).toInt()
            if (count == 0) return ""
            val text = buildString { repeat(count - 1) { append(number(2).toInt().toChar()) } }
            require(number(2) == 0L && '\u0000' !in text) { "Malformed camera property string" }
            return text
        }

        fun value(kind: Int): Long? = when {
            kind == 0xffff -> {
                take(number(1).toInt() * 2); null
            }

            kind in 0x4001..0x400a -> {
                val count = number(4)
                require(count in 0..4096) { "Camera property array too large" }
                take(count.toInt() * scalarSize(kind - 0x4000)); null
            }

            else -> scalarSize(kind).let { size ->
                if (size <= 8) number(size) else {
                    take(size); null
                }
            }
        }

        val count = number(8)
        require(count in 0..4096) { "Too many camera properties" }
        val properties = mutableMapOf<Int, SonyCameraProperty>()
        val seen = mutableSetOf<Int>()
        repeat(count.toInt()) {
            val code = number(2).toInt()
            val kind = number(2).toInt()
            val access = number(1)
            val enabled = number(1)
            if (code in controlCodes) {
                take(4)
                require(number(1) == 0L) { "Unsupported camera control form" }
            } else {
                value(kind)
                val text = if (kind == 0xffff && code in selectedStrings) stringValue() else null
                val current = if (text == null) value(kind) else null
                val form = number(1).toInt()
                val numeric = kind in 1..8 && code in selected && describe != null
                val choices = if (numeric) mutableListOf<String>() else null
                when (form) {
                    0 -> Unit
                    1 -> repeat(3) {
                        val v = value(kind)
                        choices?.add(v.toString())
                    }
                    2 -> repeat(2) {
                        val options = number(2).toInt()
                        require(options <= 4096) { "Too many camera property values" }
                        choices?.add("count=$options")
                        repeat(options) { index ->
                            val v = value(kind)
                            if (index < 96) choices?.add(v.toString())
                        }
                    }

                    else -> error("Unsupported camera property form")
                }
                describe?.invoke(buildString {
                    append("0x${code.toString(16)} type=$kind access=$access enabled=$enabled form=$form")
                    if (numeric) append(" raw=$current options=${choices.orEmpty().joinToString(",")}")
                })
                if (code in selected || code in selectedStrings) {
                    require(seen.add(code)) { "Duplicate camera property" }
                    properties[code] = SonyCameraProperty(enabled in 1L..2L, current, text)
                }
            }
        }
        require(offset == data.size) { "Trailing camera property bytes" }
        return properties
    }
}

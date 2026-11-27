package com.sasch.cameragps.sharednew.remote.wifi

internal data class SonyCameraProperty(
    val enabled: Boolean,
    val value: Long?,
    val stringValue: String? = null,
    val dataType: Int = 0,
    val access: Int = 0,
    val enabledState: Int = if (enabled) 1 else 0,
    val form: Int = 0,
    val currentValues: List<Long> = emptyList(),
    val supportedValues: List<Long> = emptyList(),
) {
    /** Currently writable on the camera: RW access, enabled=1 (not display-only 2), and legal values exist. */
    val writable: Boolean
        get() = access == 1 && enabledState == 1 && currentValues.isNotEmpty()

    override fun toString() =
        "SonyCameraProperty(enabled=$enabled, state=$enabledState, access=$access, type=$dataType, form=$form, value=$value, choices=${currentValues.size}, stringValue=redacted)"
}

/** Unselected strings and arrays are consumed without decoding. */
internal object SonyCameraProperties {
    fun scalarByteSize(dataType: Int): Int? = when (dataType) {
        1, 2 -> 1
        3, 4 -> 2
        5, 6 -> 4
        7, 8 -> 8
        else -> null
    }

    /** Encodes a numeric scalar value in little-endian wire order for `0x9205`. */
    fun encodeScalar(dataType: Int, value: Long): ByteArray? {
        val size = scalarByteSize(dataType) ?: return null
        return ByteArray(size) { i -> ((value ushr (8 * i)) and 0xff).toByte() }
    }

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
                val wantProperty = code in selected || code in selectedStrings
                val numericSelected = kind in 1..8 && code in selected
                val describeChoices = if (numericSelected && describe != null) mutableListOf<String>() else null
                val currentList = if (numericSelected) mutableListOf<Long>() else null
                val supportedList = if (numericSelected) mutableListOf<Long>() else null
                when (form) {
                    0 -> Unit
                    1 -> repeat(3) {
                        val v = value(kind)
                        if (v != null) currentList?.add(v)
                        describeChoices?.add(v.toString())
                    }
                    2 -> repeat(2) { listIndex ->
                        val options = number(2).toInt()
                        require(options <= 4096) { "Too many camera property values" }
                        describeChoices?.add("count=$options")
                        val target = if (listIndex == 0) currentList else supportedList
                        repeat(options) { index ->
                            val v = value(kind)
                            if (v != null) target?.add(v)
                            if (index < 96) describeChoices?.add(v.toString())
                        }
                    }

                    else -> error("Unsupported camera property form")
                }
                describe?.invoke(buildString {
                    append("0x${code.toString(16)} type=$kind access=$access enabled=$enabled form=$form")
                    if (numericSelected) append(" raw=$current options=${describeChoices.orEmpty().joinToString(",")}")
                })
                if (wantProperty) {
                    require(seen.add(code)) { "Duplicate camera property" }
                    properties[code] = SonyCameraProperty(
                        enabled = enabled in 1L..2L,
                        value = current,
                        stringValue = text,
                        dataType = kind,
                        access = access.toInt(),
                        enabledState = enabled.toInt(),
                        form = form,
                        currentValues = currentList?.toList().orEmpty(),
                        supportedValues = supportedList?.toList().orEmpty(),
                    )
                }
            }
        }
        require(offset == data.size) { "Trailing camera property bytes" }
        return properties
    }
}

package com.enverolab.comandas.domain

/** Texto estructurado: tabulador separa descripción e importe; ^ centra un encabezado. */
object Ticket {
    fun pair(label: String, value: String) =
        label.replace('\t', ' ') + "\t" + value.replace('\t', ' ')

    fun center(value: String) = "^" + value

    private fun wrap(text: String, width: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var rest = text
        while (rest.length > width) {
            val space = rest.lastIndexOf(' ', width).takeIf { it > 0 } ?: width
            out += rest.take(space)
            rest = rest.drop(space).trimStart()
        }
        out += rest
        return out
    }

    fun render(text: String, width: Int = 32): String {
        require(width in 24..80)
        return text
            .lines()
            .flatMap { line ->
                when {
                    line == "---" -> listOf("-".repeat(width))
                    line.startsWith('^') ->
                        wrap(line.drop(1), width).map { " ".repeat((width - it.length) / 2) + it }
                    '\t' in line -> {
                        val left = line.substringBefore('\t')
                        val right = line.substringAfter('\t')
                        if (right.length >= width)
                            wrap(left, width) + wrap(right, width).map { it.padStart(width) }
                        else {
                            val room = width - right.length - 1
                            val labels = wrap(left, room.coerceAtLeast(1))
                            labels.dropLast(1) +
                                listOf(labels.last().padEnd(width - right.length) + right)
                        }
                    }
                    else -> wrap(line, width)
                }
            }
            .joinToString("\n")
    }
}

package de.binarynoise.liberator

import java.util.UUID

fun randomEmail(domain: String = "example.com"): String {
    val uuid = UUID.randomUUID()
    return "$uuid@$domain"
}

fun dramaNumber(
    prefix: String = "+49",
    landline: Boolean = false,
    mobile: Boolean = true,
): String {
    fun generateNumbers(base: String, range: IntRange): List<String> {
        val padLength = range.last.toString().length
        return range.map { it.toString() }.map { it.padStart(padLength, '0') }.map { "$base$it" }
    }
    
    val numberPool = mutableListOf<String>()
    if (landline) {
        numberPool.addAll(generateNumbers("3023125", IntRange(0, 999)))
        numberPool.addAll(generateNumbers("6990009", IntRange(0, 999)))
        numberPool.addAll(generateNumbers("4066969", IntRange(0, 999)))
        numberPool.addAll(generateNumbers("2214710", IntRange(0, 999)))
        numberPool.addAll(generateNumbers("8999998", IntRange(0, 999)))
    }
    if (mobile) {
        numberPool.addAll(
            listOf(
                "15228817386",
                "15228895456",
                "15254599371",
                "1729925904",
                "1729968532",
                "1729973185",
                "1729973186",
                "1729980752",
                "1749091317",
                "1749464308",
            )
        )
        numberPool.addAll(generateNumbers("17139200", IntRange(0, 99)))
        numberPool.addAll(generateNumbers("176040690", IntRange(0, 99)))
    }
    val number = numberPool.random()
    return "$prefix$number"
}

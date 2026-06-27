package de.binarynoise.liberator

import java.util.UUID

fun randomEmail(domain: String = "example.com"): String {
    val uuid = UUID.randomUUID()
    return "$uuid@$domain"
}

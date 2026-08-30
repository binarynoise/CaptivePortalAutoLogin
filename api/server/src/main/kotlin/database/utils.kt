package de.binarynoise.captiveportalautologin.server.database

import io.ktor.http.URLBuilder


fun String.getUrlDomain(): String? = if (isNotEmpty()) URLBuilder(urlString = this).host else null

fun String.getMajorVersion(): Int = this.split('-', '+').first().toInt()

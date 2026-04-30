package de.binarynoise.captiveportalautologin.server.routes.stats

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Comparator.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import de.binarynoise.captiveportalautologin.server.routes.stats.TableHeaderColumn.SortDirection
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.netty.util.NetUtil
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.GroupWithKey
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.columnNames
import org.jetbrains.kotlinx.dataframe.api.count
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.getValue
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.mapToRows
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.select
import org.jetbrains.kotlinx.dataframe.api.sortWith
import org.jetbrains.kotlinx.dataframe.api.sumOf
import org.jetbrains.kotlinx.dataframe.api.take
import org.jetbrains.kotlinx.dataframe.api.toDataRow
import org.jetbrains.kotlinx.dataframe.api.toMap

@DataSchema
data class ColumnDefinition(
    val name: String,
    val displayName: String,
    val comparator: Comparator<Any>,
)

@DataSchema
data class PreFilterDefinition(
    val name: String,
    val displayName: String,
    val dataFrameProvider: suspend () -> DataFrame<*>,
)

suspend fun generateTableData(
    call: RoutingCall,
    columnDefinitions: DataFrame<ColumnDefinition>,
    groupDefault: Set<String>,
    preFilterDefinitions: List<PreFilterDefinition>,
): TableData {
    val preFilterDefinitionMap = preFilterDefinitions.associateBy { it.name }
    
    val preFilter = call.request.queryParameters.getSelectedCheckboxes("preFilter-").singleOrNull()
    var dataFrame: DataFrame<*> =
        (preFilterDefinitionMap.get(preFilter) ?: preFilterDefinitionMap.getValue("all")).dataFrameProvider.invoke()
    
    dataFrame = dataFrame.select(columns = columnDefinitions.rows().map { it.name }.toTypedArray())
    
    val originalColumnDefinitions = columnDefinitions
    var columnDefinitions = originalColumnDefinitions
    
    val filterColumns = call.request.queryParameters.getSelectedCheckboxes("filterBy-")
    val filterOptions = columnDefinitions.rows().map { colDef ->
        TableOption(
            colDef.name,
            colDef.displayName,
            colDef.name in filterColumns,
        )
    }
    val filterOptionsEmpty = filterOptions.none { it.selected }
    val filterString = call.request.queryParameters["filter"]?.takeUnless { it.isBlank() }
    when {
        filterString == null -> {}
        filterString.contains("=") -> {
            val filterParameters = parseQueryString(filterString).toMap().mapValues { (_, value) -> value.single() }
            dataFrame = dataFrame.filter { row ->
                filterParameters.all { (key, value) -> row[key]?.toString() == value }
            }
        }
        else -> {
            dataFrame = dataFrame.filter { row ->
                filterOptions.any { (name, _, active) ->
                    (active || filterOptionsEmpty) && row[name].toString().contains(filterString, ignoreCase = true)
                }
            }
        }
    }
    
    var groupColumns = call.request.queryParameters.getSelectedCheckboxes("group-")
    val groupOptions = columnDefinitions.rows().map { it: DataRow<ColumnDefinition> ->
        TableOption(it.name, it.displayName, it.name in groupColumns)
    }
    if (groupColumns.isEmpty()) {
        // use groupDefault as fallback but keep the order of columnDefinitions
        groupColumns = columnDefinitions.rows().map { it.name }.intersect(groupDefault).toList()
    }
    
    if (groupColumns.isNotEmpty()) {
        columnDefinitions =
            columnDefinitions.filter { colDef -> groupColumns.contains(colDef.name) || colDef.name == "count" }
        val hasCount = columnDefinitions.rows().any { it.name == "count" }
        if (!hasCount) {
            columnDefinitions =
                columnDefinitions.append(ColumnDefinition("count", "Count", Comparators.RegularComparator))
        }
        dataFrame = dataFrame.groupBy(cols = groupColumns.toTypedArray()).mapToRows { gwk: GroupWithKey<Any?, Any?> ->
            val mapped = gwk.key.toMap().toMutableMap()
            if (hasCount) {
                mapped["count"] = gwk.group["count"].sumOf { it as Int }
            } else {
                mapped["count"] = gwk.group.count()
            }
            mapped.toDataRow()
        }
    }
    
    val sortParam = call.request.queryParameters["sort"] ?: "timestamp-desc"
    val sortDirection = SortDirection.fromSortKey("", sortParam)
    var sortColumnKey = sortParam.substringBeforeLast("-")
    var sortColumn = columnDefinitions.rows().find { it.name == sortColumnKey }
    if (sortColumn == null) {
        sortColumn = columnDefinitions.rows().first()
        sortColumnKey = sortColumn.name
    }
    val selector: (DataRow<*>) -> Any = {
        it.getValue(sortColumn.name)
    }
    dataFrame = if (sortDirection == SortDirection.Ascending) {
        dataFrame.sortWith(compareBy(nullsLast(sortColumn.comparator), selector))
    } else {
        dataFrame.sortWith(compareByDescending(nullsFirst(sortColumn.comparator), selector))
    }
    
    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
    dataFrame = dataFrame.take(limit)
    
    val rows = dataFrame.rows().map { row ->
        val url = buildUrl {
            parameters["filter"] =
                row.toMap().entries.filterNot { (key, _) -> key == "count" }.joinToString("&") { (key, value) ->
                    val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                    val encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)
                    "${encodedKey}=${encodedValue}"
                }
            
            originalColumnDefinitions.rows().forEach { definition ->
                if (definition.name != "count" && definition.name !in row.columnNames()) {
                    parameters["group-${definition.name}"] = "on"
                }
            }
        }
        TableRow(
            columns = row.toMap().values.map { it.toString() },
            url = "?" + url.encodedQuery,
        )
    }
    val tableHeader = TableHeader(
        columns = columnDefinitions.rows().map { colDef ->
            TableHeaderColumn(
                colDef.name,
                colDef.displayName,
                if (colDef.name == sortColumnKey) sortDirection else null,
            )
        })
    
    
    val tableData = TableData(
        preFilterOptions = preFilterDefinitionMap.map { (k, v) -> TableOption(k, v.displayName, k == preFilter) },
        groupOptions = groupOptions,
        filterOptions = filterOptions,
        filterValue = filterString,
        header = tableHeader,
        rows = rows,
        limit = limit,
    )
    
    return tableData
}

private fun Parameters.getSelectedCheckboxes(prefix: String): List<String> = this.toMap()
    .mapValues { (_, value) -> value.single() }
    .filter { (key, value) -> key.startsWith(prefix) && value == "on" }.keys.map { key -> key.removePrefix(prefix) }


data class TableData(
    val preFilterOptions: List<TableOption>,
    val groupOptions: List<TableOption>,
    val filterOptions: List<TableOption>,
    val filterValue: String?,
    val header: TableHeader,
    val rows: List<TableRow>,
    val limit: Int,
) : MappableData

data class TableOption(
    val name: String,
    val displayName: String,
    val selected: Boolean,
) : MappableData

data class TableHeader(
    val columns: List<TableHeaderColumn>,
) : MappableData

data class TableHeaderColumn(
    val name: String,
    val displayName: String,
    val sorted: SortDirection?,
) : MappableData {
    enum class SortDirection {
        Ascending, Descending;
        
        companion object {
            fun fromBooleans(asc: Boolean, desc: Boolean): SortDirection? {
                return when {
                    asc -> Ascending
                    desc -> Descending
                    else -> null
                }
            }
            
            fun fromSortKey(prefix: String, sortKey: String): SortDirection? {
                return if (!sortKey.startsWith(prefix)) null
                else fromBooleans(sortKey.endsWith("-asc"), sortKey.endsWith("-desc"))
            }
        }
    }
    
    override fun toMap(): Map<String, Any?> {
        return super.toMap() + mapOf(
            "sorted-asc" to (sorted == SortDirection.Ascending),
            "sorted-desc" to (sorted == SortDirection.Descending),
        )
    }
}

data class TableRow(
    val columns: List<String>,
    val url: String,
) : MappableData

interface MappableData {
    fun toMap(): Map<String, Any?> =
        this::class.memberProperties.filterIsInstance<KProperty1<Any, *>>().associate { property ->
            val value = property.get(this)
            property.name to when (value) {
                is List<*> -> value.map { if (it is MappableData) it.toMap() else it }
                is MappableData -> value.toMap()
                else -> value
            }
        }
}

object Comparators {
    object DomainComparator : Comparator<Any> {
        override fun compare(o1: Any, o2: Any): Int {
            o1 as String
            o2 as String
            if (o1 == o2) return 0
            
            fun isIP(domain: String): Boolean = NetUtil.isValidIpV4Address(domain) || NetUtil.isValidIpV6Address(domain)
            
            fun compareArray(a: List<String>, b: List<String>): Int {
                val minLength = minOf(a.size, b.size)
                for (i in 0 until minLength) {
                    val cmp = a[i].compareTo(b[i])
                    if (cmp != 0) return cmp
                }
                return a.size.compareTo(b.size)
            }
            
            fun <T> List<T>.dropOneButNotLast() = if (size <= 1) this else this.subList(1, size)
            
            val isIPA = isIP(o1)
            val isIPB = isIP(o2)
            val partsA = o1.split('.')
            val partsB = o2.split('.')
            
            return when {
                isIPA && isIPB -> compareArray(partsA, partsB)
                isIPA -> 1
                isIPB -> -1
                else -> {
                    // Compare domain components excluding TLD
                    val domainA = partsA.reversed().dropOneButNotLast()
                    val domainB = partsB.reversed().dropOneButNotLast()
                    val domainComparison = compareArray(domainA, domainB)
                    
                    if (domainComparison != 0) {
                        domainComparison
                    } else {
                        // If domains are equal, compare by TLD
                        val tldA = partsA.last()
                        val tldB = partsB.last()
                        tldA.compareTo(tldB)
                    }
                }
            }
        }
    }
    
    object VersionComparator : Comparator<Any> {
        val pattern = Regex("^(\\d+)([+-])([a-f0-9]+)(-\\d{8})(-dev)?$")
        
        override fun compare(o1: Any, o2: Any): Int {
            o1 as String
            o2 as String
            
            if (o1 == o2) return 0
            
            val match1 = pattern.matchEntire(o1)
            val match2 = pattern.matchEntire(o2)
            
            if (match1 == null || match2 == null) return o1.compareTo(o2)
            
            return comparingInt<MatchResult> { it.groupValues[1].toInt() }.thenComparing { it.groupValues[3] }
                .thenComparing { it.groupValues[2] }
                .thenComparing { it.groupValues[4] }
                .thenComparing { it.groupValues[5] }
                .compare(match1, match2)
        }
    }
    
    object RegularComparator : Comparator<Any> {
        override fun compare(o1: Any, o2: Any): Int {
            o1 as Comparable<Any>
//            o2 as Comparable<Any>
            return o1.compareTo(o2)
        }
    }
}

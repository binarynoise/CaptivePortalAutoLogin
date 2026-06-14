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
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.GroupWithKey
import org.jetbrains.kotlinx.dataframe.api.append
import org.jetbrains.kotlinx.dataframe.api.columnNames
import org.jetbrains.kotlinx.dataframe.api.count
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame
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

// Put the unsafe cast here so we don't have unsafe casts all over the place
@Suppress("UNCHECKED_CAST")
fun <T> ColumnDefinition(
    name: String,
    displayName: String,
    comparator: Comparator<T>,
): ColumnDefinition = ColumnDefinition(name, displayName, comparator as Comparator<Any>)

@DataSchema
data class ActionColumnDefinition(
    val name: String,
    val displayName: String,
    val dependencies: List<String>,
)

data class ActionButtonData(
    val displayName: String,
    val url: String,
    val method: String,
    val isGet: Boolean = method.equals("get", ignoreCase = true),
) : MappableData

typealias ActionColumnAction = ActionButtonData

@DataSchema
data class PreFilterDefinition(
    val name: String,
    val displayName: String,
    val dataFrameProvider: suspend () -> DataFrame<*>,
)

suspend fun generateTableData(
    call: RoutingCall,
    columnDefinitions: DataFrame<ColumnDefinition>,
    preFilterDefinitions: List<PreFilterDefinition>,
    defaultGroups: Set<String>,
    defaultPreFilter: String = "all",
    defaultSort: String = "timestamp-desc",
    actionColumnDefinitions: DataFrame<ActionColumnDefinition> = emptyDataFrame(),
): TableData {
    val preFilterDefinitionMap = preFilterDefinitions.associateBy { it.name }
    
    val preFilter = call.request.queryParameters["preFilter"] ?: defaultPreFilter
    var dataFrame: DataFrame<*> = preFilterDefinitionMap.getValue(preFilter).dataFrameProvider()
    
    dataFrame = dataFrame.select(columns = (columnDefinitions.names + actionColumnDefinitions.names).toTypedArray())
    
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
    val filterQueryMap: Map<String, List<String>>? = when {
        filterString == null -> null
        filterString.contains("=") -> {
            val filterQueryMap = parseQueryString(filterString).toMap()
            val filterParameters = filterQueryMap.mapValues { (_, value) -> value.single() }
            dataFrame = dataFrame.filter { row ->
                filterParameters.all { (key, value) -> row[key]?.toString() == value }
            }
            filterQueryMap
        }
        else -> {
            dataFrame = dataFrame.filter { row ->
                filterOptions.any { (name, _, active) ->
                    (active || filterOptionsEmpty) && row[name].toString().contains(filterString, ignoreCase = true)
                }
            }
            null
        }
    }
    
    var groupColumns = call.request.queryParameters.getSelectedCheckboxes("group-")
    val groupOptions = columnDefinitions.rows().map { it: DataRow<ColumnDefinition> ->
        TableOption(it.name, it.displayName, it.name in groupColumns)
    }
    if (groupColumns.isEmpty()) {
        // use defaultGroups as fallback but keep the order of columnDefinitions
        groupColumns = columnDefinitions.rows().map { it.name }.intersect(defaultGroups).toList()
    }
    
    val visibleActionColumnDefinitions: DataFrame<ActionColumnDefinition> = if (groupColumns.isNotEmpty()) {
        actionColumnDefinitions.filter { it.dependencies.all { dep -> dep in groupColumns || filterQueryMap != null && dep in filterQueryMap.keys } }
    } else {
        actionColumnDefinitions
    }
    val visibleActionColumnNames = visibleActionColumnDefinitions.names
    
    if (groupColumns.isNotEmpty()) {
        groupColumns += visibleActionColumnNames
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
    
    val sortParam = call.request.queryParameters["sort"] ?: defaultSort
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
                row.toMap().entries.filterNot { (key, _) -> key == "count" || key in visibleActionColumnNames }
                    .joinToString("&") { (key, value) ->
                        val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                        val encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8)
                        "${encodedKey}=${encodedValue}"
                    }
            parameters["preFilter"] = preFilter
            
            originalColumnDefinitions.rows().forEach { definition ->
                if (definition.name != "count" && definition.name !in row.columnNames()) {
                    parameters["group-${definition.name}"] = "on"
                }
            }
        }
        TableRow(
            columns = columnDefinitions.names.map { row[it].toString() },
            url = "?" + url.encodedQuery,
            actionColumns = visibleActionColumnNames.map { row[it] as ActionColumnAction },
        )
    }
    val tableHeader = TableHeader(
        columns = columnDefinitions.rows().map { colDef ->
            TableHeaderColumn(
                colDef.name,
                colDef.displayName,
                if (colDef.name == sortColumnKey) sortDirection else null,
            )
        },
        actionColumns = visibleActionColumnDefinitions.rows().map { it.displayName },
    )
    
    
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

@get:JvmName("columnDefinitionNames")
val DataFrame<ColumnDefinition>.names get() = this.rows().map { it.name }.toList()

@get:JvmName("actionColumnDefinitionNames")
val DataFrame<ActionColumnDefinition>.names get() = this.rows().map { it.name }.toList()

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
    val actionColumns: List<String> = emptyList(),
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
    val actionColumns: List<ActionColumnAction> = emptyList(),
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


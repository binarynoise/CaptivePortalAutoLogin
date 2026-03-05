package de.binarynoise.captiveportalautologin

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityUrlTestBinding
import de.binarynoise.captiveportalautologin.util.getColorFromAttr
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import okhttp3.OkHttpClient
import okhttp3.Response


class UrlTestActivity : ComponentActivity() {
    val binding by viewBinding { ActivityUrlTestBinding.inflate(layoutInflater) }
    
    val client = OkHttpClient.Builder().apply {
        callTimeout(2.seconds)
        followRedirects(false)
    }.build()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        with(binding) {
            refreshButton.setOnClickListener {
                table.removeAllViews()
                refresh()
            }
        }
    }
    
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun refresh() = lifecycleScope.launch(Dispatchers.Main) {
        with(binding.table) {
            with(binding.table.context) {
                removeAllViews()
                addView(
                    TableRow(
                        TextView("Portal"),
                        TextView("http"),
                        TextView("https"),
                        TextView("Location"),
                    )
                )
                
                fun TextView.setTextFromResult(result: Result<Response>) {
                    result.onSuccess { response ->
                        text = response.code.toString()
                        setTextColor(
                            when (response.code) {
                                in 100..<200 -> 0xff75bbfd
                                in 200..<300 -> 0xff15b01a
                                in 300..<400 -> 0xffffff14
                                in 400..<500 -> 0xffe50000
                                in 500..<600 -> 0xfff97306
                                else -> getColorFromAttr(android.R.attr.textColor).toLong()
                            }.toInt()
                        )
                    }.onFailure { throwable ->
                        text = throwable::class.simpleName
                        setTextColor(0xffe50000.toInt())
                    }
                }
                
                for ((name, testUrl) in PortalDetection.backends) {
                    val httpTextView = TextView("...")
                    val httpsTextView = TextView("...")
                    val httpLocationTextView = TextView("...")
                    addView(
                        TableRow(
                            TextView(name),
                            httpTextView,
                            httpsTextView,
                            httpLocationTextView,
                        )
                    )
                    
                    launch(Dispatchers.IO) {
                        val result = runCatching { client.get(testUrl.httpUrl, null) }
                        result.onSuccess {
                            val location = it.getLocation()
                            withContext(Dispatchers.Main) {
                                httpLocationTextView.text = location
                            }
                        }
                        withContext(Dispatchers.Main) {
                            httpTextView.setTextFromResult(result)
                        }
                    }
                    launch(Dispatchers.IO) {
                        val result = runCatching { client.get(testUrl.httpsUrl, null) }
                        withContext(Dispatchers.Main) {
                            httpsTextView.setTextFromResult(result)
                        }
                    }
                }
            }
        }
    }
}

context(context: Context)
private fun TableRow(vararg views: View): TableRow {
    val row = TableRow(context)
    views.forEach { row.addView(it) }
    return row
}

context(context: Context)
private suspend fun TextView(text: String? = null, setup: suspend TextView.() -> Unit = {}): TextView {
    val textView = TextView(context)
    textView.text = text
    setup(textView)
    return textView
}

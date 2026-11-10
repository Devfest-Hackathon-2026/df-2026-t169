package com.example.gdgoc_app.ui.screen.diagnosis

import java.text.SimpleDateFormat
import java.util.Locale

/** Display formatting only; keeps the server value if a historical date cannot be parsed. */
internal fun diagnosisDisplayDate(value: String): String = try {
    val normalized=Regex("\\.([0-9]+)").replace(value) { "."+it.groupValues[1].padEnd(3,'0').take(3) }
    val pattern=if(normalized.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
    val parsed=SimpleDateFormat(pattern,Locale.ROOT).apply {isLenient=false}.parse(normalized)
    if(parsed==null) value else SimpleDateFormat("yyyy年M月d日",Locale.JAPAN).format(parsed)
} catch(_:Exception) { value }

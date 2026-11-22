package com.tvsettings.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The indent helpers for a plain scrolling column (the names match the lazy ones in the lazy-list helpers) (the names match the lazy ones above)
@Composable
fun ColumnScope.item(key: Any? = null, content: @Composable () -> Unit) {
    content()
}

@Composable
fun ColumnScope.indentedItem(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(start = 12.dp)) { content() }
}

@Composable
fun <T> ColumnScope.indentedItems(list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
    list.forEach { element -> Box(modifier = Modifier.padding(start = 12.dp)) { content(element) } }
}

@Composable
fun ColumnScope.indentedToggleItems(settings: List<ToggleSpec>) {
    settings.forEach { setting -> Box(modifier = Modifier.padding(start = 12.dp)) { ToggleRow(setting) } }
}

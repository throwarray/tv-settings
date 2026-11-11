package com.tvsettings.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** Global CPU and RAM, for a custom launcher to host. The foreground service keeps it fresh while one is placed. */
class UsageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        UsageWidget.push(context, ids)
        KeepAlive.sync(context)
    }

    override fun onEnabled(context: Context) = KeepAlive.sync(context)

    override fun onDisabled(context: Context) = KeepAlive.sync(context)
}

object UsageWidget {
    @Volatile private var cpuText = "CPU --"
    @Volatile private var ramText = "RAM --"

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))

    fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

    fun push(context: Context, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        views.setTextViewText(R.id.widget_cpu, cpuText)
        views.setTextViewText(R.id.widget_ram, ramText)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    /** Blocking. Samples global usage and pushes it to every placed widget. */
    fun refresh(context: Context) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val cpu = ResourceUsage.globalCpu()
        val (used, total) = ResourceUsage.ram(context)
        cpuText = if (cpu == null) "CPU --" else "CPU ${formatCpu(cpu)}"
        ramText = "RAM ${formatKb(used)} / ${formatKb(total)}"
        push(context, ids)
    }
}

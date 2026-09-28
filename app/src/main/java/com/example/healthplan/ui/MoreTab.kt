package com.example.healthplan.ui

import android.content.Intent
import android.view.View
import com.example.healthplan.MainActivity
import com.example.healthplan.Tab

class MoreTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val col = Ui.column(a)
        col.h1("Ещё")
        fun go(label: String, cls: Class<*>) = col.button(label, outlined = true) { a.startActivity(Intent(a, cls)) }
        go("👤  Профиль и цель", ProfileActivity::class.java)
        go("📊  Мои параметры и нормы", ParamsActivity::class.java)
        go("🥗  Мои блюда и продукты", MyFoodsActivity::class.java)
        go("📷  Распознать этикетку (фото)", OcrActivity::class.java)
        go("💾  Данные: экспорт и импорт", DataActivity::class.java)
        go("🔐  Разрешения для звонков", PermissionsActivity::class.java)
        go("📚  Источники и оговорки", SourcesActivity::class.java)
        col.card {
            text("Важно", 18f, true)
            muted(SourcesActivity.DISCLAIMER)
        }
        col.muted("Версия ${a.packageManager.getPackageInfo(a.packageName, 0).versionName} · все данные хранятся только на телефоне, без интернета.", 13f)
        return Ui.scroll(a, col)
    }
}

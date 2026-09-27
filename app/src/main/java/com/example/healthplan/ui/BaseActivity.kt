package com.example.healthplan.ui

import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.healthplan.data.Store

open class BaseActivity : AppCompatActivity() {
    val store: Store by lazy { Store.get(this) }

    fun screen(build: LinearLayout.() -> Unit) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val y = (window.decorView.findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.getChildAt(0)?.let { (it as? android.widget.ScrollView)?.scrollY } ?: 0
        val col = Ui.column(this)
        col.build()
        val sv = Ui.scroll(this, col)
        setContentView(sv)
        sv.post { sv.scrollTo(0, y) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}

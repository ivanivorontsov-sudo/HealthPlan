package com.example.healthplan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.healthplan.alarm.AlarmScheduler
import com.example.healthplan.data.Store
import com.example.healthplan.ui.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    lateinit var store: Store
    private lateinit var content: FrameLayout
    private lateinit var nav: BottomNavigationView
    var tab = 0
    private var current: Tab? = null

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        Defaults.ensureEvents(store)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = FrameLayout(this)
        nav = BottomNavigationView(this).apply {
            menu.add(0, 0, 0, "Главная").setIcon(R.drawable.ic_home)
            menu.add(0, 1, 1, "Питание").setIcon(R.drawable.ic_food)
            menu.add(0, 2, 2, "Дневник").setIcon(R.drawable.ic_book)
            menu.add(0, 3, 3, "События").setIcon(R.drawable.ic_alarm)
            menu.add(0, 4, 4, "Ещё").setIcon(R.drawable.ic_more)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { tab = it.itemId; show(); true }
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        tab = savedInstanceState?.getInt("tab") ?: 0
        nav.selectedItemId = tab

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!store.profile.filled) startActivity(android.content.Intent(this, ProfileActivity::class.java))
        else if (!store.permissionsShown) { store.permissionsShown = true; startActivity(android.content.Intent(this, PermissionsActivity::class.java)) }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putInt("tab", tab) }

    override fun onResume() {
        super.onResume()
        Defaults.syncMeals(store)
        AlarmScheduler.scheduleAll(this)   // перепланирование при каждом открытии
        show()
    }

    override fun onPause() { super.onPause(); current?.stop() }

    fun refresh() = show()

    private fun show() {
        current?.stop()
        val t: Tab = when (tab) { 0 -> HomeTab(this); 1 -> DietTab(this); 2 -> DiaryTab(this); 3 -> EventsTab(this); else -> MoreTab(this) }
        val scrollY = (content.getChildAt(0) as? android.widget.ScrollView)?.scrollY ?: 0
        val sameTab = current?.javaClass == t.javaClass
        content.removeAllViews()
        val v = t.build()
        content.addView(v)
        if (sameTab && v is android.widget.ScrollView) v.post { v.scrollTo(0, scrollY) }
        current = t
    }
}

abstract class Tab(val a: MainActivity) {
    abstract fun build(): View
    open fun stop() {}
}

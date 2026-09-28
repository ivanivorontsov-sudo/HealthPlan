package com.example.healthplan.data

import android.content.Context
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Страховочные копии во внутреннем хранилище приложения (filesDir/backups, в APK-обновлениях не трогается).
 * - перед первым запуском новой версии (если есть данные) — «auto-before-update-…json»;
 * - перед «Заменить всё» при импорте — «before-import-…json».
 * Хранится не больше [KEEP] последних файлов каждого вида.
 */
object DataSafety {
    private const val KEEP = 10
    const val DIR = "backups"

    fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun versionCode(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (_: Exception) { 0 }

    fun versionName(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

    fun exportJson(ctx: Context, store: Store): String =
        Backup.toJson(Backup.snapshot(store), versionName(ctx), versionCode(ctx), OffsetDateTime.now().toString()).toString(1)

    /** Флаг-защищённый снимок: один раз на каждую новую версию, только если есть пользовательские данные. Никогда не бросает. */
    fun snapshotBeforeUpgrade(ctx: Context, store: Store) {
        try {
            val cur = versionCode(ctx)
            val last = store.lastVersionCode
            if (cur <= 0 || last >= cur) return
            // 1.0.0 не хранила lastVersionCode, поэтому при last = 0 старая версия в имени не указывается
            if (store.hasUserData) save(ctx, store, if (last > 0) "auto-before-update-v$last-to-v$cur" else "auto-before-update-to-v$cur", "auto-before-update")
            store.lastVersionCode = cur
        } catch (_: Throwable) { /* снимок — страховка, его сбой не должен мешать запуску */ }
    }

    fun save(ctx: Context, store: Store, prefix: String, kind: String = prefix): File {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val f = File(dir(ctx), "$prefix-$ts.json")
        val tmp = File(f.path + ".tmp")
        tmp.writeText(exportJson(ctx, store))
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
        prune(ctx, kind)
        return f
    }

    fun list(ctx: Context): List<File> = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun prune(ctx: Context, kind: String) {
        list(ctx).filter { it.name.startsWith(kind) }.drop(KEEP).forEach { it.delete() }
    }
}

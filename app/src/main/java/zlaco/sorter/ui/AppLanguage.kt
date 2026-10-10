package zlaco.sorter.ui

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.StringRes
import zlaco.sorter.R
import java.util.Locale

/**
 * App language, following the system by default.
 *
 * The phone this was written for runs its system in English while its apps run
 * in Russian, so "follow the system" alone leaves the app in a language the
 * user does not read. Android 13 and later keep a per-app language, declared
 * through `localeConfig`; setting it here is what makes the picker below work,
 * and the same setting shows up in system settings afterwards.
 */
object AppLanguage {

    data class Option(val tag: String, @StringRes val labelRes: Int)

    val options: List<Option> = listOf(
        Option("system", R.string.language_system),
        Option("en", R.string.language_english),
        Option("ru", R.string.language_russian),
    )

    /** Empty list means "no override", which is how the platform spells "follow the system". */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "system"
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            ?: return "system"
        if (locales.isEmpty) return "system"
        return locales[0]?.language ?: "system"
    }

    fun apply(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // No per-app language before Android 13. Point at the system screen
            // rather than pretending the choice took.
            openSystemLanguagePage(context)
            return
        }
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (tag == "system") {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList(Locale.forLanguageTag(tag))
        }
    }

    private fun openSystemLanguagePage(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

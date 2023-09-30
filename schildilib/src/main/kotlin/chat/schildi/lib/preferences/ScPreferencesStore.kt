package chat.schildi.lib.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import chat.schildi.lib.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "schildinext-preferences")

class ScPreferencesStore(context: Context) {
    private val store = context.dataStore

    val SC_THEME = ScPref.ScBoolPref("SC_THEMES", true, R.string.sc_pref_sc_themes_title)
    val SC_TEST = ScPref.ScStringListPref("TEST", "A", arrayOf("A", "B", "C"), arrayOf("a", "b", "c"), R.string.sc_pref_sc_themes_title)

    val scTweaks = listOf(
        SC_THEME,
        SC_TEST,
    )

    suspend fun <T>setSetting(scPref: ScPref<T>, value: T) {
        store.edit { prefs ->
            prefs[scPref.key] = value
        }
    }

    suspend fun <T>setSettingTypesafe(scPref: ScPref<T>, value: Any?) {
        val v = scPref.ensureType(value)
        if (v == null) {
            Timber.e("Cannot set typesafe setting for ${scPref.key}, $value")
            return
        }
        setSetting(scPref, v)
    }

    fun <T> settingFlow(scPref: ScPref<T>): Flow<T> {
        return store.data.map { prefs ->
            prefs[scPref.key] ?: scPref.defaultValue
        }
    }

    @Composable
    fun <T>settingState(scPref: ScPref<T>, context: CoroutineContext = EmptyCoroutineContext): State<T> = settingFlow(scPref).collectAsState(scPref.defaultValue, context)

    suspend fun reset() {
        store.edit { it.clear() }
    }
}

@Composable
fun <R>List<ScPref<out Any>>.prefValMap(v: @Composable (ScPref<out Any>) -> R) = associate { it.sKey to v(it) }
@Composable
fun List<ScPref<out Any>>.prefMap() = prefValMap { p -> p }

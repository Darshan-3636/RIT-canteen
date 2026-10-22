package edu.msrit.canteen.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import edu.msrit.canteen.customer.AppearanceChoice

/** Local UI preference; future authenticated account sync must be separate from this device setting. */
class AppearancePreference(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("rit_appearance", Context.MODE_PRIVATE)
    var choice by mutableStateOf(runCatching {
        AppearanceChoice.valueOf(preferences.getString("choice", AppearanceChoice.SYSTEM.name)!!)
    }.getOrDefault(AppearanceChoice.SYSTEM))
        private set

    fun choose(value: AppearanceChoice) {
        preferences.edit { putString("choice", value.name) }
        choice = value
    }
}

package pt.isec.amov.safetysec.utils

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object LanguageUtils {

    // Mantém o código da língua atual ("en" ou "pt")
    var currentCode by mutableStateOf("en")

    // Apenas atualiza o estado. A MainActivity vai reagir a isto.
    fun setLocale(code: String) {
        currentCode = code
    }

    // Cria um novo contexto com a língua definida (usado na MainActivity)
    fun getLocalizedContext(context: Context): Context {
        val locale = Locale(currentCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
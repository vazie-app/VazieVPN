package app.vazie.vpn.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import java.util.Locale

/** Uppercase a label the way the composition's locale asks for, not the way the process happens to be
 * configured. */
@Composable
fun String.vazieUppercase(): String =
    uppercase(Locale.forLanguageTag(ComposeLocale.current.toLanguageTag()))

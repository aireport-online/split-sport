package online.aireport.split.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import online.aireport.split.AppContainer
import online.aireport.split.SplitApplication

/**
 * A view model for the current navigation destination, built from the app's dependencies. The
 * receiver gives access to `createSavedStateHandle()`.
 */
@Composable
inline fun <reified VM : ViewModel> splitViewModel(crossinline create: CreationExtras.(AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as SplitApplication).container
    return viewModel { create(container) }
}

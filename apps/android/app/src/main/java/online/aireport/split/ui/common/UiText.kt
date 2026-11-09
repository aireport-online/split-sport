package online.aireport.split.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import online.aireport.split.R
import online.aireport.split.data.api.ApiException

/** Text for the screen that is either a string resource or came from the server. */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int) : UiText

    data class Plain(val value: String) : UiText

    @Composable
    @ReadOnlyComposable
    fun resolve(): String = when (this) {
        is Resource -> stringResource(id)
        is Plain -> value
    }
}

/** The server's own message when it sent one, otherwise a Dutch description of what went wrong. */
fun ApiException.toUiText(): UiText = when (this) {
    is ApiException.Server -> message?.let(UiText::Plain) ?: UiText.Resource(R.string.error_server)
    is ApiException.Network -> UiText.Resource(R.string.error_network)
    is ApiException.InvalidServerUrl -> UiText.Resource(R.string.error_server_url)
    is ApiException.UnexpectedResponse -> UiText.Resource(R.string.error_server)
}

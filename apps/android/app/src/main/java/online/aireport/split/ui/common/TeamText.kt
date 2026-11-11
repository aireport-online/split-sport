package online.aireport.split.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import online.aireport.split.R
import online.aireport.split.data.model.Sport

/** The name of a sport as it is shown: "Hockey". */
@get:StringRes
val Sport.labelRes: Int
    get() = when (this) {
        Sport.VOETBAL -> R.string.sport_voetbal
        Sport.HOCKEY -> R.string.sport_hockey
        Sport.KORFBAL -> R.string.sport_korfbal
        Sport.VOLLEYBAL -> R.string.sport_volleybal
        Sport.HANDBAL -> R.string.sport_handbal
        Sport.BASKETBAL -> R.string.sport_basketbal
        Sport.TENNIS -> R.string.sport_tennis
        Sport.PADEL -> R.string.sport_padel
        Sport.RUGBY -> R.string.sport_rugby
        Sport.WATERPOLO -> R.string.sport_waterpolo
        Sport.OVERIG -> R.string.sport_overig
    }

/** "Hockey · MHC Oostpoort · 2026/27": sport, club and season; club and season only when filled in. */
@Composable
@ReadOnlyComposable
fun teamLine(sport: Sport, club: String?, season: String? = null): String =
    listOfNotNull(stringResource(sport.labelRes), club?.takeIf { it.isNotBlank() }, season?.takeIf { it.isNotBlank() })
        .joinToString(" · ")

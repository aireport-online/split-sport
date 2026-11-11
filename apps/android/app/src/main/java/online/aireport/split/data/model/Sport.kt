package online.aireport.split.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The sports a team can play, in the order the picker offers them. A value this version does
 * not know (a newer server) is read as [OVERIG]: see `coerceInputValues` in `SplitJson`.
 */
@Serializable
enum class Sport {
    @SerialName("voetbal") VOETBAL,
    @SerialName("hockey") HOCKEY,
    @SerialName("korfbal") KORFBAL,
    @SerialName("volleybal") VOLLEYBAL,
    @SerialName("handbal") HANDBAL,
    @SerialName("basketbal") BASKETBAL,
    @SerialName("tennis") TENNIS,
    @SerialName("padel") PADEL,
    @SerialName("rugby") RUGBY,
    @SerialName("waterpolo") WATERPOLO,
    @SerialName("overig") OVERIG,
}

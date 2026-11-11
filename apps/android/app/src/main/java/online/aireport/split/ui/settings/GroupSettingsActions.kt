package online.aireport.split.ui.settings

import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.Sport

/** What the team settings screen can ask for; the view model does it. */
interface GroupSettingsActions {
    fun retry()

    /** Saves the name, club and season; an empty club or season clears it. */
    fun updateDetails(name: String, club: String, season: String)

    fun recolor(color: GroupColor)

    fun changeSport(sport: Sport)

    /** Hands the pot's money to another team member. */
    fun changeTreasurer(memberId: String)

    fun addFine(name: String, amountCents: Long)

    fun updateFine(fineId: String, name: String, amountCents: Long)

    fun removeFine(fineId: String)

    fun addMember(name: String)

    fun renameMember(memberId: String, name: String)

    fun removeMember(memberId: String)

    fun leave()

    fun onMessageShown()
}

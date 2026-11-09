package online.aireport.split.data.session

/** Which server the app talks to and, when signed in, as whom. */
data class Session(val serverUrl: String, val token: String?) {
    val isSignedIn: Boolean get() = token != null
}

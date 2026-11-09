package online.aireport.split.data.api

/** Why a call to the Split API failed. The only exception the data layer lets through. */
sealed class ApiException(message: String?, cause: Throwable?) : Exception(message, cause) {
    /**
     * The server answered with an error. [message] is the server's Dutch `error.message`, meant
     * to be shown as-is; it is null when the answer was not a Split error body.
     */
    class Server(val status: Int, val code: String?, message: String?) : ApiException(message, null)

    /** The server could not be reached. */
    class Network(cause: Throwable) : ApiException(cause.message, cause)

    /** The server URL is not a usable http(s) address. */
    class InvalidServerUrl : ApiException(null, null)

    /** The server answered with something this app does not understand. */
    class UnexpectedResponse(cause: Throwable) : ApiException(cause.message, cause)
}

package app.vazie.vpn.feature.home

/** Whether the phone should say something with a buzz, and which thing. */
internal enum class ConnectionFeedback { Success, Failure }

/** The one rule for when a connection result is worth feeling. */
internal fun connectionFeedback(
    previous: ConnectionUiState?,
    current: ConnectionUiState,
): ConnectionFeedback? {
    if (previous == null || previous == current) return null
    return when (current) {
        is ConnectionUiState.Connected ->
            ConnectionFeedback.Success.takeIf { previous.isAttempt() }

        is ConnectionUiState.Failed -> ConnectionFeedback.Failure

        ConnectionUiState.NoInternet ->
            ConnectionFeedback.Failure.takeIf { previous !is ConnectionUiState.Connected }

        ConnectionUiState.Idle,
        ConnectionUiState.Preparing,
        ConnectionUiState.Connecting,
        ConnectionUiState.Disconnecting,
        -> null
    }
}

/** The two states that mean "Vazie is currently trying, because somebody asked it to". */
private fun ConnectionUiState.isAttempt(): Boolean =
    this == ConnectionUiState.Preparing || this == ConnectionUiState.Connecting

package com.universestream.data.remote.xtream

import com.universestream.data.security.CredentialDecryptionException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

internal object XtreamErrorFormatter {
    fun message(prefix: String, throwable: Throwable): String {
        val formatted = when {
            throwable is CredentialDecryptionException -> "$prefix: ${throwable.message}"
            throwable.isCertificateTrustFailure() -> "$prefix: Server TLS certificate is not trusted by this device. Verify the HTTPS URL or ask the provider for a valid certificate."
            throwable is XtreamAuthenticationException -> "$prefix: Authentication failed. Please check your username, password, and server URL."
            throwable is XtreamParsingException -> "$prefix: ${throwable.message ?: "Server returned malformed or unsupported data."}"
            throwable is XtreamRequestException -> "$prefix: Request failed with HTTP ${throwable.statusCode}."
            throwable is XtreamNetworkException -> when (throwable.kind) {
                XtreamNetworkFailureKind.TIMEOUT -> "$prefix: Provider server did not respond in time."
                XtreamNetworkFailureKind.DNS -> "$prefix: Provider hostname could not be resolved."
                XtreamNetworkFailureKind.CONNECTION -> "$prefix: Could not connect to the provider server."
                XtreamNetworkFailureKind.CLEAR_TEXT_BLOCKED -> "$prefix: HTTP provider was blocked by Android network policy."
                XtreamNetworkFailureKind.TLS -> "$prefix: Secure connection failed."
                XtreamNetworkFailureKind.SERVER_RESPONSE -> "$prefix: Provider returned HTTP ${throwable.statusCode ?: "5xx"}."
                XtreamNetworkFailureKind.UNKNOWN -> "$prefix: Could not connect to the provider server."
            }
            else -> "$prefix: ${throwable.message ?: "Unexpected network error"}"
        }
        return XtreamUrlFactory.sanitizeLogMessage(formatted)
    }

    private fun Throwable.isCertificateTrustFailure(): Boolean {
        return generateSequence(this) { it.cause }.any { current ->
            current is SSLPeerUnverifiedException ||
                current is CertificateException ||
                current.message?.contains("trust anchor", ignoreCase = true) == true ||
                current.message?.contains("certificate", ignoreCase = true) == true ||
                current.message?.contains("hostname", ignoreCase = true) == true
        }
    }
}

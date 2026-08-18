package com.universestream.data.remote.xtream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XtreamUrlFactoryTest {

    @Test
    fun `player api preserves http port and normalizes trailing slash`() {
        val endpoint = XtreamUrlFactory.buildPlayerApiUrl(
            serverUrl = "http://provider.example:8080/",
            username = "user name",
            password = "p@ss"
        )

        assertThat(endpoint).isEqualTo(
            "http://provider.example:8080/player_api.php?username=user%20name&password=p%40ss"
        )
    }

    @Test
    fun `log sanitization removes credential query values`() {
        val message = XtreamUrlFactory.sanitizeLogMessage(
            "http://provider.example:8080/player_api.php?username=user&password=secret"
        )

        assertThat(message).contains("username=<redacted>")
        assertThat(message).contains("password=<redacted>")
        assertThat(message).doesNotContain("secret")
    }
}


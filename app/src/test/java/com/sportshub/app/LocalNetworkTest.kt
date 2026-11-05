package com.sportshub.app

import com.sportshub.app.data.LocalNetwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    @Test
    fun privateAddressesAreLocal() {
        listOf(
            "https://192.168.1.20/streams",
            "https://10.0.0.5:8443/",
            "https://172.20.3.4/",
            "https://169.254.10.1/",
            "https://mediaserver.local/live",
            "https://nas.lan/",
            "https://tv.home.arpa/",
            "https://[fe80::1]/",
            "https://[fd12:3456::1]/",
        ).forEach { assertTrue(it, LocalNetwork.isLocalAddress(it)) }
    }

    @Test
    fun publicAddressesAreNot() {
        listOf(
            "https://www.espn.com/nfl",
            "https://8.8.8.8/",
            "https://172.32.0.1/",
            "https://192.169.0.1/",
            "https://example.com/local",
            "not a url",
        ).forEach { assertFalse(it, LocalNetwork.isLocalAddress(it)) }
    }
}

package com.kite.app

import com.kite.app.run.CardRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityCommandHintTest {
    @Test
    fun completedRunDoesNotShowFailureHintFromCommandArguments() {
        val text = "curl -fL --connect-timeout 30 https://example.com/package"

        assertNull(cardRunCommandHint(CardRunStatus.Completed, text))
    }

    @Test
    fun failedRunDoesNotTreatConnectTimeoutOptionAsTimeoutResult() {
        val text = "curl -fL --connect-timeout 30 https://example.com/package\ncurl: (22) 403"

        assertNull(cardRunCommandHint(CardRunStatus.Failed, text))
    }

    @Test
    fun failedRunShowsTimeoutHintForRealTimeoutSignal() {
        val expected = "命令超时，可能还在等待输入、网络、服务启动，或者命令本身卡住了。"

        assertEquals(
            expected,
            cardRunCommandHint(CardRunStatus.Failed, "curl: (28) Operation timed out after 30000 milliseconds")
        )
        assertEquals(
            expected,
            cardRunCommandHint(CardRunStatus.Failed, "KITE_RESOURCE_FAILURE stage=install reason=timeout")
        )
    }
}

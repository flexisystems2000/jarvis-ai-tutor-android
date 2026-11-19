package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.AccountType
import org.junit.Assert.*
import org.junit.Test

class PlanPolicyTest {
    @Test fun freePlanHasFiftyDailyTokens() {
        assertEquals(50, PlanPolicy.dailyLimit(AccountType.FREE))
    }
    @Test fun plusPlanHasFourThousandTokens() {
        assertEquals(4000, PlanPolicy.dailyLimit(AccountType.JARVIS_PLUS))
    }
    @Test fun proPlanHasTenThousandTokensAndVideo() {
        assertEquals(10000, PlanPolicy.dailyLimit(AccountType.JARVIS_PRO))
        assertTrue(PlanPolicy.videoEnabled(AccountType.JARVIS_PRO))
    }
    @Test fun masterclassHasNoTokenLimitAndVideo() {
        assertNull(PlanPolicy.dailyLimit(AccountType.FLEXI_MASTERCLASS))
        assertTrue(PlanPolicy.canUseTokens(AccountType.FLEXI_MASTERCLASS, 999999, 500))
        assertTrue(PlanPolicy.videoEnabled(AccountType.FLEXI_MASTERCLASS))
    }
    @Test fun freeLimitBlocksOverage() {
        assertFalse(PlanPolicy.canUseTokens(AccountType.FREE, 50))
        assertTrue(PlanPolicy.canUseTokens(AccountType.FREE, 49))
    }
}

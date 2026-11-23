package com.massspx.droidbridge

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisEngineTest {
    @Test fun identifiesSignatureMismatch() {
        assertTrue(DiagnosisEngine.explain("INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match").contains("လက်မှတ်"))
    }

    @Test fun identifiesAbiMismatch() {
        assertTrue(DiagnosisEngine.explain("INSTALL_FAILED_NO_MATCHING_ABIS").contains("Architecture"))
    }

    @Test fun identifiesStorageFailure() {
        assertTrue(DiagnosisEngine.explain("INSTALL_FAILED_INSUFFICIENT_STORAGE").contains("နေရာ"))
    }

    @Test fun alwaysReturnsSafeFallback() {
        assertTrue(DiagnosisEngine.explain("unknown platform failure").isNotBlank())
    }
}

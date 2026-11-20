
package com.flexi.jarvis.core.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisErrorHandlerTest {

    @Test
    fun invalidCredentialsShowsFriendlyMessage() {
        val result = JarvisErrorHandler.fromCode("invalid_credentials")

        assertEquals("INVALID_CREDENTIALS", result.code)
        assertEquals(
            "Your login details are incorrect. Check your password or use Google sign-in.",
            result.message
        )
        assertEquals(
            JarvisErrorCategory.AUTHENTICATION,
            result.category
        )
    }

    @Test
    fun duplicateEmailShowsFriendlyMessage() {
        val result = JarvisErrorHandler.fromCode("email_exists")

        assertEquals("EMAIL_EXISTS", result.code)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun rowLevelSecurityErrorIsRecognised() {
        val result = JarvisErrorHandler.fromCode("42501")

        assertEquals(JarvisErrorCategory.DATABASE, result.category)
        assertTrue(result.message.contains("permission"))
    }

    @Test
    fun duplicateDatabaseRecordIsRecognised() {
        val result = JarvisErrorHandler.fromCode("23505")

        assertEquals(JarvisErrorCategory.DATABASE, result.category)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun postgrestMissingTableIsRecognised() {
        val result = JarvisErrorHandler.fromCode("PGRST205")

        assertEquals(JarvisErrorCategory.REST_API, result.category)
        assertTrue(result.message.contains("database resource"))
    }

    @Test
    fun rateLimitIsRetryable() {
        val result = JarvisErrorHandler.fromCode("over_request_rate_limit")

        assertTrue(result.retryable)
        assertTrue(result.message.contains("Too many requests"))
    }

    @Test
    fun serverErrorIsRetryable() {
        val result = JarvisErrorHandler.fromHttpStatus(503)

        assertEquals("HTTP_503", result.code)
        assertEquals(503, result.httpStatus)
        assertTrue(result.retryable)
    }

    @Test
    fun forbiddenErrorIsNotRetryable() {
        val result = JarvisErrorHandler.fromHttpStatus(403)

        assertEquals("HTTP_403", result.code)
        assertFalse(result.retryable)
    }

    @Test
    fun unknownErrorGetsSafeFallback() {
        val result = JarvisErrorHandler.fromCode("some_future_error")

        assertEquals("SOME_FUTURE_ERROR", result.code)
        assertEquals(JarvisErrorCategory.AUTHENTICATION, result.category)
        assertTrue(result.message.contains("Something went wrong"))
    }
}

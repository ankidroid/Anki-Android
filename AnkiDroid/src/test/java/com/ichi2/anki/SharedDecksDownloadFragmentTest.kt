package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadFragmentTest {
    @Test
    fun `test getDeckIdFromDownloadURL with valid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        val deckId = SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url)
        assertEquals("1104981491", deckId)
    }

    @Test
    fun `test getDeckIdFromDownloadURL without Query Parameter`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491"
        val deckId = SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url)
        assertEquals("1104981491", deckId)
    }

    @Test
    fun `test getDeckIdFromDownloadURL with invalid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        val deckId = SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url)
        assertNull(deckId)
    }

    @Test
    fun `test getDeckPageUri with valid deck URL`() {
        val fragment = SharedDecksDownloadFragment()
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("https://ankiweb.net/shared/info/1104981491", fragment.getDeckPageUri(url))
    }

    @Test
    fun `test getDeckPageUri with invalid deck URL`() {
        val fragment = SharedDecksDownloadFragment()
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertEquals("https://ankiweb.net/shared/decks/", fragment.getDeckPageUri(url))
    }
}

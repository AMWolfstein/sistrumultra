package me.misa198.airmedy.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import me.misa198.airmedy.player.ListeningSession
import me.misa198.airmedy.player.ListeningWrite
import me.misa198.airmedy.player.PlaybackAttempt
import me.misa198.airmedy.player.PlaybackEndReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PlaybackService prunes raw listening records with cleanupListening(now - ListeningRetentionMs),
 * a 180-day window (README: "Raw records are retained for 180 days and daily ... aggregates
 * are retained for all-time totals"). This covers the store side of that contract with an
 * explicit cutoff; the retention length itself stays defined only in PlaybackService.
 */
class ListeningRetentionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SyncDatabase
    private lateinit var filesDir: File
    private lateinit var store: AndroidLibrarySyncStore

    private val day = 24L * 60 * 60 * 1_000
    private val cutoff = 1_780_000_000_000L
    private val before = cutoff - day
    private val after = cutoff + day

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java).build()
        filesDir = File(context.cacheDir, "listening-retention-${System.nanoTime()}").apply { mkdirs() }
        store = AndroidLibrarySyncStore(database, filesDir)
    }

    @After fun tearDown() {
        database.close()
        filesDir.deleteRecursively()
    }

    private suspend fun session(id: String, endedAt: Long) = store.recordListening(
        ListeningWrite.Session(ListeningSession(id, "device", "t-$id", endedAt - 60_000, endedAt, 60, qualifiedPlay = true)),
    )

    private suspend fun attempt(id: String, startedAt: Long, endedAt: Long?) {
        val started = PlaybackAttempt(id, "device", "t-$id", startedAt, startPositionMs = 0)
        store.recordListening(ListeningWrite.AttemptStarted(started))
        if (endedAt != null) {
            store.recordListening(ListeningWrite.AttemptFinished(started.copy(endedAt = endedAt, listenedSeconds = 30, endReason = PlaybackEndReason.COMPLETED)))
        }
    }

    @Test fun cleanupDeletesRawRecordsOlderThanTheCutoffAndKeepsTheRest() = runBlocking {
        session("old", endedAt = before)
        session("edge", endedAt = cutoff)
        session("new", endedAt = after)
        attempt("old-finished", startedAt = before - 60_000, endedAt = before)
        attempt("new-finished", startedAt = after - 60_000, endedAt = after)
        attempt("open", startedAt = before - 60_000, endedAt = null)
        val dailyBefore = store.listeningSnapshot("r", 0).let { it.dailyTracks to it.dailyAttempts }
        assertTrue("the writes produced daily aggregates", dailyBefore.first.isNotEmpty() && dailyBefore.second.isNotEmpty())

        store.cleanupListening(cutoff)

        val snapshot = store.listeningSnapshot("r", 0)
        assertEquals("sessions ending at or after the cutoff are kept", setOf("edge", "new"), snapshot.sessions.map { it.id }.toSet())
        assertEquals("finished attempts before the cutoff are deleted", setOf("new-finished"), snapshot.attempts.map { it.id }.toSet())
        // The snapshot lists finished attempts only; an unfinished one must survive for
        // recoverOpenPlaybackAttempts to close it.
        assertEquals(listOf("open"), database.syncDao().openPlaybackAttempts().map { it.id })
        assertEquals("daily aggregates are kept for all-time totals", dailyBefore, snapshot.dailyTracks to snapshot.dailyAttempts)
    }
}

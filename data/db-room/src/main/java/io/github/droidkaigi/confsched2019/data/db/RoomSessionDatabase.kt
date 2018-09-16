package io.github.droidkaigi.confsched2019.data.db

import androidx.lifecycle.*
import io.github.droidkaigi.confsched2019.data.api.response.Response
import io.github.droidkaigi.confsched2019.data.db.entity.SessionDao
import io.github.droidkaigi.confsched2019.data.db.entity.SessionEntity
import io.github.droidkaigi.confsched2019.data.db.entity.SessionEntityImpl
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject

class RoomSessionDatabase @Inject constructor(
        private val databaseSession: SessionCacheDatabase,
        private val sessionDao: SessionDao
//        private val speakerDao: SpeakerDao,
//        private val sessionSpeakerJoinDao: SessionSpeakerJoinDao,
//        private val sessionFeedbackDao: SessionFeedbackDao
) : SessionDatabase {
    override fun sessionsChannel(): ReceiveChannel<List<SessionEntity>> = sessionDao.sessionsLiveData().observeChannel().map {
        it.orEmpty()
    }

    override suspend fun sessions(): List<SessionEntity> {
        return withContext(CommonPool) {
            sessionDao.sessions()
        }
    }

    private val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private fun LocalDateTime.atJST(): ZonedDateTime {
        return atZone(ZoneId.of("JST", ZoneId.SHORT_IDS))
    }

    override fun save(apiResponse: Response) {
        sessionDao.clearAndInsert(apiResponse.sessions.map { session ->
            SessionEntityImpl(
                    id = session.id,
                    title = session.title,
                    desc = session.description,
                    stime = LocalDateTime.parse(session.startsAt, FORMATTER).atJST().toInstant().toEpochMilli(),
                    etime = LocalDateTime.parse(session.endsAt, FORMATTER).atJST().toInstant().toEpochMilli()
            )
        })
    }

}

// from: https://github.com/dmytrodanylyk/coroutines-arch/blob/master/library/src/main/java/com/kotlin/arch/LiveDataChannel.kt
class LiveDataChannel<T>(private val liveData: LiveData<T>)
    : LinkedListChannel<T?>(), ReceiveChannel<T?>, Observer<T?>, LifecycleObserver {

    override fun onChanged(t: T?) {
        offer(t)
    }

    override fun afterClose(cause: Throwable?) = liveData.removeObserver(this)

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() = close()

}

fun <T> LiveData<T>.observeChannel(lifecycleOwner: LifecycleOwner): LiveDataChannel<T> {
    val channel = LiveDataChannel(this)
    observe(lifecycleOwner, channel)
    lifecycleOwner.lifecycle.addObserver(channel)
    return channel
}

fun <T> LiveData<T>.observeChannel(): LiveDataChannel<T> {
    val channel = LiveDataChannel(this)
    observeForever(channel)
    return channel
}

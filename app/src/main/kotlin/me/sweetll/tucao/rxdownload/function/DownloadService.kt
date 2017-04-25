package me.sweetll.tucao.rxdownload.function

import android.app.IntentService
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.raizlabs.android.dbflow.kotlinextensions.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.rxdownload.entity.DownloadBean
import me.sweetll.tucao.rxdownload.entity.DownloadEvent
import me.sweetll.tucao.rxdownload.entity.DownloadMission
import me.sweetll.tucao.rxdownload.entity.DownloadStatus
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.util.concurrent.Semaphore

class DownloadService: IntentService("DownloadWorker") {
    lateinit var binder: DownloadBinder

    val downloadApi: DownloadApi by lazy {
        Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_RAW_API_URL)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(
                        OkHttpClient.Builder()
                                .addInterceptor(HttpLoggingInterceptor())
                                .build()
                )
                .build()
                .create(DownloadApi::class.java)
    }

    var semaphore: Semaphore = Semaphore(1) // 同时只允许1个任务下载

    val missionMap: MutableMap<String, DownloadMission> = mutableMapOf()
    val processorMap: MutableMap<String, BehaviorProcessor<DownloadEvent>> = mutableMapOf()

    init {
        setIntentRedelivery(true) // Make sure service restart when die
    }

    override fun onCreate() {
        super.onCreate()
        binder = DownloadBinder()

        syncFromDb()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllMission()
        syncToDb()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onHandleIntent(intent: Intent?) {

    }

    private fun stopAllMission() {
        missionMap.forEach {
            _, mission ->
            mission.pause = true
        }
    }

    private fun syncFromDb() {
        val beans = (select from DownloadBean::class).list
        beans.forEach {
            missionMap.put(it.url, DownloadMission(it))
        }
    }

    private fun syncToDb() {
        missionMap.forEach {
            _, mission ->
            mission.save()
        }
    }

    fun download(url: String, saveName: String, savePath: String) {
        // 检查missionMap中是否存在
        val mission = missionMap.getOrPut(url, {
            DownloadMission(
                    DownloadBean(url = url, saveName = saveName, savePath = savePath)
            )
        }).apply { pause = false }

        val processor = processorMap.getOrPut(url, {
            BehaviorProcessor.create<DownloadEvent>()
        })

        // 开始下载
        Flowable.create<DownloadEvent>({
            // 检查状态

            processor.onNext(DownloadEvent(DownloadStatus.READY))

            while (!semaphore.tryAcquire()) {
                if (mission.pause) {
                    processor.onNext(DownloadEvent(DownloadStatus.PAUSED))
                    return@create
                }
            }

            processor.onNext(DownloadEvent(DownloadStatus.STARTED, mission.bean.downloadLength, mission.bean.contentLength))

            downloadApi.download(mission.bean.url, mission.bean.getRange(), mission.bean.getIfRange())
                    .subscribeOn(Schedulers.io())
                    .doAfterTerminate { semaphore.release() }
                    .subscribe({
                        response ->
                        try {
                            val header = response.headers()
                            val body = response.body()

                            mission.bean.lastModified = header.get("Last-Modified")
                            mission.bean.etag = header.get("ETag")

                            var count: Int
                            val data = ByteArray(1024 * 8)
                            val fileSize: Long = body.contentLength()

                            if (response.code() == 200) {
                                mission.bean.downloadLength = 0
                                mission.bean.contentLength = fileSize
                                mission.bean.prepareFile()
                            }

                            val inputStream = BufferedInputStream(body.byteStream(), 1024 * 8)
                            val file = mission.bean.getRandomAccessFile()
                            val fileChannel = file.channel
                            val outputStream = fileChannel.map(FileChannel.MapMode.READ_WRITE, mission.bean.downloadLength, fileSize)

                            count = inputStream.read(data)
                            while (count != -1 && !mission.pause) {
                                mission.bean.downloadLength += count
                                outputStream.put(data, 0, count)
                                processor.onNext(DownloadEvent(DownloadStatus.STARTED, mission.bean.downloadLength, mission.bean.contentLength))

                                count = inputStream.read(data)
                            }

                            if (mission.pause) {
                                processor.onNext(DownloadEvent(DownloadStatus.PAUSED))
                            }

                            if (mission.bean.downloadLength == mission.bean.contentLength) {
                                processor.onNext(DownloadEvent(DownloadStatus.COMPLETED, mission.bean.downloadLength, mission.bean.contentLength))
                            }

                            fileChannel.close()
                            file.close()
                        } catch (error: Exception) {
                            error.printStackTrace()
                        } finally {
                            semaphore.release()
                        }
                    }, {
                        error ->
                        error.printStackTrace()
                        processor.onNext(DownloadEvent(DownloadStatus.FAILED))
                        semaphore.release()
                    })
        }, BackpressureStrategy.LATEST)
                .publish()
                .connect()
    }

    fun pause(url: String) {
        missionMap[url]?.let {
            it.pause = true
        }
    }

    fun cancel(url: String) {
        missionMap[url]?.let {
            it.pause = true
            it.bean.getFile().delete()
            missionMap.remove(url)
            processorMap.remove(url)
        }
    }

    fun receive(url: String): BehaviorProcessor<DownloadEvent> {
        val mission = missionMap[url]!! // 可能不存在吗？
        val processor = processorMap.getOrPut(url, {
            BehaviorProcessor.create<DownloadEvent>()
                    .apply {
                        onNext(DownloadEvent(DownloadStatus.PAUSED, mission.bean.downloadLength, mission.bean.contentLength)) // 默认暂停状态
                    }
        })
        return processor
    }

    inner class DownloadBinder: Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}

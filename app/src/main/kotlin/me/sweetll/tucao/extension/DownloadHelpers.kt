package me.sweetll.tucao.extension

import android.Manifest
import android.app.Activity
import android.os.Environment
import android.os.Environment.DIRECTORY_DOWNLOADS
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.business.download.event.RefreshDownloadingVideoEvent
import me.sweetll.tucao.business.download.model.Part
import me.sweetll.tucao.business.download.model.Video
import me.sweetll.tucao.di.service.XmlApiService
import me.sweetll.tucao.model.json.Result
import org.greenrobot.eventbus.EventBus
import zlc.season.rxdownload2.RxDownload
import zlc.season.rxdownload2.entity.DownloadFlag
import javax.inject.Inject
import com.github.salomonbrys.kotson.*
import com.google.gson.GsonBuilder
import me.sweetll.tucao.business.download.event.RefreshDownloadedVideoEvent
import me.sweetll.tucao.business.download.model.ExcludeStateConstrollerStrategy
import java.io.File
import android.preference.PreferenceManager


object DownloadHelpers {
    private val DOWNLOAD_FILE_NAME = "download"

    private val KEY_S_DOWNLOAD_VIDEO = "download_video"

    private val defaultPath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).path + "/me.sweetll.tucao"
    private val rxDownload: RxDownload = RxDownload.getInstance().context(AppApplication.get())

    private val serviceInstance = ServiceInstance()

    private val gson = GsonBuilder()
            .setExclusionStrategies(ExcludeStateConstrollerStrategy())
            .create()

    fun getDownloadFolder(): File {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(AppApplication.get())
        val downloadPath = sharedPref.getString("download_path", defaultPath)
        val downloadFolder = File(downloadPath)
        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs()
        }
        return downloadFolder
    }

    fun loadDownloadVideos(): MutableList<MultiItemEntity> {
        /*
        val part1 = Part("P1", 1024)
        val part2 = Part("P2", 1024)
        val video1 = Video("11", "Video1", "", 2048)
        video1.addSubItem(part1)
        video1.addSubItem(part2)
        val video2 = Video("22", "Video2", "", 1024)
        val part3 = Part("P1", 1024)
        video2.addSubItem(part3)

        val data = mutableListOf<MultiItemEntity>(video1, video2)

        return data
        */
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        val jsonString = sp.getString(KEY_S_DOWNLOAD_VIDEO, "[]")
        return gson.fromJson<List<Video>>(jsonString).toMutableList()
    }

    fun loadDownloadingVideos(): MutableList<MultiItemEntity> = loadDownloadVideos()
            .filter {
                video ->
                (video as Video).subItems.any {
                    it.flag != DownloadFlag.COMPLETED
                }
            }
            .map {
                video ->
                (video as Video).subItems.removeAll { it.flag == DownloadFlag.COMPLETED }
                video
            }
            .toMutableList()

    fun loadDownloadedVideos(): MutableList<MultiItemEntity> = loadDownloadVideos()
            .filter {
                video ->
                (video as Video).subItems.any {
                    it.flag == DownloadFlag.COMPLETED
                }
            }
            .map {
                video ->
                video as Video
                video.subItems.removeAll { it.flag != DownloadFlag.COMPLETED }
                video.status.totalSize = video.subItems.sumByLong { it.status.totalSize }
                video.status.downloadSize = video.status.totalSize
                video
            }
            .toMutableList()

    fun saveDownloadVideo(video: Video) {
        val videos = loadDownloadVideos()

        val existVideo = videos.find { (it as Video).hid == video.hid }
        if (existVideo != null) {
            existVideo as Video
            existVideo.addSubItem(video.getSubItem(0))
            existVideo.subItems.sortBy(Part::order)
        } else {
            videos.add(0, video)
        }

        val jsonString = gson.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
    }

    // 保存已下载的视频
    fun saveDownloadPart(part: Part) {
        val videos = loadDownloadVideos()
        val existVideo = videos.flatMap {
            (it as Video).subItems
        }.find { it. vid == part.vid}
        existVideo?.flag = part.flag
        existVideo?.status = part.status

        val jsonString = gson.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
    }

    fun startDownload(activity: Activity, result: Result) {
        RxPermissions(activity)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .doOnNext {
                    granted ->
                    if (!granted) {
                        throw RuntimeException("请给予写存储卡权限以供离线缓存使用")
                    }
                    "已开始下载".toast()
                }
                .flatMap {
                    Observable.fromIterable(result.video)
                }
                .subscribe({
                    video ->
                    download(Video(result.hid, result.title, result.thumb, singlePart = result.part == 1), Part(video.title, video.order, video.vid, video.type))
                })
    }

    fun startDownload(part: Part) {
        part.durls.forEach {
            rxDownload.serviceDownload(it.url, it.cacheFileName, it.cacheFolderPath).subscribe()
        }
    }

    private fun download(video: Video, part: Part) {
            serviceInstance.xmlApiService.playUrl(part.type, part.vid, System.currentTimeMillis() / 1000)
                    .subscribeOn(Schedulers.io())
                    .flatMap {
                        response ->
                        if ("succ" == response.result) {
                            Observable.just(response.durls)
                        } else {
                            Observable.error(Throwable("请求视频接口出错"))
                        }
                    }
                    .doOnNext {
                        durls ->
                        durls.forEach {
                            it.cacheFolderPath = "${getDownloadFolder().absolutePath}/${video.hid}/p${part.order}"
                            it.cacheFileName = "${it.order}"
                        }
                        part.durls.addAll(durls)
                        durls.forEach {
                            rxDownload.serviceDownload(it.url, it.cacheFileName, it.cacheFolderPath).subscribe()
                        }
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        video.addSubItem(part)
                        saveDownloadVideo(video)
                    }, {
                        error ->
                        error.printStackTrace()
                        error.message?.toast()
                    })
    }

    fun pauseDownload(part: Part) {
        part.durls.forEach {
            rxDownload.pauseServiceDownload(it.url).subscribe()
        }
    }

    fun cancelDownload(parts: List<Part>) {
        parts.forEach {
            part ->
            part.durls.forEach {
                rxDownload.cancelServiceDownload(it.url).subscribe()
            }
        }
        deleteDownload(parts)
    }

    fun deleteDownload(parts: List<Part>) {
        val videos = loadDownloadVideos()
        videos.forEach {
            video ->
            (video as Video).subItems.removeAll {
                part ->
                parts.any { it.vid == part.vid }
            }
        }
        videos.removeAll {
            (it as Video).subItems.isEmpty()
        }

        val jsonString = gson.toJson(videos)
        val sp = DOWNLOAD_FILE_NAME.getSharedPreference()
        sp.edit {
            putString(KEY_S_DOWNLOAD_VIDEO, jsonString)
        }

        parts.forEach {
            part ->
            part.durls.forEach {
                rxDownload.deleteServiceDownload(it.url, true).subscribe()
            }
        }
        EventBus.getDefault().post(RefreshDownloadingVideoEvent())
        EventBus.getDefault().post(RefreshDownloadedVideoEvent())
    }

    interface Callback {
        fun startDownload()

        fun pauseDownload()
    }


    class ServiceInstance {
        @Inject
        lateinit var xmlApiService: XmlApiService

        init {
            AppApplication.get()
                    .getApiComponent()
                    .inject(this)
        }
    }

}

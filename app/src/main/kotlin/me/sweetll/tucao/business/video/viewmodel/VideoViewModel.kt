package me.sweetll.tucao.business.video.viewmodel

import android.databinding.ObservableField
import android.support.design.widget.BottomSheetDialog
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import me.sweetll.tucao.AppApplication
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.video.VideoActivity
import me.sweetll.tucao.business.video.adapter.DownloadPartAdapter
import me.sweetll.tucao.business.video.adapter.PartAdapter
import me.sweetll.tucao.di.service.ApiConfig
import me.sweetll.tucao.extension.DownloadHelpers
import me.sweetll.tucao.extension.logD
import me.sweetll.tucao.extension.sanitizeJson
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Result
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.widget.CustomBottomSheetDialog
import java.io.File
import java.io.FileOutputStream

class VideoViewModel(val activity: VideoActivity): BaseViewModel() {
    val result = ObservableField<Result>()

    constructor(activity: VideoActivity, result: Result) : this(activity) {
        this.result.set(result)
    }

    fun queryResult(hid: String) {
        /*
        val result = Result("title")
        result.video.addAll(arrayOf(
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123"),
                Video("title1", "189", "123")
        ))
        this.result.set(result)
        activity.loadResult(result)
        */
        jsonApiService.view(hid)
                .bindToLifecycle(activity)
                .sanitizeJson()
                .subscribe({
                    result ->
                    this.result.set(result)
                    activity.loadResult(result)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("获取视频信息...".toRegex(), "获取视频信息...[失败]")
                    }
                })
    }

    fun queryPlayUrls(hid: String, part: Int, type: String, vid: String) {
        xmlApiService.playUrl(type, vid, System.currentTimeMillis() / 1000)
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .flatMap {
                    response ->
                    if ("succ" == response.result) {
                        Observable.just(response.durls)
                    } else {
                        Observable.error(Throwable("请求视频接口出错"))
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    duals ->
                    activity.loadDuals(duals)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("解析视频地址...".toRegex(), "解析视频地址...[失败]")
                    }
                })

        rawApiService.danmu(ApiConfig.generatePlayerId(hid, part), System.currentTimeMillis() / 1000)
                .bindToLifecycle(activity)
                .subscribeOn(Schedulers.io())
                .map({
                    responseBody ->
                    val outputFile = File.createTempFile("tucao", ".xml", AppApplication.get().cacheDir)
                    val outputStream = FileOutputStream(outputFile)

                    outputStream.write(responseBody.bytes())
                    outputStream.flush()
                    outputStream.close()
                    outputFile.absolutePath
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    uri ->
                    activity.loadDanmuUri(uri)
                }, {
                    error ->
                    error.printStackTrace()
                    activity.binding.player.loadText?.let {
                        it.text = it.text.replace("全舰弹幕装填...".toRegex(), "全舰弹幕装填...[失败]")
                    }
                })
    }

    fun onClickDownload(view: View) {
        if (result.get() == null) return
        val dialog = CustomBottomSheetDialog(activity)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_pick_download_video, null)
        dialog.setContentView(dialogView)

        dialogView.findViewById(R.id.img_close).setOnClickListener {
            dialog.dismiss()
        }

        val partRecycler = dialogView.findViewById(R.id.recycler_part) as RecyclerView
        val partAdapter = DownloadPartAdapter(
                result.get().video
                        .map {
                            it.copy(checked = false)
                        }
                        .toMutableList()
        )

        val startDownloadButton = dialog.findViewById(R.id.btn_start_download) as Button
        startDownloadButton.setOnClickListener {
            view ->
            DownloadHelpers.startDownload(activity, result.get().copy(
                    video = partAdapter.data.filter(Video::checked).toMutableList()
            ))
            "已开始下载".toast()
            dialog.dismiss()
        }

        partRecycler.addOnItemTouchListener(object: OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val video = helper.getItem(position) as Video
                video.checked = !video.checked
                helper.notifyItemChanged(position)
                startDownloadButton.isEnabled = partAdapter.data.any { it.checked }
            }

        })

        partRecycler.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        partRecycler.adapter = partAdapter

        val pickAllButton = dialog.findViewById(R.id.btn_pick_all) as Button
        pickAllButton.setOnClickListener {
            view ->
            if (partAdapter.data.all { it.checked }) {
                // 取消全选
                startDownloadButton.isEnabled = false
                pickAllButton.text = "全部选择"
                partAdapter.data.forEach {
                    item ->
                    item.checked = false
                }
            } else {
                // 全选
                startDownloadButton.isEnabled = true
                pickAllButton.text = "取消全选"
                partAdapter.data.forEach {
                    item ->
                    item.checked = true
                }
            }
            partAdapter.notifyDataSetChanged()
        }

        dialog.show()
    }

    fun onClickStar(view: View) {
        "这个功能尚未实现，请期待下一个版本~".toast()
    }

}

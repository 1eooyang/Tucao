package me.sweetll.tucao.business.video

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.shuyu.gsyvideoplayer.GSYPreViewManager
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.GSYVideoPlayer
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.video.adapter.PartAdapter
import me.sweetll.tucao.business.video.viewmodel.VideoViewModel
import me.sweetll.tucao.databinding.ActivityVideoBinding
import me.sweetll.tucao.extension.toast
import me.sweetll.tucao.model.json.Result
import me.sweetll.tucao.model.json.Video
import me.sweetll.tucao.model.xml.Durl
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File
import java.io.FileOutputStream

class VideoActivity : BaseActivity() {
    lateinit var viewModel: VideoViewModel
    lateinit var binding: ActivityVideoBinding

    lateinit var orientationUtils: OrientationUtils

    lateinit var result: Result

    var isPlay = false
    var isPause = false

    lateinit var partAdapter: PartAdapter

    companion object {
        private val ARG_RESULT = "result"

        fun intentTo(context: Context, result: Result) {
            val intent = Intent(context, VideoActivity::class.java)
            intent.putExtra(ARG_RESULT, result)
            context.startActivity(intent)
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        result = intent.getParcelableExtra(ARG_RESULT)
        viewModel = VideoViewModel(this, result)

//        val mediaPlayer = GSYVideoManager.instance().mediaPlayer
//        if (mediaPlayer is IjkMediaPlayer) {
        (GSYVideoManager.instance().mediaPlayer as IjkMediaPlayer).setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "safe", 0)
//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "concat,file,subfile,http,https,tls,rtp,tcp,udp,crypto");
//        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_video)
        binding.viewModel = viewModel

        orientationUtils = OrientationUtils(this, binding.player)
        orientationUtils.isEnable = false

        // 是否可以滑动界面改变进度，声音
        binding.player.setIsTouchWiget(true)
        //关闭自动旋转
        binding.player.isRotateViewAuto = false
        binding.player.isLockLand = false
        binding.player.isShowFullAnimation = false
        binding.player.isNeedLockFull = true
        binding.player.isOpenPreView = true
        binding.player.fullscreenButton.setOnClickListener {
            view ->
            //直接横屏
            orientationUtils.resolveByClick()

            //第一个true是否需要隐藏ActionBar，第二个true是否需要隐藏StatusBar
            binding.player.startWindowFullscreen(this, true, true)
        }

        binding.player.setStandardVideoAllCallBack(object: StandardVideoAllCallBackAdapter() {
            override fun onPrepared(p0: String?, vararg p1: Any?) {
                super.onPrepared(p0, *p1)
                orientationUtils.isEnable = true
                isPlay = true
            }

            override fun onQuitFullscreen(p0: String?, vararg p1: Any?) {
                super.onQuitFullscreen(p0, *p1)
                orientationUtils.backToProtVideo()
            }
        })

        binding.player.setLockClickListener {
            view, lock ->
            orientationUtils.isEnable = !lock
        }

//        val firstVid = result.video[0].vid
//        if (firstVid != null) {
//             载入第一个视频
//            viewModel.queryPlayUrls(result.video[0].type, firstVid)
//        } else {
//            "所选视频已失效".toast()
//        }


        try {
            val outputFile = File.createTempFile("tucao", ".concat", getCacheDir())
            val outputStream = FileOutputStream(outputFile)

            val test =
                    "ffconcat version 1.0\n" +
                    "file 'http://58.216.103.180/youku/6571AA2CD214E842FB8A7A6405/0300010900553A4D76DD1718581209B15F3A81-E563-D647-2FC4-06C5A8F05A9A.flv?sid=04840553300321201a5b3_00&ctype=12'\n" +
                    "duration 200.992\n" +
                    "file 'http://222.73.245.134/youku/6772E9CDF24398118DF649471A/0300010901553A4D76DD1718581209B15F3A81-E563-D647-2FC4-06C5A8F05A9A.flv?sid=04840553300321201a5b3_00&ctype=12'\n" +
                    "duration 181.765";

            outputStream.write(test.toByteArray())

            outputStream.flush()
            outputStream.close()

            binding.player.setUp(outputFile.absolutePath, true, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }


        setupRecyclerView()
    }

    fun setupRecyclerView() {
        result.video[0].checked = true
        partAdapter = PartAdapter(result.video)

        binding.partRecycler.addOnItemTouchListener(object : OnItemClickListener() {
            override fun onSimpleItemClick(helper: BaseQuickAdapter<*, *>, view: View, position: Int) {
                val selectedVideo = helper.getItem(position) as Video
                if (!selectedVideo.checked) {
                    partAdapter.data.forEach { it.checked = false }
                    selectedVideo.checked = true
                    partAdapter.notifyDataSetChanged()

                    if (selectedVideo.vid != null) {
                        viewModel.queryPlayUrls(selectedVideo.type, selectedVideo.vid)
                    } else {
                        "所选视频已失效".toast()
                    }
                }
            }
        })
//        binding.partRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.partRecycler.layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        binding.partRecycler.adapter = partAdapter
    }

    fun loadDuals(durls: MutableList<Durl>?) {
        durls?.isNotEmpty().let {
            // TODO: 载入多段视频
            binding.player.setUp(durls!![0].url, true, null)
            "载入完成".toast()
        }
    }

    override fun onPause() {
        super.onPause()
        isPause = true
    }

    override fun onResume() {
        super.onResume()
        isPause = false
    }

    override fun onDestroy() {
        super.onDestroy()
        GSYVideoPlayer.releaseAllVideos()
        GSYPreViewManager.instance().releaseMediaPlayer()
        orientationUtils.releaseListener()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isPlay && !isPause) {
            if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_USER) {
                if (!binding.player.isIfCurrentIsFullscreen) {
                    binding.player.startWindowFullscreen(this, true, true)
                }
            } else {
                if (binding.player.isIfCurrentIsFullscreen) {
                    StandardGSYVideoPlayer.backFromWindowFull(this);
                }
                orientationUtils.isEnable = true
            }
        }
    }
}
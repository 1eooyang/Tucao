package me.sweetll.tucao.business.home.adapter

import android.widget.ImageView
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.sweetll.tucao.R
import me.sweetll.tucao.extension.formatByWan
import me.sweetll.tucao.extension.load
import me.sweetll.tucao.model.json.Channel
import me.sweetll.tucao.model.json.Result

class RecommendAdapter(data: MutableList<Pair<Channel, List<Result>>>?): BaseQuickAdapter<Pair<Channel, List<Result>>, BaseViewHolder>(R.layout.item_recommend_video, data) {
    override fun convert(helper: BaseViewHolder, item: Pair<Channel, List<Result>>) {
        val channel = item.first
        helper.setText(R.id.text_channel, channel.name)

        item.second.take(4).forEachIndexed {
            index, result ->
            val thumbImg: ImageView
            val playText: TextView
            val titleText: TextView
            when (index) {
                0 -> {
                    thumbImg = helper.getView<ImageView>(R.id.img_thumb1)
                    playText = helper.getView<TextView>(R.id.text_play1)
                    titleText = helper.getView<TextView>(R.id.text_title1)
                    helper.setTag(R.id.linear1, result.hid)
                    helper.addOnClickListener(R.id.linear1)
                }
                1 -> {
                    thumbImg = helper.getView<ImageView>(R.id.img_thumb2)
                    playText = helper.getView<TextView>(R.id.text_play2)
                    titleText = helper.getView<TextView>(R.id.text_title2)
                    helper.setTag(R.id.linear2, result.hid)
                    helper.addOnClickListener(R.id.linear2)
                }
                2 -> {
                    thumbImg = helper.getView<ImageView>(R.id.img_thumb3)
                    playText = helper.getView<TextView>(R.id.text_play3)
                    titleText = helper.getView<TextView>(R.id.text_title3)
                    helper.setTag(R.id.linear3, result.hid)
                    helper.addOnClickListener(R.id.linear3)
                }
                else -> {
                    thumbImg = helper.getView<ImageView>(R.id.img_thumb4)
                    playText = helper.getView<TextView>(R.id.text_play4)
                    titleText = helper.getView<TextView>(R.id.text_title4)
                    helper.setTag(R.id.linear4, result.hid)
                    helper.addOnClickListener(R.id.linear4)
                }
            }
            thumbImg.load(result.thumb)
            playText.text = result.play.formatByWan()
            titleText.text = result.title
        }

        for (index in data.size .. 3) {
            when (index) {
                0 -> {
                    helper.setVisible(R.id.linear1, false)
                }
                1 -> {
                    helper.setVisible(R.id.linear2, false)
                }
                2 -> {
                    helper.setVisible(R.id.linear3, false)
                }
                else -> {
                    helper.setVisible(R.id.linear4, false)
                }
            }
        }

        if (channel.id != 0) {
            helper.setText(R.id.text_more, "更多${channel.name}内容")
            helper.setTag(R.id.text_more, channel.id)
            helper.setVisible(R.id.text_more, true)
            helper.setVisible(R.id.img_rank, false)
            helper.addOnClickListener(R.id.text_more)
        } else {
            helper.setVisible(R.id.text_more, false)
            helper.setVisible(R.id.img_rank, true)
            helper.addOnClickListener(R.id.img_rank)
        }

    }
}
package me.sweetll.tucao.business.rank.viewmodel

import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import me.sweetll.tucao.base.BaseViewModel
import me.sweetll.tucao.business.rank.RankActivity
import me.sweetll.tucao.business.rank.event.ChangeRankFilterEvent
import org.greenrobot.eventbus.EventBus

class RankViewModel(rankActivity: RankActivity): BaseViewModel() {

    fun onClickFilterDate(view: View) {
        if (view is CheckedTextView && !view.isChecked) {
            val parentView = view.parent as ViewGroup
            (0 .. parentView.childCount - 1)
                    .map { parentView.getChildAt(it) as CheckedTextView }
                    .forEach {
                        it.isChecked = view == it
                    }
            EventBus.getDefault().post(ChangeRankFilterEvent((view.tag as String).toInt()))
        }
    }

}

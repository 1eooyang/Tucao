package me.sweetll.tucao.business.search

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import me.sweetll.tucao.Const
import me.sweetll.tucao.R
import me.sweetll.tucao.base.BaseActivity
import me.sweetll.tucao.business.channel.adapter.VideoAdapter
import me.sweetll.tucao.business.search.viewmodel.SearchViewModel
import me.sweetll.tucao.databinding.ActivitySearchBinding
import me.sweetll.tucao.model.json.Result

class SearchActivity : BaseActivity() {
    val viewModel = SearchViewModel(this)
    lateinit var binding: ActivitySearchBinding

    val videoAdapter = VideoAdapter(null)

    override fun getStatusBar(): View = binding.statusBar

    override fun initView(savedInstanceState: Bundle?) {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_search)
        binding.viewModel = viewModel

        binding.searchEdit.setOnEditorActionListener {
            view, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.onClickSearch(view)
                true
            }
            false
        }

        videoAdapter.setOnLoadMoreListener {
            viewModel.loadMoreData()
        }

        binding.searchRecycler.layoutManager = LinearLayoutManager(this)
        binding.searchRecycler.adapter = videoAdapter
    }

    fun loadData(data: MutableList<Result>?) {
        videoAdapter.setNewData(data)
        if (data == null || data.size < viewModel.pageSize) {
            videoAdapter.setEnableLoadMore(false)
        }
    }

    fun loadMoreData(data: MutableList<Result>?, flag: Int) {
        when (flag) {
            Const.LOAD_MORE_COMPLETE -> {
                videoAdapter.addData(data)
                videoAdapter.loadMoreComplete()
            }
            Const.LOAD_MORE_END -> {
                videoAdapter.addData(data)
                videoAdapter.loadMoreEnd()
            }
            Const.LOAD_MORE_FAIL -> {
                videoAdapter.loadMoreFail()
            }
        }
    }

}

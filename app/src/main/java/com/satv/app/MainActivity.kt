package com.satv.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.satv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: ChannelAdapter
    private var all: List<Channel> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        adapter = ChannelAdapter { channel ->
            val index = all.indexOfFirst { it.url == channel.url }.coerceAtLeast(0)
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putStringArrayListExtra("channel_names", ArrayList(all.map { it.name }))
                putStringArrayListExtra("channel_urls", ArrayList(all.map { it.url }))
                putExtra("channel_index", index)
            })
        }
        b.recyclerChannels.layoutManager = LinearLayoutManager(this)
        b.recyclerChannels.adapter = adapter
        b.buttonRefresh.setOnClickListener { load(true) }
        var immersive = false
        b.buttonFullScreen.setOnClickListener {
            immersive = !immersive
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                b.buttonFullScreen.text = "Exit full"
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                b.buttonFullScreen.text = "Full screen"
            }
        }
        b.editSearch.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { filter(b.editSearch.text.toString()); true } else false
        }
        b.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { filter(s?.toString() ?: "") }
        })
        load(false)
    }

    private fun load(refresh: Boolean) {
        b.progress.isVisible = true
        all = if (refresh) ChannelRepository.refresh(this) else ChannelRepository.getChannels(this)
        adapter.submitList(all)
        b.progress.isVisible = false
        b.textEmpty.isVisible = all.isEmpty()
    }

    private fun filter(q: String) {
        val term = q.trim().lowercase()
        val filtered = if (term.isEmpty()) all else all.filter { it.name.lowercase().contains(term) || it.category.lowercase().contains(term) }
        adapter.submitList(filtered)
        b.textEmpty.isVisible = filtered.isEmpty()
    }
}

package com.satv.app
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.satv.app.databinding.ActivityMainBinding
class MainActivity:AppCompatActivity(){
    private lateinit var b:ActivityMainBinding
    private lateinit var adapter:ChannelAdapter
    private var all:List<Channel> = emptyList()
    override fun onCreate(s:Bundle?){ super.onCreate(s); b=ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        adapter=ChannelAdapter{ c -> startActivity(Intent(this,PlayerActivity::class.java).putExtra("channel_name",c.name).putExtra("channel_url",c.url)) }
        b.recyclerChannels.layoutManager=LinearLayoutManager(this); b.recyclerChannels.adapter=adapter
        b.buttonRefresh.setOnClickListener{ load(true) }
        b.editSearch.setOnEditorActionListener{_,a,_-> if(a==EditorInfo.IME_ACTION_SEARCH){filter(b.editSearch.text.toString());true}else false}
        b.editSearch.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}; override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}
            override fun afterTextChanged(s:android.text.Editable?){filter(s?.toString()?:"")} })
        load(false) }
    private fun load(refresh:Boolean){ b.progress.isVisible=true; all=if(refresh) ChannelRepository.refresh(this) else ChannelRepository.getChannels(this); adapter.submitList(all); b.progress.isVisible=false; b.textEmpty.isVisible=all.isEmpty() }
    private fun filter(q:String){ val t=q.trim().lowercase(); val f=if(t.isEmpty()) all else all.filter{it.name.lowercase().contains(t)||it.category.lowercase().contains(t)}; adapter.submitList(f); b.textEmpty.isVisible=f.isEmpty() }
}

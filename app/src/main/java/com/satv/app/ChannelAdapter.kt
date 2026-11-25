package com.satv.app
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.satv.app.databinding.ItemChannelBinding
class ChannelAdapter(private val onClick:(Channel)->Unit):ListAdapter<Channel,ChannelAdapter.ChannelViewHolder>(DIFF){
    inner class ChannelViewHolder(private val b:ItemChannelBinding):RecyclerView.ViewHolder(b.root){
        fun bind(c:Channel){ b.textChannelName.text=c.name; b.textCategory.text=c.category; b.root.setOnClickListener{onClick(c)} } }
    override fun onCreateViewHolder(p:ViewGroup,v:Int)=ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(p.context),p,false))
    override fun onBindViewHolder(h:ChannelViewHolder,pos:Int)=h.bind(getItem(pos))
    companion object{ val DIFF=object:DiffUtil.ItemCallback<Channel>(){ override fun areItemsTheSame(o:Channel,n:Channel)=o.name==n.name&&o.url==n.url; override fun areContentsTheSame(o:Channel,n:Channel)=o==n } }
}

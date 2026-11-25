package com.satv.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.satv.app.databinding.ItemChannelBinding

class ChannelAdapter(private val onClick: (Channel) -> Unit) : ListAdapter<Channel, ChannelAdapter.ChannelViewHolder>(DIFF) {
    inner class ChannelViewHolder(private val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(channel: Channel) {
            val number = (bindingAdapterPosition + 1).coerceAtLeast(1)
            b.textChannelNumber.text = String.format("%03d", number)
            b.textChannelName.text = channel.name
            b.textCategory.text = channel.category
            b.textLogoFallback.text = channel.name.trim().take(1).uppercase()
            b.textLogoFallback.visibility = View.VISIBLE
            b.imageChannelLogo.visibility = View.GONE
            val logo = channel.logoUrl
            if (!logo.isNullOrBlank()) {
                Glide.with(b.imageChannelLogo).load(logo).centerInside().into(b.imageChannelLogo)
                b.imageChannelLogo.visibility = View.VISIBLE
                b.textLogoFallback.visibility = View.GONE
            }
            b.root.setOnClickListener { onClick(channel) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ChannelViewHolder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) = holder.bind(getItem(position))
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(old: Channel, new: Channel) = old.name == new.name && old.url == new.url
            override fun areContentsTheSame(old: Channel, new: Channel) = old == new
        }
    }
}

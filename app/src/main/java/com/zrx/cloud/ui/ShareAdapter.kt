package com.zrx.cloud.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zrx.cloud.data.ShareItem
import com.zrx.cloud.databinding.ItemShareBinding

class ShareAdapter(
    private val onCopy: (ShareItem) -> Unit,
    private val onCancel: (ShareItem) -> Unit,
) : RecyclerView.Adapter<ShareAdapter.VH>() {

    private val items = mutableListOf<ShareItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<ShareItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemShareBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemShareBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.name.text = if (item.dir) "${item.name}（文件夹）" else item.name
        h.b.url.text = item.url
        h.b.meta.text = if (item.alive) {
            "下载 ${item.hits} 次  ·  ${item.createdAt}"
        } else {
            "源文件已删除  ·  ${item.createdAt}"
        }
        h.b.btnCopy.setOnClickListener { onCopy(item) }
        h.b.btnCancel.setOnClickListener { onCancel(item) }
    }
}

package com.zrx.cloud.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zrx.cloud.R
import com.zrx.cloud.data.FileItem
import com.zrx.cloud.databinding.ItemFileBinding
import com.zrx.cloud.util.Format

class FileAdapter(
    private val onClick: (FileItem) -> Unit,
    private val onLongClick: (FileItem) -> Boolean,
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private val items = mutableListOf<FileItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<FileItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.name.text = item.name
        h.b.meta.text = if (item.dir) "文件夹  ·  ${item.time}"
        else "${Format.bytes(item.size)}  ·  ${item.time}"
        h.b.icon.setImageResource(if (item.dir) R.drawable.ic_folder else R.drawable.ic_file)
        h.b.root.setOnClickListener { onClick(item) }
        h.b.root.setOnLongClickListener { onLongClick(item) }
    }
}

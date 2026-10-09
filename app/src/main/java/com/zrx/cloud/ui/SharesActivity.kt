package com.zrx.cloud.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zrx.cloud.R
import com.zrx.cloud.data.NetDiskApi
import com.zrx.cloud.data.ShareItem
import com.zrx.cloud.databinding.ActivitySharesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharesActivity : AppCompatActivity() {

    private lateinit var b: ActivitySharesBinding
    private val api by lazy { NetDiskApi() }
    private val adapter = ShareAdapter(::copyLink, ::cancelConfirm)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySharesBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.shares)
        b.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear) {
                clearAllConfirm()
                true
            } else false
        }

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { api.shares() }
                adapter.submit(items)
                b.empty.isVisible = items.isEmpty()
            } catch (e: Exception) {
                toast(e.message ?: "加载失败")
            }
        }
    }

    private fun copyLink(item: ShareItem) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("share", item.url))
        toast(getString(R.string.copied))
    }

    private fun cancelConfirm(item: ShareItem) {
        AlertDialog.Builder(this)
            .setTitle("取消分享")
            .setMessage("确定取消「${item.name}」的分享？链接将立即失效。")
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.unshare(item.id) }
                        refresh()
                    } catch (e: Exception) {
                        toast(e.message ?: "操作失败")
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearAllConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all)
            .setMessage("确定清空全部分享？所有分享链接将立即失效。")
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.unshareAll() }
                        refresh()
                    } catch (e: Exception) {
                        toast(e.message ?: "操作失败")
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

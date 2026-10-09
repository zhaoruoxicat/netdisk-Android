package com.zrx.cloud.ui

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.zrx.cloud.R
import com.zrx.cloud.data.FileItem
import com.zrx.cloud.data.NetDiskApi
import com.zrx.cloud.data.Session
import com.zrx.cloud.databinding.ActivityMainBinding
import com.zrx.cloud.upload.UploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val api by lazy { NetDiskApi() }
    private val adapter = FileAdapter(::onItemClick, ::onItemLongClick)

    private var currentPath = ""
    private var loading = false

    /* ---------------- 权限与选择器 ---------------- */

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            uris.forEach {
                runCatching {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            ensureNotificationPermission()
            UploadService.enqueue(this, uris, currentPath)
            toast("已加入上传队列（${uris.size} 个），进度见通知栏")
        }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    /* ---------------- 生命周期 ---------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.swipe.setOnRefreshListener { refresh() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPath.isNotEmpty()) {
                    currentPath = parentOf(currentPath)
                    refresh()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, refreshReceiver,
            IntentFilter(UploadService.ACTION_REFRESH),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(refreshReceiver)
    }

    /* ---------------- 列表 ============================== */

    private fun refresh() {
        if (loading) return
        loading = true
        b.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { api.list(currentPath) }
                adapter.submit(items)
                b.empty.isVisible = items.isEmpty()
                b.toolbar.title = if (currentPath.isEmpty()) getString(R.string.app_name)
                else currentPath.substringAfterLast('/')
            } catch (e: Exception) {
                if (e.message?.contains("登录已失效") == true) {
                    logout()
                    return@launch
                }
                toast(e.message ?: "加载失败")
            } finally {
                loading = false
                b.swipe.isRefreshing = false
            }
        }
    }

    private fun parentOf(p: String): String =
        if ('/' in p) p.substringBeforeLast('/') else ""

    private fun onItemClick(item: FileItem) {
        if (item.dir) {
            currentPath = item.path
            refresh()
        } else {
            showFileOptions(item)
        }
    }

    private fun onItemLongClick(item: FileItem): Boolean {
        showFileOptions(item)
        return true
    }

    /* ---------------- 文件操作 ============================== */

    private fun showFileOptions(item: FileItem) {
        val ops = if (item.dir) {
            arrayOf(getString(R.string.op_share), getString(R.string.op_rename), getString(R.string.op_move), getString(R.string.op_delete))
        } else {
            arrayOf(getString(R.string.op_download), getString(R.string.op_share), getString(R.string.op_rename), getString(R.string.op_move), getString(R.string.op_delete))
        }
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(ops) { _, which ->
                when (ops[which]) {
                    getString(R.string.op_download) -> download(item)
                    getString(R.string.op_share) -> share(item)
                    getString(R.string.op_rename) -> renameDialog(item)
                    getString(R.string.op_move) -> moveDialog(item)
                    getString(R.string.op_delete) -> deleteConfirm(item)
                }
            }
            .show()
    }

    /** 用系统下载器，支持断点续传和系统通知 */
    private fun download(item: FileItem) {
        val req = DownloadManager.Request(Uri.parse(api.downloadUrl(item.path)))
            .setTitle(item.name)
            .setDescription(getString(R.string.app_name))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.name)
        Session.cookieHeader.takeIf { it.isNotEmpty() }?.let { req.addRequestHeader("Cookie", it) }
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        toast("开始下载：${item.name}")
    }

    private fun share(item: FileItem) {
        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) { api.share(item.path) }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.share_link_title)
                    .setMessage(url)
                    .setPositiveButton(R.string.copy) { _, _ -> copyText(url) }
                    .setNegativeButton(R.string.close, null)
                    .show()
            } catch (e: Exception) {
                toast(e.message ?: "分享失败")
            }
        }
    }

    private fun renameDialog(item: FileItem) {
        inputDialog("重命名", item.name) { name ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { api.rename(item.path, name) }
                    refresh()
                } catch (e: Exception) {
                    toast(e.message ?: "重命名失败")
                }
            }
        }
    }

    private fun moveDialog(item: FileItem) {
        inputDialog("移动到目录（留空为根目录）", currentPath) { dest ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { api.move(listOf(item.path), dest.trim('/')) }
                    refresh()
                } catch (e: Exception) {
                    toast(e.message ?: "移动失败")
                }
            }
        }
    }

    private fun deleteConfirm(item: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定删除「${item.name}」？${if (item.dir) "文件夹内的所有内容也会被删除。" else ""}")
            .setPositiveButton(R.string.op_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.delete(listOf(item.path)) }
                        refresh()
                    } catch (e: Exception) {
                        toast(e.message ?: "删除失败")
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun mkdirDialog() {
        inputDialog("新建文件夹", "") { name ->
            if (name.isEmpty()) return@inputDialog
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { api.mkdir(currentPath, name) }
                    refresh()
                } catch (e: Exception) {
                    toast(e.message ?: "创建失败")
                }
            }
        }
    }

    /* ---------------- 菜单与账号 ============================== */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_upload -> {
            pickFiles.launch(arrayOf("*/*"))
            true
        }
        R.id.action_mkdir -> {
            mkdirDialog()
            true
        }
        R.id.action_shares -> {
            startActivity(Intent(this, SharesActivity::class.java))
            true
        }
        R.id.action_logout -> {
            logout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun logout() {
        Session.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /* ---------------- 小工具 ============================== */

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun inputDialog(title: String, initial: String, onOk: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val edit = view.findViewById<EditText>(R.id.edit)
        edit.setText(initial)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.confirm) { _, _ -> onOk(edit.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyText(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("share", text))
        toast(getString(R.string.copied))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

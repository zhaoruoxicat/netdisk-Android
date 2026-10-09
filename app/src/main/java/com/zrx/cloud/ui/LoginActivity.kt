package com.zrx.cloud.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.zrx.cloud.data.ApiException
import com.zrx.cloud.data.NetDiskApi
import com.zrx.cloud.data.Session
import com.zrx.cloud.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.loggedIn) {
            goMain()
            return
        }
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etServer.setText(Session.baseUrl)
        b.etUser.setText(Session.username)
        b.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val server = b.etServer.text.toString().trim()
        val user = b.etUser.text.toString().trim()
        val pass = b.etPass.text.toString()
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            toast("请填写服务器地址、用户名和密码")
            return
        }
        b.btnLogin.isEnabled = false
        b.progress.isVisible = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { NetDiskApi().login(server, user, pass) }
                goMain()
            } catch (e: Exception) {
                Session.logout() // 没登上就别留着半截会话
                toast(e.message ?: "登录失败")
                b.btnLogin.isEnabled = true
                b.progress.isVisible = false
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

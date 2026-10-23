package com.example.douyindownloader

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnDownload: Button
    private var videoDownloadUrl: String? = null
    private var lastCheckedClip: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        tvStatus = findViewById(R.id.tvStatus)
        btnDownload = findViewById(R.id.btnDownload)
        val btnPaste = findViewById<Button>(R.id.btnPaste)
        val btnParse = findViewById<Button>(R.id.btnParse)

        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString() ?: ""
                extractAndFillUrl(text)
            }
        }

        btnParse.setOnClickListener {
            val urlText = etUrl.text.toString().trim()
            if (urlText.isNotEmpty()) {
                parseDouyinVideo(urlText)
            } else {
                Toast.makeText(this, "请先输入或粘贴链接", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownload.setOnClickListener {
            if (!videoDownloadUrl.isNullOrEmpty()) {
                startSystemDownload(videoDownloadUrl!!)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardAuto()
    }

    private fun checkClipboardAuto() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0) {
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipText.isNotEmpty() && clipText != lastCheckedClip) {
                if (clipText.contains("douyin.com") || clipText.contains("v.douyin.com")) {
                    lastCheckedClip = clipText
                    extractAndFillUrl(clipText)
                    Toast.makeText(this, "已自动检测到抖音链接", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractAndFillUrl(text: String) {
        val regex = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+".toRegex()
        val match = regex.find(text)
        if (match != null) {
            etUrl.setText(match.value)
            tvStatus.text = "状态：已自动填入链接，请点击解析"
        }
    }

    private fun parseDouyinVideo(shareUrl: String) {
        tvStatus.text = "状态：正在解析无水印原视频..."
        btnDownload.isEnabled = false

        val apiUrl = "https://api.pearkcookie.cn/api/douyin/index.php?url=${Uri.encode(shareUrl)}"
        val client = OkHttpClient()
        val request = Request.Builder().url(apiUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "状态：解析失败，网络异常"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    try {
                        val json = JSONObject(responseBody ?: "")
                        if (json.optInt("code") == 200 || json.optInt("status") == 200) {
                            val data = json.optJSONObject("data") ?: json
                            videoDownloadUrl = data.optString("url")
                            if (videoDownloadUrl.isNullOrEmpty()) {
                                videoDownloadUrl = data.optString("nowm_addr")
                            }

                            if (!videoDownloadUrl.isNullOrEmpty()) {
                                tvStatus.text = "状态：解析成功！准备下载"
                                btnDownload.isEnabled = true
                            } else {
                                tvStatus.text = "状态：解析失败，未找到视频地址"
                            }
                        } else {
                            tvStatus.text = "状态：解析接口返回错误"
                        }
                    } catch (e: Exception) {
                        tvStatus.text = "状态：数据解析异常"
                    }
                }
            }
        })
    }

    private fun startSystemDownload(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("抖音无水印视频")
                setDescription("正在下载原视频...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "DouYin_${System.currentTimeMillis()}.mp4")
                allowScanningByMediaScanner()
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "已开始下载，请在系统相册/视频中查看", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

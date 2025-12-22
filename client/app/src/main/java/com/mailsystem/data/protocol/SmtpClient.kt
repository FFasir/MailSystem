package com.mailsystem.data.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.regex.Pattern  // 新增：导入正则工具

/**
 * SMTP 客户端 - 直接通过 SMTP 协议发送邮件
 */
class SmtpClient(
    private val host: String = "10.0.2.2",  // Android 模拟器访问本机
    private val port: Int = 2525
) {
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    // ========== 新增：邮箱格式校验函数（核心） ==========
    fun isEmailValid(email: String): Boolean {
        if (email.isBlank()) return false
        // 标准邮箱正则：匹配 xxx@xxx.xxx 格式
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        val pattern = Pattern.compile(emailPattern)
        val matcher = pattern.matcher(email)
        return matcher.matches()
    }

    /**
     * 发送邮件
     */
    suspend fun sendMail(
        fromAddr: String,
        toAddr: String,
        subject: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        // ========== 新增：发送前先校验收件人格式 ==========
        if (!isEmailValid(toAddr)) {
            return@withContext Result.failure(Exception("收件人邮箱格式无效（需填写 xxx@xxx.xxx 格式）"))
        }

        try {
            // 连接到服务器
            connect()
            
            // 读取欢迎消息
            val welcome = readLine()
            if (!welcome.startsWith("220")) {
                return@withContext Result.failure(Exception("连接失败: $welcome"))
            }
            
            // HELO 握手
            sendCommand("HELO client")
            val heloResponse = readLine()
            if (!heloResponse.startsWith("250")) {
                return@withContext Result.failure(Exception("HELO 失败: $heloResponse"))
            }
            
            // MAIL FROM
            sendCommand("MAIL FROM:<$fromAddr>")
            val mailFromResponse = readLine()
            if (!mailFromResponse.startsWith("250")) {
                return@withContext Result.failure(Exception("MAIL FROM 失败: $mailFromResponse"))
            }
            
            // RCPT TO
            sendCommand("RCPT TO:<$toAddr>")
            val rcptToResponse = readLine()
            if (!rcptToResponse.startsWith("250")) {
                val errorMsg = when {
                    rcptToResponse.startsWith("550") -> "收件人不存在或不可用"
                    rcptToResponse.contains("blocked") -> "收件人地址被拒绝（黑名单）"
                    else -> "RCPT TO 失败"
                }
                return@withContext Result.failure(Exception("$errorMsg: $rcptToResponse"))
            }
            
            // DATA
            sendCommand("DATA")
            val dataResponse = readLine()
            if (!dataResponse.startsWith("354")) {
                return@withContext Result.failure(Exception("DATA 失败: $dataResponse"))
            }

            // 发送邮件内容（修复：非空断言，避免空指针）
            val mailContent = buildString {
                append("From: $fromAddr\r\n")
                append("To: $toAddr\r\n")
                append("Subject: $subject\r\n")
                append("\r\n")
                append(body)
                append("\r\n.\r\n")
            }
            writer!!.write(mailContent)  // 修复：connect后writer非空，用!!断言
            writer!!.flush()

            val sendResponse = readLine()
            if (!sendResponse.startsWith("250")) {
                return@withContext Result.failure(Exception("邮件发送失败: $sendResponse"))
            }

            // QUIT
            sendCommand("QUIT")
            readLine()
            
            disconnect()
            
            Result.success("邮件发送成功")
            
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }
    
    private fun connect() {
        socket = Socket(host, port)
        reader = BufferedReader(InputStreamReader(socket?.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket?.getOutputStream(), Charsets.UTF_8))
    }
    
    private fun disconnect() {
        try {
            writer?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        try {
            reader?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        try {
            socket?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        socket = null
        reader = null
        writer = null
    }

    // 修复：sendCommand添加非空断言
    private fun sendCommand(command: String) {
        writer!!.write("$command\r\n")  // connect后writer非空
        writer!!.flush()
    }
    
    private fun readLine(): String {
        return reader?.readLine() ?: ""
    }
}

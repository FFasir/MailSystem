package com.mailsystem.data.protocol

import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * POP3 邮件信息
 */
data class Pop3Mail(
    val id: Int,
    val size: Int,
    val content: String = ""
)

/**
 * POP3 客户端 - 直接通过 POP3 协议收取邮件
 */
class Pop3Client(
    private val host: String = "10.0.2.2",  // Android 模拟器访问本机
    private val port: Int = 8110
) {
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    
    /**
     * 登录并获取邮件列表
     */
    suspend fun login(username: String, password: String): Result<List<Pop3Mail>> = 
        withContext(Dispatchers.IO) {
        try {
            // 连接到服务器
            connect()
            
            // 读取欢迎消息
            val welcome = readLine()
            if (!welcome.startsWith("+OK")) {
                return@withContext Result.failure(Exception("连接失败: $welcome"))
            }
            
            // USER 命令
            sendCommand("USER $username")
            val userResponse = readLine()
            if (!userResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("用户名错误: $userResponse"))
            }
            
            // PASS 命令
            sendCommand("PASS $password")
            val passResponse = readLine()
            if (!passResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("密码错误: $passResponse"))
            }
            
            // LIST 命令获取邮件列表
            sendCommand("LIST")
            val listResponse = readLine()
            if (!listResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("获取列表失败: $listResponse"))
            }
            
            // 读取邮件列表
            val mails = mutableListOf<Pop3Mail>()
            while (true) {
                val line = readLine()
                if (line == ".") break
                
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    val id = parts[0].toIntOrNull() ?: continue
                    val size = parts[1].toIntOrNull() ?: continue
                    mails.add(Pop3Mail(id, size))
                }
            }
            
            disconnect()
            
            Result.success(mails)
            
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }
    
    /**
     * 获取邮件内容
     */
    suspend fun retrieveMail(username: String, password: String, mailId: Int): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            // 连接并登录
            connect()
            readLine() // 欢迎消息
            
            sendCommand("USER $username")
            readLine()
            
            sendCommand("PASS $password")
            val passResponse = readLine()
            if (!passResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("认证失败"))
            }
            
            // RETR 命令获取邮件内容
            sendCommand("RETR $mailId")
            val retrResponse = readLine()
            if (!retrResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("获取邮件失败: $retrResponse"))
            }
            
            // 读取邮件内容
            val content = buildString {
                while (true) {
                    val line = readLine()
                    if (line == ".") break
                    append(line)
                    append("\n")
                }
            }
            
            // QUIT
            sendCommand("QUIT")
            readLine()
            
            disconnect()
            
            Result.success(content)
            
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }
    
    /**
     * 删除邮件
     */
    suspend fun deleteMail(username: String, password: String, mailId: Int): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            // 连接并登录
            connect()
            readLine() // 欢迎消息
            
            sendCommand("USER $username")
            readLine()
            
            sendCommand("PASS $password")
            val passResponse = readLine()
            if (!passResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("认证失败"))
            }
            
            // DELE 命令删除邮件
            sendCommand("DELE $mailId")
            val deleResponse = readLine()
            if (!deleResponse.startsWith("+OK")) {
                return@withContext Result.failure(Exception("删除失败: $deleResponse"))
            }
            
            // QUIT（执行删除）
            sendCommand("QUIT")
            val quitResponse = readLine()
            Log.d("Pop3Client", "QUIT response: $quitResponse")
            
            disconnect()
            
            Result.success("邮件已删除")
            
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
    
    private fun sendCommand(command: String) {
        writer?.write("$command\r\n")
        writer?.flush()
    }
    
    private fun readLine(): String {
        return reader?.readLine() ?: ""
    }
}

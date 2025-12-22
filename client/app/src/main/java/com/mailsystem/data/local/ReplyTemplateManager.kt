package com.mailsystem.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mailsystem.data.model.ReplyTemplate
import java.util.UUID

class ReplyTemplateManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("reply_templates", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getTemplates(): List<ReplyTemplate> {
        val json = prefs.getString("templates", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<ReplyTemplate>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                persistDefaults()
            }
        } else {
            persistDefaults()
        }
    }

    fun saveTemplate(template: ReplyTemplate) {
        val templates = getTemplates().toMutableList()
        val index = templates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            templates[index] = template
        } else {
            templates.add(template)
        }
        saveTemplates(templates)
    }

    fun deleteTemplate(id: String) {
        val templates = getTemplates().toMutableList()
        templates.removeAll { it.id == id }
        saveTemplates(templates)
    }

    private fun saveTemplates(templates: List<ReplyTemplate>) {
        prefs.edit().putString("templates", gson.toJson(templates)).apply()
    }

    private fun getDefaultTemplates(): List<ReplyTemplate> {
        // Stable IDs avoid duplicate/default templates being treated as new entries
        return listOf(
            ReplyTemplate("default_ack", "确认收到", "您好，邮件已收到，我会尽快处理。", "通用"),
            ReplyTemplate("default_later", "稍后回复", "您好，我现在比较忙，稍后会详细回复您。", "通用"),
            ReplyTemplate("default_thanks", "谢谢", "谢谢您的分享！", "感谢"),
            ReplyTemplate("default_agree", "同意", "我同意您的提议。", "商务")
        )
    }

    private fun persistDefaults(): List<ReplyTemplate> {
        val defaults = getDefaultTemplates()
        saveTemplates(defaults)
        return defaults
    }
}

package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 项目启动后自动执行：复制插件内置模板到 my-easy-code/templates，
 * 并创建 mapper.yml 示例文件，方便用户按需修改。
 */
class StartupImporter : StartupActivity {
    override fun runActivity(project: Project) {
        val base = project.basePath ?: return
        val root = Path.of(base).resolve("my-easy-code")
        val templatesDir = root.resolve("templates")
        Files.createDirectories(templatesDir)
        // 取消与 mapper 映射相关的初始化
        // 内置模板清单（按需扩展）
        val names = listOf("entity","mapper","mapperXml","service","serviceImpl","controller","dto","vo")
        names.forEach { n ->
            val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
            if (inStream != null) {
                val target = templatesDir.resolve("$n.ftl")
                Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root.toFile())?.refresh(false, true)
    }
}

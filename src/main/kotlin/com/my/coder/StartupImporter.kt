package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path

/**
 * 项目启动后自动执行：复制插件内置模板到 my-easy-code/templates/general，
 * 并创建 my-easy-code/templates/enums 目录。
 */
class StartupImporter : StartupActivity {
    override fun runActivity(project: Project) {
        val base = project.basePath ?: return
        val root = Path.of(base).resolve("my-easy-code")
        val templatesDir = root.resolve("templates")
        val destGeneral = templatesDir.resolve("general")
        val enumsDir = templatesDir.resolve("enums")
        try { Files.createDirectories(destGeneral) } catch (_: Throwable) {}
        try { Files.createDirectories(enumsDir) } catch (_: Throwable) {}
        // 取消与 mapper 映射相关的初始化
        // 内置模板清单（按需扩展）
        val names = listOf("entity","mapper","mapperXml","service","serviceImpl","controller","dto","vo")
        names.forEach { n ->
            val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
            if (inStream != null) {
                val target = destGeneral.resolve("$n.ftl")
                Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        run {
            val enumStream = javaClass.classLoader.getResourceAsStream("templates/enum.ftl")
            if (enumStream != null) {
                val target = enumsDir.resolve("enum.ftl")
                Files.copy(enumStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root.toFile())?.refresh(false, true)
    }
}

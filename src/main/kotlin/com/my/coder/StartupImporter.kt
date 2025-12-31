package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

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
        val needImportGeneral = try {
            Files.list(destGeneral).use { s ->
                s.anyMatch { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
            }.not()
        } catch (_: Throwable) { true }
        if (needImportGeneral) {
            val names = listOf("entity","mapper","mapperXml","service","serviceImpl","controller","dto","vo")
            names.forEach { n ->
                val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
                if (inStream != null) {
                    val target = destGeneral.resolve("$n.ftl")
                    Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        run {
            val generalReadme = destGeneral.resolve("README.md")
            if (!Files.exists(generalReadme)) {
                val txt = """
                    # templates/general
                    
                    - 作用：存放普通代码模板（entity、service、serviceImpl、controller、mapper、mapperXml、dto、vo 等）
                    - 使用：将 .ftl 模板放入该目录，生成弹框的“模板选择”会自动加载
                    - 命名：建议与模板名称一致，例如 entity.ftl、service.ftl 等
                    - 变量：支持 ${'$'}{baseDir}、${'$'}{packagePath}、${'$'}{packageName}、${'$'}{entityName}、${'$'}{tableName} 等
                      详见项目根 README 的“模板支持的变量”
                    """.trimIndent()
                Files.write(generalReadme, txt.toByteArray(StandardCharsets.UTF_8))
            }
        }
        run {
            val enumStream = javaClass.classLoader.getResourceAsStream("templates/enum.ftl")
            if (enumStream != null) {
                val target = enumsDir.resolve("enum.ftl")
                if (!Files.exists(target)) Files.copy(enumStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        run {
            val enumsReadme = enumsDir.resolve("README.md")
            if (!Files.exists(enumsReadme)) {
                val txt = """
                    # templates/enums
                    
                    - 作用：存放枚举模板，供“枚举字段”面板选择生成枚举类
                    - 使用：将 .ftl 模板放入该目录，在“选择枚举模板”下拉中选择该模板
                    - 变量：enumPackage、enumName、tableName、columnName
                    - 输出：默认生成到 src/main/java/${'$'}{packagePath}/enums，可在枚举字段行“保存路径”覆盖
                    
                    ## 基础枚举模板示例
                    
                    ```ftl
                    package ${'$'}{enumPackage};
                    
                    public enum ${'$'}{enumName} {
                        ;
                    }
                    ```
                """.trimIndent()
                Files.write(enumsReadme, txt.toByteArray(StandardCharsets.UTF_8))
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root.toFile())?.refresh(false, true)
    }
}

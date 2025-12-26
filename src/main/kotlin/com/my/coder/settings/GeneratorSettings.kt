package com.my.coder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.my.coder.config.TemplateItem

@Service(Service.Level.PROJECT)
@State(name = "MyCoderGeneratorSettings", storages = [Storage("mycoder-generator.xml")])
class GeneratorSettings : PersistentStateComponent<GeneratorSettings.State> {
    class State {
        var packageName: String? = null
        var baseDir: String? = null
        var author: String? = null
        var yamlPath: String? = null
        var templateRoot: String? = null
        var dtoExclude: MutableList<String>? = null
        var voExclude: MutableList<String>? = null
        var activeScheme: String? = null
        var schemes: MutableList<TemplateScheme>? = null
        var generateMapper: Boolean = true
        var generateMapperXml: Boolean = true
        var useLombok: Boolean = true
        var applyMapperMapping: Boolean = true
        var excludeApplyBoth: Boolean = false
        var stripTablePrefix: String? = null
        var templateOutputs: MutableMap<String, String>? = mutableMapOf()
        var templateFileNames: MutableMap<String, String>? = mutableMapOf()
        var enumTemplateName: String? = null
    }

    class TemplateScheme {
        var name: String = "default"
        var templates: MutableList<TemplateItem> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(s: State) {
        XmlSerializerUtil.copyBean(s, state)
    }
}

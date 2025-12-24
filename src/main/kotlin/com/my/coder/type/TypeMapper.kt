package com.my.coder.type

object TypeMapper {
    fun toJavaType(typeName: String, size: Int, nullable: Boolean): String {
        val t = typeName.lowercase()
        return when {
            t.contains("char") || t.contains("text") || t == "json" -> "String"
            t == "bigint" -> "Long"
            t == "int" || t == "integer" || t == "mediumint" -> "Integer"
            t == "smallint" -> "Short"
            t == "tinyint" && size == 1 -> "Boolean"
            t == "tinyint" -> "Integer"
            t == "bit" -> "Boolean"
            t == "decimal" || t == "numeric" -> "java.math.BigDecimal"
            t == "double" -> "Double"
            t == "float" -> "Float"
            t == "date" -> "java.time.LocalDate"
            t == "time" -> "java.time.LocalTime"
            t == "datetime" || t == "timestamp" -> "java.time.LocalDateTime"
            t.contains("blob") || t.contains("binary") -> "byte[]"
            else -> "String"
        }
    }
}

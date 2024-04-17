package pt.isel

import java.io.Reader
import java.io.StringReader
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmName



abstract class AbstractYamlParser<T : Any>(val type: KClass<T>) : YamlParser<T> {
    /**
     * Used to get a parser for other Type using this same parsing approach.
     */
    abstract fun <T : Any> yamlParser(type: KClass<T>) : AbstractYamlParser<T>
    /**
     * Creates a new instance of T through the first constructor
     * that has all the mandatory parameters in the map and optional parameters for the rest.
     */
    abstract fun newInstance(args: Map<String, Any>): T


    final override fun parseObject(yaml: Reader): T {
        val paramMap = mutableMapOf<String, Any>()
        val yamlText = yaml.readLines().toMutableList()
        val yamlCopy = yamlText.toMutableList()
        val yamlMap = linkedMapOf<String, LinkedList<String>>()
        var fullText = ""
        yamlText.forEach { line ->
            if (line.isNotBlank()){
                fullText += line + "\n"
                val parts = line.split(":", limit = 2).map { it.trim() }
                if(parts.size == 2){
                    yamlMap.getOrPut(parts[0]) { LinkedList() }.add(parts[1])
                }
            }
        }
        val const = type.constructors.first()
        val yamlArgMap = mutableMapOf<String, String>()
        this.type.declaredMemberProperties.forEach { field ->
            val yamlArg =field.findAnnotation<YamlArg>()
            if (yamlArg != null){
                yamlArgMap[field.name] = yamlArg.name
            }
        }
        const?.parameters?.forEach { param ->
            if (yamlMap.containsKey(yamlArgMap[param.name]) || yamlMap.containsKey(param.name)){
                if (param.type.arguments.isNotEmpty()){
                    var reader = "-\n"
                    val listType = param.type.arguments.first.type!!.jvmErasure
                    if(listType.javaPrimitiveType == null && listType.simpleName != "String"){
                        repeat((yamlMap[yamlArgMap[listType.constructors.first().parameters.first().name]] ?:yamlMap[listType.constructors.first().parameters.first().name])!!.size){
                            listType.constructors.first().parameters.forEach { listParam ->
                                if(yamlMap[yamlArgMap[listParam.name]] != null  || yamlMap[listParam.name] != null) {
                                    reader += "  " + listParam.name + ": " + (yamlMap[yamlArgMap[listParam.name]]
                                        ?: yamlMap[listParam.name])!!.poll() + "\n"
                                }
                            }
                            reader += " -\n"
                        }
                    }
                    else{
                        val t = fullText.split("\n-")
                        val i = t.indexOfFirst { it.trim() == "-" }
                        while(t[i+2] == "-"){
                            reader += t[i] + "\n" +t[i+1]
                            i+2
                        }
                    }
                    paramMap[param.name!!] = YamlParserReflect.yamlParser(listType).parseList(reader.reader())
                    //paramMap[param.name!!] = emptyList<Any>()
                } else if (param.type.jvmErasure.simpleName != "String" && param.type.jvmErasure.javaPrimitiveType == null) {
                    var tmpYaml = ""
                    val converterType = param.annotations.find { it.annotationClass.simpleName == "YamlConvert" } as? YamlConvert
                    if (converterType != null) {
                        val converter = converterType.type.createInstance() as IYamlConvert
                        paramMap[param.name!!] = converter.convertYamlToObject(yamlMap[yamlArgMap[param.name] ?: param.name]!!.poll().toString())
                    } else {
                        param.type.jvmErasure.memberProperties.forEach { prop ->
                            if (yamlMap[yamlArgMap[prop.name]] != null || yamlMap[prop.name] != null) {
                                tmpYaml += "${prop.name} : ${(yamlMap[yamlArgMap[prop.name]] ?: yamlMap[prop.name])!!.poll()}\n"
                            }
                        }
                        paramMap[param.name!!] =
                            YamlParserReflect.yamlParser(param.type.jvmErasure).parseObject(tmpYaml.reader())
                    }
                } else {
                    paramMap[param.name!!] = (yamlMap[yamlArgMap[param.name]] ?: yamlMap[param.name!!])!!.poll()
                }
            }
        }
        return newInstance(paramMap)
    }

    private fun buildParamString(kParameter: KParameter, map: Map<String, LinkedList<String>>):String{
        var str = ""
        val mapCopy = map
        kParameter.type.jvmErasure.constructors.first().parameters.forEach {
            if(it.type.jvmErasure.javaPrimitiveType == null && it.type.jvmErasure.simpleName != "String"){
                str += it.name + ":\n" + buildParamString(it, mapCopy)
            }
            else{
                str +=  "   " +  it.name + ": " +mapCopy[it.name!!]!!.poll()
            }
        }
        return str
    }

    override fun parseList(yaml: Reader): List<T> {
        val textString = yaml.readLines().filter(String::isNotEmpty)
        val final = mutableListOf<T>()
        if(type.simpleName != "String" && type.javaPrimitiveType == null){
            val sep = textString.removeFirst().length+1
            var tmpObj = ""
            textString.forEach {
                if(it.trim() == "-" && it.length == sep && tmpObj != ""){
                    final.add(parseObject(tmpObj.reader()))
                    tmpObj = ""
                }
                else{
                    tmpObj += it + "\n"
                }
            }
            if(tmpObj != ""){
                final.add(parseObject(tmpObj.reader()))
            }
            if(type == List::class){//se tipo for lista dar parse a lista com o tipo sendo o tipo da lista que e tipo em type
                final.add(YamlParserReflect.yamlParser(type.primaryConstructor!!.parameters.first().type.arguments.first().type!!.jvmErasure).parseObject(tmpObj.reader()) as T)
            }
        }
        else{
            textString.forEach {
                val part = it.trim().removePrefix("-").trim()
                if(part != ""){
                    val t = when (type) {
                        Byte::class -> part.toByte() as T
                        Int::class -> part.toInt() as T
                        Long::class -> part.toLong() as T
                        Short::class -> part.toShort() as T
                        Boolean::class -> part.toBoolean() as T
                        Float::class -> part.toFloat() as T
                        Double::class -> part.toDouble() as T
                        Char::class -> part as Char as T
                        else -> part as T
                    }
                    final.add(t)
                }
            }
        }
        return final
    }

}

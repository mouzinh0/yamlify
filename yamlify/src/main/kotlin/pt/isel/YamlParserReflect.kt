package pt.isel

import org.yaml.snakeyaml.Yaml
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType


@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class YamlArg(val name : String)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class YamlConvert(val type : KClass<*>)

interface IYamlConvert {
    fun convertYamlToObject(yaml: String) : Any
}

/**
 * A YamlParser that uses reflection to parse objects.
 */
class YamlParserReflect<T : Any>(type: KClass<T>) : AbstractYamlParser<T>(type) {
    companion object {
        /**
         *Internal cache of YamlParserReflect instances.
         */
        private val yamlParsers: MutableMap<KClass<*>, YamlParserReflect<*>> = mutableMapOf()
        /**
         * Creates a YamlParser for the given type using reflection if it does not already exist.
         * Keep it in an internal cache of YamlParserReflect instances.
         */
        fun <T : Any> yamlParser(type: KClass<T>): AbstractYamlParser<T> {
            return yamlParsers.getOrPut(type) { YamlParserReflect(type) } as YamlParserReflect<T>
        }
    }
    /**
     * Used to get a parser for other Type using the same parsing approach.
     */
    override fun <T : Any> yamlParser(type: KClass<T>) = YamlParserReflect.yamlParser(type)
    /**
     * Creates a new instance of T through the first constructor
     * that has all the mandatory parameters in the map and optional parameters for the rest.
     */


    override fun newInstance(args: Map<String, Any>): T{
        val newArgs = mutableMapOf<String, Any?>()

        val const =  type.constructors.first()
        const.parameters.forEach {p->
            if (!args.containsKey(p.name)){
                if(p.isOptional){
                    if(p.type.isMarkedNullable){
                        newArgs[p.name!!] = null
                    }
                    else if(p.type.arguments.isNotEmpty()){
                        newArgs[p.name!!] = emptyList<Any>()
                    }
                }
            }
            else{
                val value = args[p.name]
                when(p.type.javaType.typeName){
                    "byte" -> newArgs[p.name!!] = value.toString().toByte()
                    "int" -> newArgs[p.name!!] = value.toString().toInt()
                    "long"-> newArgs[p.name!!] = value.toString().toLong()
                    "short" -> newArgs[p.name!!] = value.toString().toShort()
                    "boolean" -> newArgs[p.name!!] = value.toString().toBoolean()
                    "float" -> newArgs[p.name!!] = value.toString().toFloat()
                    "double" -> newArgs[p.name!!] = value.toString().toDouble()
                    "char"-> newArgs[p.name!!] = (value as Char)
                    else -> {
                        val converterType = p.annotations.find { it.annotationClass.simpleName == "YamlConvert" } as? YamlConvert
                        if (converterType != null) {
                            val converter = converterType.type.createInstance() as IYamlConvert
                            newArgs[p.name!!] = converter.convertYamlToObject(value.toString())
                        } else
                            newArgs[p.name!!] = value // For other types, use the value as is
                    }
                }
            }
        }
        val instance:T
        try{
            instance = const.call(*newArgs.values.toTypedArray())
        }
        catch (e: IllegalArgumentException){
            throw e
        }
        return instance
    }


}





package pt.isel

import org.cojen.maker.ClassMaker
import org.cojen.maker.MethodMaker
import org.cojen.maker.Variable
import java.lang.reflect.Parameter
import kotlin.reflect.KClass
/**
 * A YamlParser that uses Cojen Maker to generate a parser.
 */
open class YamlParserCojen<T : Any>(
    type: KClass<T>,
    private val nrOfInitArgs: Int)
    : AbstractYamlParser<T>(type) {

    companion object {
        private val yamlParsers: MutableMap<String, YamlParserCojen<*>> = mutableMapOf()

        private fun parserName(type: KClass<*>, nrOfInitArgs: Int): String {
            return "YamlParser${type.simpleName}$nrOfInitArgs"
        }
        /**
         * Creates a YamlParser for the given type using Cojen Maker if it does not already exist.
         * Keep it in an internal cache.
         */
        fun <T : Any> yamlParser(type: KClass<T>, nrOfInitArgs: Int = type.constructors.first().parameters.size): AbstractYamlParser<T> {
            return yamlParsers.getOrPut(parserName(type, nrOfInitArgs)) {
                YamlParserCojen(type, nrOfInitArgs)
                    .buildYamlParser()
                    .finish()
                    .getConstructor(KClass::class.java, Integer::class.java)
                    .newInstance(type, nrOfInitArgs) as YamlParserCojen<*>
            } as YamlParserCojen<T>
        }
    }
    /**
     * Used to get a parser for other Type using the same parsing approach.
     */
    override fun <T : Any> yamlParser(type: KClass<T>) = YamlParserCojen.yamlParser(type)

    /**
     * Do not change this method in YamlParserCojen.
     */
    override fun newInstance(args: Map<String, Any>): T {
        throw UnsupportedOperationException("This method is overridden in a subclass dynamically generated by buildYamlParser() function!")
    }

    private fun buildYamlParser() : ClassMaker {
        TODO()
    }
}


package pt.isel

import java.time.LocalDate

class StringToDate : IYamlConvert{
        override fun convertYamlToObject(yaml: String) : Any {
                val words = yaml.split("-")
                return LocalDate.of(words[0].toString().toInt(), words[1].toString().toInt(), words[2].toString().toInt())
        }
}

package uk.co.endofhome.skrooge.csvformatters

import java.nio.file.Path
import java.nio.file.Paths

interface StatementCsvFormatter {

    fun baseInputPath(): Path = Paths.get("input/raw")
    fun normalisedInputsPath(): Path = Paths.get("input/normalised")

    operator fun invoke(inputFileName: Path): List<String>

    fun String.capitalizeMerchant(): String {
        var specialCharacterDetected = false
        val numericCharacters = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val specialCharacters = listOf('\'', '&', '/', ' ', '*') + numericCharacters
        return this.map { char ->
            if (specialCharacters.contains(char)) {
                specialCharacterDetected = true
                char
            } else {
                if (specialCharacterDetected) {
                    specialCharacterDetected = false
                    char.toUpperCase()
                } else {
                    char
                }
            }
        }.joinToString("")
            .capitalizeUnlessWebAddress()
    }

    fun String.capitalizeUnlessWebAddress(): String =
        when {
            this.startsWith("www") -> this
            else -> this.capitalize()
        }

    fun specialMerchants(): Map<String, String> =
        mapOf(
            "B & Q" to "B&Q",
            "Easycar" to "Easycar",
            "Eft Payment" to "EFT Payment",
            "Lloyds Tsb Credit" to "Lloyds TSB Credit",
            "Sainsburys" to "Sainsburys",
            "Spotify" to "Spotify",
            "Tesco" to "Tesco",
            "Wickes" to "Wickes",
            "Zipcar" to "Zipcar"
        )

    fun String.modifyIfSpecialMerchant(): String =
        specialMerchants().let { merchants ->
            merchants.keys.find { specialKey -> this.contains(specialKey) }
                ?.let { specialKey -> merchants[specialKey] } ?: this
        }
}

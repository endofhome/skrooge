package uk.co.endofhome.skrooge

import java.nio.file.Path

interface StatementCsvFormatter {

    operator fun invoke(inputFileName: Path): List<String>

    fun String.capitalizeMerchant(): String {
        var specialCharacterDetected = false
        val numericCharacters = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val specialCharacters = listOf('\'', '&', '/', ' ', '*') + numericCharacters
        return this.map {
            when (specialCharacters.contains(it)) {
                true -> {
                    specialCharacterDetected = true
                    it
                }
                false -> {
                    if (specialCharacterDetected == true) {
                        specialCharacterDetected = false
                        it.toUpperCase()
                    } else it
                }
            }
        }.joinToString("").capitalizeUnlessWebAddress()
    }

    fun String.capitalizeUnlessWebAddress(): String {
        return when {
            this.startsWith("www") -> this
            else -> this.capitalize()
        }
    }

    fun specialMerchants(): Map<String, String> {
        return mapOf(
                "Tesco" to "Tesco",
                        "B & Q" to "B&Q",
                        "Spotify" to "Spotify",
                        "Wickes" to "Wickes",
                        "Eft Payment" to "EFT Payment",
                        "Lloyds Tsb Credit" to "Lloyds TSB Credit",
                        "Sainsburys" to "Sainsburys"
        )
    }

    fun String.modifyIfSpecialMerchant(): String {
        val match = BankFiveStatementCsvFormatter.specialMerchants().keys.find { this.contains(it) }
        return when {
            match.isNullOrEmpty() -> this
            else -> BankFiveStatementCsvFormatter.specialMerchants()[match]!!
        }
    }

}
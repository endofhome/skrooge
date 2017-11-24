package uk.co.endofhome.skrooge

interface StatementCsvFormatter {
    fun String.capitalizeMerchant(): String {
        var specialCharacterDetected = false
        val numericCharacters = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        val specialCharacters = listOf('\'', '&', '/', ' ') + numericCharacters
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
        }.joinToString("").capitalize()
    }
}
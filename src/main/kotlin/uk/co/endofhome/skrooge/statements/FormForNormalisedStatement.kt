package uk.co.endofhome.skrooge.statements

import org.http4k.core.Body
import org.http4k.core.FormFile
import org.http4k.core.Request
import org.http4k.lens.FormField
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.WebForm
import org.http4k.lens.multipartForm
import org.http4k.lens.webForm
import java.io.File
import java.nio.file.Path
import java.time.Month
import java.time.Year

data class FormForNormalisedStatement(val statementMetadata: StatementMetadata, val file: File) {
    companion object {
        private const val statementName = "statement-name"
        private const val statementFile = "statement-file"
        private const val statementFilePathKey = "statement-file-path"

        fun fromMultiPart(request: Request, normalisedStatements: Path): FormForNormalisedStatement {
            val multipartForm = extractMultiPartForm(request, statementName, statementFile)
            val fields = multipartForm.fields
            val files = multipartForm.files
            val (year, monthString, user, statement) = fields.values()
            val formFile = files[statementFile]?.firstOrNull()

            if (year != null && monthString != null && user != null && statement != null && formFile != null) {
                val month = Month.valueOf(monthString.toUpperCase())
                val statementMetadata = StatementMetadata(Year.parse(year), month, user, statement)
                val file = writeFileToFileSystem(statementMetadata, formFile, normalisedStatements)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                throw IllegalStateException(
                        """Form fields cannot be null, but were:
                            |year: $year
                            |month: $monthString
                            |user: $user
                            |statement: $statement
                            |formFile: $formFile
                        """.trimMargin()
                )
            }
        }

        fun fromUrlEncoded(request: Request): FormForNormalisedStatement {
            val form = extractUrlEncodedForm(request, statementFilePathKey)
            val (year, month, user, statement, statementFilePath) = form.fields.values()

            if (year != null && month != null && user != null && statement != null && statementFilePath != null) {
                val statementMetadata = StatementMetadata(Year.of(year.toInt()), Month.valueOf(month.toUpperCase()), user, statement)
                val file = File(statementFilePath)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                throw IllegalStateException(
                        """Form fields cannot be null, but were:
                            |year: $year
                            |month: $month
                            |user: $user
                            |statement: $statement
                            |statementFilePath: $statementFilePath
                        """.trimMargin()
                )
            }
        }

        private fun Map<String, List<String>>.values(): FieldValues {
            val year = this[StatementMetadata.yearName]?.firstOrNull()
            val monthString = this[StatementMetadata.monthName]?.firstOrNull()
            val user = this[StatementMetadata.userName]?.firstOrNull()
            val statement = this[statementName]?.firstOrNull()
            val statementFilePath = this[statementFilePathKey]?.firstOrNull()
            return FieldValues(year, monthString, user, statement, statementFilePath)
        }

        private fun extractMultiPartForm(request: Request, statementName: String, statementFile: String): MultipartForm {
            val yearLens = MultipartFormField.required(StatementMetadata.yearName)
            val monthLens = MultipartFormField.required(StatementMetadata.monthName)
            val userLens = MultipartFormField.required(StatementMetadata.userName)
            val statementNameLens = MultipartFormField.required(statementName)
            val statementFileLens = MultipartFormFile.required(statementFile)
            val multipartFormBody = Body.multipartForm(
                    Validator.Feedback,
                    yearLens,
                    monthLens,
                    userLens,
                    statementNameLens,
                    statementFileLens
            ).toLens()

            return multipartFormBody.extract(request)
        }

        private fun extractUrlEncodedForm(request: Request, statementFilePathKey: String): WebForm {
            val yearLens = FormField.required(StatementMetadata.yearName)
            val monthLens = FormField.required(StatementMetadata.monthName)
            val userLens = FormField.required(StatementMetadata.userName)
            val statementNameLens = FormField.required(StatementMetadata.statement)
            val statementPathLens = FormField.required(statementFilePathKey)
            val webForm = Body.webForm(
                    Validator.Feedback,
                    yearLens,
                    monthLens,
                    userLens,
                    statementNameLens,
                    statementPathLens
            )
            return webForm.toLens().extract(request)
        }

        private fun writeFileToFileSystem(statementMetadata: StatementMetadata, formFile: FormFile, normalisedStatements: Path): File {
            val (year, month, user, statement) = statementMetadata
            val fileBytes = formFile.content.use { inputStream ->
                inputStream.readBytes()
            }
            val file = File("$normalisedStatements/$year-${format(month)}_${user.capitalize()}_$statement.csv")
            file.writeBytes(fileBytes)
            return file
        }

        private fun format(month: Month) = month.value.toString().padStart(2, '0')

        data class FieldValues(val year: String?, val month: String?, val user: String?, val statement: String?, val statementFilePath: String?)
    }
}
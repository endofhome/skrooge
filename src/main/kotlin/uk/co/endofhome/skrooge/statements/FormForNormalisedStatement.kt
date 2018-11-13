package uk.co.endofhome.skrooge.statements

import org.http4k.core.Body
import org.http4k.core.FormFile
import org.http4k.core.Request
import org.http4k.lens.FormField
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statementName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.io.File
import java.nio.file.Path
import java.time.Month
import java.time.Year

data class FormForNormalisedStatement(val statementMetadata: StatementMetadata, val file: File) {
    companion object {
        fun fromMultiPart(request: Request, normalisedStatements: Path): FormForNormalisedStatement {
            val fieldLenses = listOf(
                MultipartFormField.required(yearName),
                MultipartFormField.required(monthName),
                MultipartFormField.required(userName),
                MultipartFormField.required(statementName)
            )
            val fileLens = MultipartFormFile.required(FileMetadata.statementFile)

            val multipartFormBody = Body.multipartForm(
                    Validator.Feedback,
                    *fieldLenses.toTypedArray(),
                    fileLens
            ).toLens()

            val multipartForm = multipartFormBody.extract(request)

            if (multipartForm.errors.isEmpty()) {
                val (year, month, user, statement) = fieldLenses.map { it.extract(multipartForm) }
                val statementMetadata = StatementMetadata(Year.parse(year), Month.valueOf(month.toUpperCase()), user, statement)
                val formFile = fileLens.extract(multipartForm)
                val file = writeFileToFileSystem(statementMetadata, formFile, normalisedStatements)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                val fieldsWithErrors = multipartForm.errors.map { it.meta.name }
                val osNewline = System.lineSeparator()
                throw IllegalStateException("Form fields were missing:$osNewline${fieldsWithErrors.joinToString(osNewline)}")
            }
        }

        fun fromUrlEncoded(request: Request): FormForNormalisedStatement {
            val fieldLenses = listOf(
                FormField.required(yearName),
                FormField.required(monthName),
                FormField.required(userName),
                FormField.required(statementName)
            ) + listOf(
                FormField.required(statementFilePathKey)
            )
            val webForm = Body.webForm(
                    Validator.Feedback,
                    *fieldLenses.toTypedArray()
            )
            val form = webForm.toLens().extract(request)

            if (form.errors.isEmpty()) {
                val (year, month, user, statement, statementFilePath) = fieldLenses.map { it.extract(form) }
                val statementMetadata = StatementMetadata(Year.of(year.toInt()), Month.valueOf(month.toUpperCase()), user, statement)

                return FormForNormalisedStatement(statementMetadata, File(statementFilePath))
            } else {
                val fieldsWithErrors = form.errors.map { it.meta.name }
                val osNewline = System.lineSeparator()
                throw IllegalStateException("Form fields were missing:$osNewline${fieldsWithErrors.joinToString(osNewline)}")
            }
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
    }
}
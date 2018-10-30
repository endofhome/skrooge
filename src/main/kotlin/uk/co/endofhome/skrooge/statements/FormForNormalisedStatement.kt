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
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFile
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.FileMetadata.statementName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.io.File
import java.nio.file.Path
import java.time.Month
import java.time.Year

data class FormForNormalisedStatement(val statementMetadata: StatementMetadata, val file: File) {
    companion object {
        fun fromMultiPart(request: Request, normalisedStatements: Path): FormForNormalisedStatement {
            val yearLens = MultipartFormField.required(yearName)
            val monthLens = MultipartFormField.required(monthName)
            val userLens = MultipartFormField.required(userName)
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
            val multipartForm = multipartFormBody.extract(request)

            if (multipartForm.errors.isEmpty()) {
                val year = yearLens.extract(multipartForm)
                val month = monthLens.extract(multipartForm)
                val user = userLens.extract(multipartForm)
                val statement = statementNameLens.extract(multipartForm)
                val formFile = statementFileLens.extract(multipartForm)

                val statementMetadata = StatementMetadata(Year.parse(year), Month.valueOf(month.toUpperCase()), user, statement)
                val file = writeFileToFileSystem(statementMetadata, formFile, normalisedStatements)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                val fieldsWithErrors = multipartForm.errors.map { it.meta.name }
                val osNewline = System.lineSeparator()
                throw IllegalStateException("Form fields were missing:$osNewline ${fieldsWithErrors.joinToString(osNewline)}")
            }
        }

        fun fromUrlEncoded(request: Request): FormForNormalisedStatement {
            val yearLens = FormField.required(yearName)
            val monthLens = FormField.required(monthName)
            val userLens = FormField.required(userName)
            val statementNameLens = FormField.required(statement)
            val statementPathLens = FormField.required(statementFilePathKey)
            val webForm = Body.webForm(
                    Validator.Feedback,
                    yearLens,
                    monthLens,
                    userLens,
                    statementNameLens,
                    statementPathLens
            )
            val form = webForm.toLens().extract(request)

            if (form.errors.isEmpty()) {
                val year = yearLens.extract(form)
                val month = monthLens.extract(form)
                val user = userLens.extract(form)
                val statement = statementNameLens.extract(form)
                val statementFilePath = statementPathLens.extract(form)

                val statementMetadata = StatementMetadata(Year.of(year.toInt()), Month.valueOf(month.toUpperCase()), user, statement)
                val file = File(statementFilePath)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                val fieldsWithErrors = form.errors.map { it.meta.name }
                val osNewline = System.lineSeparator()
                throw IllegalStateException("Form fields were missing:$osNewline ${fieldsWithErrors.joinToString(osNewline)}")
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
package setup.server

import spray.json._

package object implicits {
  val basicFormats = new BasicFormats {}
  import basicFormats._

  implicit object ImportedFileJsonWriter extends JsonWriter[ImportedFile] {
    override def write(obj: ImportedFile): JsValue =
      JsObject(
        "id" -> obj.id.toJson,
        "fileName" -> obj.fileName.toJson,
        "content" -> obj.content.toJson
      )
  }

  implicit object ImportedFileInputJsonReader
      extends JsonReader[ImportedFileInput] {
    override def read(json: JsValue): ImportedFileInput = {
      val fields = json.asJsObject.fields
      val fileName = fields("fileName") match {
        case JsString(value) => value
        case _ =>
          throw new DeserializationException(
            "Couldn't parse the value of field `fileName` since it is not a string."
          )
      }

      val content = fields("content") match {
        case JsString(value) => value
        case _ =>
          throw new DeserializationException(
            "Couldn't parse the value of field `content` since it is not a string."
          )
      }

      ImportedFileInput(fileName, content)
    }
  }

  implicit object MigrationInputJsonReader extends JsonReader[MigrationInput] {
    override def read(json: JsValue): MigrationInput = {
      val fields = json.asJsObject.fields
      val code = fields("code") match {
        case JsString(value) => value
        case _ =>
          throw new DeserializationException(
            "Couldn't parse the value of field `code` since it is not a string."
          )
      }
      val importedFiles = fields("importedFiles") match {
        case JsArray(values) =>
          values.map(_.convertTo[ImportedFileInput]).toList
        case _ =>
          throw new DeserializationException(
            "Couldn't parse the value of field `importedFiles` since it is not an array."
          )
      }

      MigrationInput(code, importedFiles)
    }
  }

  implicit object MigrationJsonWriter extends JsonWriter[Migration] {
    override def write(obj: Migration): JsValue =
      JsObject(
        "id" -> obj.id.toJson,
        "code" -> obj.code.toJson,
        "migrationTimestamp" -> obj.migrationTimestamp.toJson,
        "importedFiles" -> JsArray(obj.importedFiles.map(_.toJson).toVector)
      )
  }

  implicit object ProjectInputJsonReader extends JsonReader[ProjectInput] {
    override def read(json: JsValue): ProjectInput = {
      val fields = json.asJsObject.fields
      val name = fields
        .get("name")
        .map {
          case JsString(value) => value
          case _ =>
            throw new DeserializationException(
              "Couldn't parse the value of field `name` since it is not a string."
            )
        }

      val pgUser = fields.get("pgUser") match {
        case Some(JsString(value)) => value
        case Some(_) =>
          throw new DeserializationException(
            "Couldn't parse the value of field `pgUser` since it is not a string."
          )
        case None =>
          throw new DeserializationException(
            "Couldn't find field `pgUser`."
          )
      }

      val pgUri = fields.get("pgUri") match {
        case Some(JsString(value)) => value
        case Some(_) =>
          throw new DeserializationException(
            "Couldn't parse the value of field `pgUri` since it is not a string."
          )
        case None =>
          throw new DeserializationException(
            "Couldn't find field `pgUri`."
          )
      }

      val pgPassword = fields.get("pgPassword") match {
        case Some(JsString(value)) => value
        case Some(_) =>
          throw new DeserializationException(
            "Couldn't parse the value of field `pgPassword` since it is not a string."
          )
        case None =>
          throw new DeserializationException(
            "Couldn't find field `pgPassword`."
          )
      }

      val currentMigration = fields
        .get("currentMigration")
        .map(_.convertTo[MigrationInput])

      val migrationHistory = fields("migrationHistory") match {
        case JsArray(elements) =>
          elements.map(_.convertTo[MigrationInput]).toList
        case _ =>
          throw new DeserializationException(
            "Couldn't parse the value of field `migrationHistory` since it is not an array."
          )
      }

      ProjectInput(
        name,
        pgUri,
        pgUser,
        pgPassword,
        currentMigration,
        migrationHistory
      )
    }
  }

  implicit object ProjectJsonWriter extends JsonWriter[Project] {
    override def write(obj: Project): JsValue =
      JsObject(
        "id" -> obj.id.toJson,
        "name" -> (obj.name match {
          case Some(value) => value.toJson
          case None        => JsNull
        }),
        "currentMigration" -> (obj.currentMigration match {
          case Some(value) => value.toJson
          case None        => JsNull
        }),
        "migrationHistory" -> JsArray(
          obj.migrationHistory.map(_.toJson).toVector
        )
      )
  }
}

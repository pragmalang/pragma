package cli

import java.io._
import java.util.zip._
import scala.util._
import cats.implicits._
import java.util.Base64

object Zip {

  /** Recursively measures the size of a file/directory */
  def size(path: os.Path): Long = {
    if (os.isDir(path))
      os.walk(path)
        .filterNot(path => os.isDir(path) || os.isLink(path))
        .foldLeft(0L)((acc, filePath) => acc + os.size(filePath))
    else if (os.isLink(path)) {
      os.followLink(path) match {
        case Some(value) => size(value)
        case None        => 0
      }
    } else os.size(path)
  }

  /** Returns a base-64 encoded string containing the zipped directory */
  def zipDir(path: os.Path) =
    Try {
      if (!os.isDir(path))
        throw new Exception(
          s"Trying to zip non-directory '$path' which is not a directory"
        )
      val bos = new ByteArrayOutputStream(size(path).toInt)
      val zos = new ZipOutputStream(bos)
      val paths = os.walk(path)
      paths.filterNot(os.isDir).map(_.relativeTo(path)).map { child =>
        zos.putNextEntry(new ZipEntry(child.toString))
        val childContent = os.read(child.resolveFrom(path))
        zos.write(childContent.getBytes, 0, childContent.length)
        zos.closeEntry()
      }
      zos.close()
      new String(Base64.getEncoder.encode(bos.toByteArray))
    }.handleErrorWith {
      case e: Exception =>
        Failure(new Exception(s"Error while zipping $path\n${e.getMessage}"))
    }

}

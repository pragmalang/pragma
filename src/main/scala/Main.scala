import java.io.File
import scala.io.Source
import scala.util._
import domain.utils._
import org.parboiled2.Position
import domain._
import running.Server
import org.parboiled2.ParseError

object Main {
  def main(args: Array[String]): Unit = {
    val inputFile =
      if (args.length > 0)
        Try(Source.fromFile(new File(args(0))))
      else
        Try(Source.fromFile(new File("Pragmafile")))

    val code = inputFile.map { file =>
      val text = file.getLines.mkString("\n")
      file.close
      text
    }
    val syntaxTree = code.flatMap(SyntaxTree.from _)

    syntaxTree match {
      case Failure(userErr: UserError) =>
        userErr.errors foreach (err => printError(err._1, err._2))

      case Failure(ParseError(pos, _, _)) =>
        printError("Parse error", Some(PositionRange(pos, pos)))

      case Failure(otherErr) => printError(otherErr.getMessage, None)

      case Success(st) => new Server(st).main(args)
    }
  }

  lazy val errSep = Console.RED + ("━" * 100) + Console.RESET

  def printError(message: String, position: Option[PositionRange]) = {
    println(errSep)
    print("[error] ")
    println(message)
    position match {
      case Some(
          PositionRange(
            Position(_, line, char),
            Position(_, line2, char2)
          )
          ) => {
        print('\t')
        println(
          s"(at line $line character $char until line $line2 character $char2)"
        )
      }
      case _ => ()
    }
    println(errSep)
  }
}

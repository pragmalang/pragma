import java.io.File
import scala.io.Source
import scala.util._
import domain.utils._
import org.parboiled2.Position
import domain._
import running.Server

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
        userErr.errors foreach { err =>
          println(errSep)
          print("[error] ")
          println(err._1)
          err._2.foreach {
            case PositionRange(
                Position(_, line, char),
                Position(_, line2, char2)
                ) =>
              print('\t')
              println(
                s"(at line $line character $char until line $line2 character $char2)"
              )
          }
          println(errSep)
        }

      case Failure(otherErr) => {
        println(errSep)
        print("[error] ")
        println(otherErr.getMessage)
        println(errSep)
      }

      case Success(st) => new Server(st).main(args)
    }
  }

  lazy val errSep = Console.RED + ("━" * 80) + Console.RESET
}

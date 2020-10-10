import cats.implicits._, cats.effect._
import sys.process._

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] = prepareDaemon.as(ExitCode.Success)

  def prepareDaemon: IO[Unit] =
    for {
      _ <- IO("docker --help".!).handleErrorWith { _ =>
        new Exception(
          "Could not find Docker on the path. Please make sure it's installed before running Pragma"
        ).pure[IO]
      }
      _ <- IO {
        """ 
        | docker run --rm -d
        | --name openwhisk -h openwhisk
        | -p 3232:3232 -p 3233:3233
        | -v //var/run/docker.sock:/var/run/docker.sock
        | openwhisk/standalone:nightly
        """.stripMargin.!
      }.handleErrorWith { t =>
        new Exception("Could not start OpenWhisk:\n" + t.getMessage).pure[IO]
      }
      _ <- IO {
        "docker run --rm -d --name pragma_postgres -p 5433:5432 postgres:latest".!
      }.handleErrorWith { t =>
        new Exception("Could not start Postgres:\n" + t.getMessage).pure[IO]
      }
      _ <- IO("docker run --rm -d pragmad:latest".!)
        .handleErrorWith { t =>
          new Exception("Could not start Pragma daemon:\n" + t.getMessage)
            .pure[IO]
        }
    } yield ()

}

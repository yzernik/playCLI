package controllers

import play.api._
import play.api.mvc._

import play.api.libs.iteratee._

import play.api.Play.current
import play.api.libs.ws._

import cli.CLI

import sys.process._
import java.io._

import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  // Pipe examples
  val grep = (q: String) => CLI.pipe(Seq("grep", q), 64)
  val addEchoToOgg = CLI.pipe("sox -t ogg - -t ogg - echo 0.5 0.7 60 1")
  val scaleVideoHalf = CLI.pipe("ffmpeg -v warning -i pipe:0 -vf scale=iw/2:-1 -f avi pipe:1")

  // Consume an stream with url and push it in a socket with f
  def proxy (url: String)(f: Socket.Out[Array[Byte]] => Unit) = 
    (socket: Socket.Out[Array[Byte]]) => WS.url(url).withTimeout(-1).get(headers => f(socket))

  def index = Action(Ok(views.html.index()))

  // grep words
  def grepDictionary(search: String) = Action {
    val dictionary = Enumerator.fromFile(new File("/usr/share/dict/words"))
    Ok.stream(dictionary &> grep(search))
      .withHeaders(CONTENT_TYPE -> "text/plain")
  }

  // Re-stream a web radio by adding echo with sox
  def webRadioWithEcho = Action {
    val src = "http://radio.hbr1.com:19800/ambient.ogg"
    Ok.stream(proxy(src)(addEchoToOgg &> _))
      .withHeaders(CONTENT_TYPE -> "audio/ogg")
  }

  // Retrieve an online video, resize it, stream it
  def downloadReEncodeAndStreamVideo = Action {
    val src = "http://ftp.nluug.nl/pub/graphics/blender/demo/movies/Sintel.2010.1080p.mkv"
    Ok.stream(proxy(src)(scaleVideoHalf &> _))
      .withHeaders(CONTENT_TYPE -> "video/avi")
  }
  
  // Use a local video, resize it, stream it
  def reEncodeAndStreamVideo = Action {
    val stream = Enumerator.fromFile(Play.getFile("Sintel.2010.1080p.mkv")) // download it on sintel.org
    Ok.stream(proxy(src)(scaleVideoHalf &> _))
      .withHeaders(CONTENT_TYPE -> "video/avi")
  }

  // consume a ogg sound, add an echo effect and store in a /tmp/out.ogg file
  def audioEchoEffectGenerate = Action {
    val file = File.createTempFile("sample_with_echo_", ".ogg") // handle myself the output
    val enum = Enumerator.fromFile(Play.getFile("conf/exemple.ogg"))
    val addEchoToLocalOgg = CLI.consume(Process("sox -t ogg - -t ogg - echo 0.5 0.7 60 1") #> file)(_)
    AsyncResult {
      addEchoToLocalOgg(enum) map { _ =>
        Ok("'"+file.getAbsolutePath+"' file has been generated.")
      }
    }
  }

  // List all files in this Play project
  def find = Action {
    Ok.stream(CLI.enumerate("find .") >>> Enumerator.eof)
      .withHeaders(CONTENT_TYPE -> "text/plain")
  }
  
  // Retrieve a webpage and display it (of course, this is just for the demo, I won't use curl, prefer using WS)
  def curl = Action {
    Ok.stream(CLI.enumerate("curl -s http://blog.greweb.fr/") >>> Enumerator.eof)
      .withHeaders(CONTENT_TYPE -> "text/html")
  }
  
}

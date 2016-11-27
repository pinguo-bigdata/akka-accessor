package io.github.junheng.akka.accessor.access

import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, _}
import akka.stream.scaladsl.{Flow, Sink}
import com.typesafe.config.ConfigFactory
import akka.pattern._

import scala.concurrent.Future
import scala.language.postfixOps

class Accessor(address: String, port: Int, receiver: ActorSystem, plugins: List[AccessorPlugin] = Nil) extends Actor with ActorLogging {

  log.info("started, initializing http service...")
  private val serverSource = Http().bind(address, port)

  private val handler: HttpRequest => HttpResponse = {
    request: HttpRequest =>
      val entityOpt = if (plugins.forall(_.processRequest(request, receipt, log, receiver))) {
        process(request, receipt)
        HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("error"))
      } else {
        HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("error"))
      }
      entityOpt match {
        case Some(entity) => context.actorSelection(")").ask(entity)
        case None => caller ! new Exception("unsupported request")
      }
  }

  private val bindingFuture = serverSource.to(Sink.foreach { connection =>
    println("Accepted new connection from " + connection.remoteAddress)

    connection handleWith {
      Flow[HttpRequest] map handler
    }
  }).run()



  def process(request: HttpRequest, receipt: ActorRef): Option[MessageEntity] = {
    val path = request.uri.path.toString()
    if (request.uri.toString().matches("(.+)code=(.+)")) {
      val code = request.uri.query.toMap("code")
      val caller: AccessorCaller = new AccessorCaller(receipt, log)
      val entityOpt: Option[MessageEntity] = request.method match {
        case HttpMethods.POST =>
          val contentType = request.headers.find(_.name.toLowerCase == "content-type") match {
            case Some(header) => header.value.toLowerCase
            case None => "application/json"
          }
          contentType match {
            case "application/json" | "text/plain" => Some(JsonMessageEntity(code, request.entity.asString(HttpCharsets.`UTF-8`), caller))
            case _ => None
          }
        case HttpMethods.GET => Some(CodeMessageEntity(code, caller))
      }
    }
  }
}


object Accessor {
  val container = ActorSystem("accessor", ConfigFactory.parseString("akka {}"))

  def start(address: String, port: Int, plugins: List[AccessorPlugin] = Nil)(implicit receiver: ActorSystem) = {
    container.actorOf(Props(new Accessor(address, port, receiver, plugins)), "protocol")
  }

}

abstract class MessageEntity(code: String, caller: ActorRef)

case class CodeMessageEntity(code: String, caller: ActorRef) extends MessageEntity(code, caller)

case class JsonMessageEntity(code: String, payload: String, caller: ActorRef) extends MessageEntity(code, caller)

case class OctetMessageEntity(code: String, payload: Array[Byte], caller: ActorRef) extends MessageEntity(code, caller)

case class PartMessageEntity(code: String, payload: MultipartFormData, caller: ActorRef) extends MessageEntity(code, caller)
package org.eigengo.rsa.ingest.v100

import java.util.UUID

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord, KafkaSerializer}
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import org.apache.kafka.common.serialization.StringSerializer
import org.eigengo.rsa.Envelope

import scala.util.Try

object SimplifiedTweetProcessorActor {

  def props(config: Config): Props = {
    val producerConf = KafkaProducer.Conf(
      config.getConfig("tweet-image-producer"),
      new StringSerializer,
      KafkaSerializer[Envelope](_.toByteArray)
    )
    Props(classOf[SimplifiedTweetProcessorActor], producerConf)
  }
}

class SimplifiedTweetProcessorActor(producerConf: KafkaProducer.Conf[String, Envelope]) extends Actor {
  private[this] val producer = KafkaProducer(conf = producerConf)
  implicit val _ = ActorMaterializer()

  override def receive: Receive = {
    case SimplifiedTweet(handle, mediaUrls) ⇒
      mediaUrls.foreach { mediaUrl ⇒
        import context.dispatcher
        val request = HttpRequest(method = HttpMethods.GET, uri = Uri(mediaUrl))
        Http(context.system).singleRequest(request).foreach { response ⇒
          response.entity.dataBytes.runForeach { bs ⇒
            val _ = Try(producer.send(KafkaProducerRecord("tweet-image", handle,
              Envelope(version = 100,
                ingestionTimestamp = System.nanoTime(),
                processingTimestamp = System.nanoTime(),
                messageId = UUID.randomUUID().toString,
                correlationId = UUID.randomUUID().toString,
                payload = ByteString.copyFrom(bs.toArray)))))
          }
        }
      }
  }

}
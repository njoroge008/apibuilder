package controllers

import java.util.UUID

import com.gilt.apidoc.models.{Generator, Version}
import com.gilt.apidoc.models.json._

import com.gilt.apidoc.spec.models.{Service}
import com.gilt.apidoc.spec.models.json._

import com.gilt.apidoc.generator.Client
import com.gilt.apidoc.generator.models.InvocationForm

import core.ServiceBuilder
import db.{GeneratorsDao, Authorization, VersionsDao}
import lib.{Config, AppConfig, Validation}

import play.api.mvc._
import play.api.libs.json._

import scala.concurrent.Future

object Code extends Controller {

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  val apidocVersion = Config.requiredString("git.version")

  def getByOrgKeyAndApplicationKeyAndVersionAndGeneratorKey(
    orgKey: String,
    applicationKey: String,
    versionName: String,
    generatorKey: String
  ) = AnonymousRequest.async { request =>
    VersionsDao.findVersion(Authorization(request.user), orgKey, applicationKey, versionName) match {
      case None => {
        Future.successful(NotFound)
      }

      case Some(version) => {
        GeneratorsDao.findAll(user = request.user, key = Some(generatorKey)).headOption match {
          case None => {
            Future.successful(Conflict(Json.toJson(Validation.error(s"Generator with key[$generatorKey] not found"))))
          }

          case Some(generator: Generator) => {
            val userAgent = s"apidoc:$apidocVersion ${AppConfig.apidocWebHostname}/${orgKey}/${applicationKey}/${version.version}/${generator.key}"
            val service = Json.parse(version.service.toString).as[Service]

            new Client(generator.uri).invocations.postByKey(
              key = generator.key,
              invocationForm = InvocationForm(service = service, userAgent = Some(userAgent))
            ).map { invocation =>
              Ok(Json.toJson(com.gilt.apidoc.models.Code(generator, invocation.source)))
            }
          }
        }
      }
    }
  }

}

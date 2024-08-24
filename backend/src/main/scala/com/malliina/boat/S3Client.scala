package com.malliina.boat

import cats.effect.{Async, Resource, Sync}
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps}
import com.malliina.boat.S3Client.{asF, log}
import com.malliina.util.{AppLogger, FileUtils}
import com.malliina.values.ErrorMessage
import fs2.io.file.Path as FS2Path
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait FileStore[F[_]]:
  def upload(file: Path): F[PutObjectResponse]
  def files(): F[List[S3Object]]

opaque type BucketName = String
object BucketName:
  def make(s: String): BucketName = s
extension (n: BucketName) def name: String = n

opaque type S3Key = String
object S3Key:
  private val allowed = "^([a-zA-Z0-9]+[0-9a-zA-Z!_'()*.\\-]*)$".r

  def build(s: String): Either[ErrorMessage, S3Key] =
    if allowed.findFirstMatchIn(s).isDefined then Right(s)
    else Left(ErrorMessage(s"Invalid S3 key: '$s'."))

  extension (k: S3Key) def key: String = k

object S3Client:
  private val log = AppLogger(getClass)

  def build[F[_]: Async](
    bucket: BucketName = BucketName.make("agent.boat-tracker.com")
  ): Resource[F, S3Client[F]] =
    val creds = DefaultCredentialsProvider.builder().profileName("boat").build()
    val s3Client = Sync[F].delay(
      S3AsyncClient
        .builder()
        .region(Region.EU_WEST_1)
        .credentialsProvider(creds)
        .build()
    )
    Resource.make(s3Client)(c => Sync[F].delay(c.close())).map(c => S3Client(c, bucket))

  extension [T](cf: CompletableFuture[T])
    def asF[F[_]: Async]: F[T] = Async[F].async_ : cb =>
      cf.whenComplete((r, t) => Option(t).fold(cb(Right(r)))(t => cb(Left(t))))

class S3Client[F[_]: Async](client: S3AsyncClient, bucketName: BucketName) extends FileStore[F]:
  def download(key: S3Key): F[Option[FS2Path]] =
    val req = GetObjectRequest.builder().bucket(bucketName.name).key(key.key).build()
    val dest = FileUtils.tempDir.resolve(key.key)
    log.info(s"Downloading '$key' from '$bucketName' to '$dest'...")
    client
      .getObject(req, dest)
      .asF
      .map: res =>
        log.info(s"Downloaded '$key' from '$bucketName' to '$dest'.")
        Option(FS2Path.fromNioPath(dest))
      .handleError:
        case nske: NoSuchKeyException =>
          None
        case err =>
          log.warn(s"Failed to download '$key' from '$bucketName'.", err)
          None

  def upload(file: Path): F[PutObjectResponse] =
    val req =
      PutObjectRequest.builder().bucket(bucketName.name).key(file.getFileName.toString).build()
    client.putObject(req, file).asF

  def files(): F[List[S3Object]] =
    val req = ListObjectsRequest.builder().bucket(bucketName.name).build()
    client.listObjects(req).asF.map(_.contents.asScala.toList)

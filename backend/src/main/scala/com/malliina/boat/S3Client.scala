package com.malliina.boat

import cats.effect.{Async, Sync}
import cats.effect.kernel.Resource
import com.malliina.util.FileUtils

import java.nio.file.Path
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import cats.implicits.toFunctorOps
import java.util.concurrent.CompletableFuture
import scala.jdk.CollectionConverters.CollectionHasAsScala

extension [T](cf: CompletableFuture[T])
  def io[F[_]: Async]: F[T] = Async[F].async_ { cb =>
    cf.whenComplete((r, t) => Option(t).fold(cb(Right(r)))(t => cb(Left(t))))
  }

trait FileStore[F[_]]:
  def upload(file: Path): F[PutObjectResponse]
  def files(): F[ListObjectsResponse]

opaque type BucketName = String
object BucketName:
  def make(s: String): BucketName = s
extension (n: BucketName) def name: String = n

object S3Client:
  def build[F[_]: Async](
    bucket: BucketName = BucketName.make("agent.boat-tracker.com")
  ): Resource[F, S3Client[F]] =
    val creds = DefaultCredentialsProvider.builder().profileName("pimp").build()
    val s3Client = Sync[F].delay(
      S3AsyncClient
        .builder()
        .region(Region.EU_WEST_1)
        .credentialsProvider(creds)
        .build()
    )
    Resource.make(s3Client)(c => Sync[F].delay(c.close())).map(c => S3Client(c, bucket))

class S3Client[F[_]: Async](client: S3AsyncClient, bucketName: BucketName) extends FileStore[F]:
  def download(key: String): F[Path] =
    val req = GetObjectRequest.builder().bucket(bucketName.name).key(key).build()
    val dest = FileUtils.tempDir.resolve(key)
    client.getObject(req, dest).io.map(_ => dest)

  def upload(file: Path): F[PutObjectResponse] =
    val req =
      PutObjectRequest.builder().bucket(bucketName.name).key(file.getFileName.toString).build()
    client.putObject(req, file).io

  def files(): F[ListObjectsResponse] =
    // client.listObjects(bucketName).io.getObjectSummaries.asScala.toList
    ???

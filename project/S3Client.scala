import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*

import java.nio.file.Path

trait FileStore {
  def upload(file: Path): String
}

object S3Client extends FileStore {
  val bucketName = "agent.boat-tracker.com"
  val creds = DefaultCredentialsProvider.builder().profileName("pimp").build()
  val client = S3AsyncClient
    .builder()
    .region(Region.EU_WEST_1)
    .credentialsProvider(creds)
    .build()

  def upload(file: Path): String = {
    val key = file.getFileName.toString
    client
      .putObject(
        PutObjectRequest.builder().bucket(bucketName).key(key).build(),
        file
      )
      .get()
    key
  }
}

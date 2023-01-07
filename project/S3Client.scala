import java.nio.file.Path
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{GetObjectRequest, PutObjectRequest, S3Object, S3ObjectSummary}

import scala.collection.JavaConverters.asScalaBufferConverter

trait FileStore {
  def upload(file: Path): String
  def files(): Seq[S3ObjectSummary]
}

object S3Client extends FileStore {
  val bucketName = "agent.boat-tracker.com"

  val builder = AmazonS3ClientBuilder
    .standard()
    .withCredentials(
      new AWSCredentialsProviderChain(
        new ProfileCredentialsProvider("pimp"),
        DefaultAWSCredentialsProviderChain.getInstance()
      )
    )
  val aws = builder.withRegion(Regions.EU_WEST_1).build()

  def download(key: String): S3Object =
    aws.getObject(new GetObjectRequest(bucketName, key))

  def upload(file: Path): String = {
    aws.putObject(new PutObjectRequest(bucketName, file.getFileName.toString, file.toFile))
    file.getFileName.toString
  }

  def files(): Seq[S3ObjectSummary] =
    aws.listObjects(bucketName).getObjectSummaries.asScala.toList
}

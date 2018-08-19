package com.malliina.boat

import java.nio.file.Path

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import scala.collection.JavaConverters.asScalaBufferConverter

trait FileStore {
  def upload(file: Path): PutObjectResult

  def files(): Seq[S3ObjectSummary]
}

object S3Client {
  def apply(): S3Client = {
    val builder = AmazonS3ClientBuilder.standard().withCredentials(
      new AWSCredentialsProviderChain(
        new ProfileCredentialsProvider("pimp"),
        DefaultAWSCredentialsProviderChain.getInstance()
      )
    )
    new S3Client(builder.withRegion(Regions.EU_WEST_1).build(), "agent.boat-tracker.com")
  }
}

class S3Client(aws: AmazonS3, bucketName: String) extends FileStore {
  def download(key: String): S3Object = {
    aws.getObject(new GetObjectRequest(bucketName, key))
  }

  def upload(file: Path): PutObjectResult = {
    aws.putObject(new PutObjectRequest(bucketName, file.getFileName.toString, file.toFile))
  }

  def files(): Seq[S3ObjectSummary] = {
    aws.listObjects(bucketName).getObjectSummaries.asScala.toList
  }
}

package com.geeksville.andropilot.service

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.model.ProgressListener
import java.io.File
import com.amazonaws.services.s3.model.ProgressEvent
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.client.HttpResponseException
import scala.util.Random
import org.apache.http.HttpStatus
import org.apache.http.params.BasicHttpParams
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.PlainSocketFactory
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.params.HttpConnectionParams
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager

class DroneShareUpload(srcFile: File, val userId: String, val userPass: String)
  extends S3Upload("s3-droneshare", DroneShareUpload.createKey(), srcFile) {

  private val baseUrl = "http://upload.droneshare.com"
  // private val baseUrl = "http://192.168.0.93:8080"
  private val webAppUploadUrl = baseUrl + "/api/upload/froms3.json"

  var tlogId = "FIXME" // Need to use the server response

  /**
   * URL to see the webpage for this tlog
   */
  def viewURL = baseUrl + "/view/" + tlogId

  def kmzURL = baseUrl + "/api/tlog/" + tlogId + ".kmz"

  private def jsonToWebApp = """
	|{
    |  "key": "%s",
    |  "userId": "%s",
    |  "userPass": "%s"
	|}
  	""".stripMargin.format(keyName, userId, userPass)

  /**
   * Now tell our webapp
   */
  override protected def handleUploadCompleted() {
    try {
      tlogId = tellWebApp()
      println("WebApp responds: " + tlogId)

      handleWebAppCompleted()
    } catch {
      case ex: HttpResponseException if ex.getStatusCode == HttpStatus.SC_NOT_ACCEPTABLE =>
        handleUploadNotAccepted()
      case ex: Exception =>
        handleUploadFailed(Some(ex))
    }
  }

  /**
   * The webserver will send error code 406 if the file upload is considered unacceptably boring (flight too short)
   * Just tell the user something about that and do not treat it as an error
   */
  protected def handleUploadNotAccepted() {}

  /**
   * Show the user the view/download URL
   */
  protected def handleWebAppCompleted() {}

  def tellWebApp() = {
    //instantiates httpclient to make request

    //url with the post data
    val httpost = new HttpPost(webAppUploadUrl)

    println("Sending JSON: " + jsonToWebApp)

    //passes the results to a string builder/entity
    val se = new StringEntity(jsonToWebApp)

    //sets the post request as the resulting string
    httpost.setEntity(se)
    //sets a request header so the page receving the request
    //will know what to do with it
    httpost.setHeader("Accept", "application/json")
    httpost.setHeader("Content-type", "application/json")

    //Handles what is returned from the page 
    val responseHandler = new BasicResponseHandler()
    val resp = DroneShareUpload.httpclient.execute(httpost, responseHandler)

    // Skanky way to decode a json string
    if (resp.startsWith("\"") && resp.endsWith("\""))
      resp.substring(1, resp.length - 1)
    else
      throw new Exception("Malformed response")
  }
}

object DroneShareUpload {
  val httpclient = {
    // new DefaultHttpClient()
    //use following code to solve Adapter is detached error
    //refer: http://stackoverflow.com/questions/5317882/android-handling-back-button-during-asynctask
    val params = new BasicHttpParams()

    val schemeRegistry = new SchemeRegistry()
    schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
    val sslSocketFactory = SSLSocketFactory.getSocketFactory();
    schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));

    // Set the timeout in milliseconds until a connection is established.
    //HttpConnectionParams.setConnectionTimeout(params, CONNECTION_TIMEOUT);
    // Set the default socket timeout (SO_TIMEOUT) 
    // in milliseconds which is the timeout for waiting for data.
    //HttpConnectionParams.setSoTimeout(params, SOCKET_TIMEOUT);

    val cm = new ThreadSafeClientConnManager(params, schemeRegistry);
    new DefaultHttpClient(cm, params);
  }

  private val rand = new Random(System.currentTimeMillis)

  def createKey() = {
    "uploads/" + math.abs(rand.nextLong).toString + ".tlog"
  }
}

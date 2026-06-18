import javax.net.ssl.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.io.PrintWriter

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

// Helper method to bypass SSL validation (safeguard against expired certificates on the container / network proxies)
fun bypassSslValidation() {
  val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
    override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
  })

  val sc = SSLContext.getInstance("SSL")
  sc.init(null, trustAllCerts, SecureRandom())
  HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
  HttpsURLConnection.setDefaultHostnameVerifier(object : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean = true
  })
}

// Pure Kotlin multipart uploader
fun uploadMultipart(serviceUrl: String, fileField: String, apkFile: File): String {
  val boundary = "===Boundary" + System.currentTimeMillis() + "==="
  val url = URL(serviceUrl)
  val conn = url.openConnection() as HttpURLConnection
  conn.requestMethod = "POST"
  conn.doOutput = true
  conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
  conn.setRequestProperty("User-Agent", "Mozilla/5.0")
  
  conn.outputStream.use { output ->
    val writer = PrintWriter(output.writer())
    writer.print("--$boundary\r\n")
    writer.print("Content-Disposition: form-data; name=\"$fileField\"; filename=\"${apkFile.name}\"\r\n")
    writer.print("Content-Type: application/vnd.android.package-archive\r\n\r\n")
    writer.flush()
    
    apkFile.inputStream().use { input ->
      input.copyTo(output)
    }
    output.flush()
    
    writer.print("\r\n--$boundary--\r\n")
    writer.flush()
  }
  
  if (conn.responseCode in 200..299) {
    return conn.inputStream.bufferedReader().use { it.readText() }.trim()
  } else {
    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
    throw Exception("HTTP ${conn.responseCode}: $err")
  }
}

tasks.register("uploadApk") {
  doLast {
    bypassSslValidation()
    val apkFile = file("${rootDir}/ViewerApp.apk")
    if (!apkFile.exists()) {
      println("ERROR: ViewerApp.apk does not exist at ${apkFile.absolutePath}")
      return@doLast
    }
    val sizeText = "%.2f".format(apkFile.length() / (1024.0 * 1024.0))
    println("Uploading ViewerApp.apk ($sizeText MB)...")

    val resultFile = file("${rootDir}/upload_links.txt")
    resultFile.writeText("=== APK DOWNLOAD LINKS ===\n")

    // --- SERVICE 1: 0x0.st ---
    try {
      println("Attempting upload to 0x0.st...")
      val response = uploadMultipart("https://0x0.st", "file", apkFile)
      println("SUCCESS! 0x0.st URL: $response")
      resultFile.appendText("0x0.st: $response\n")
    } catch (e: Exception) {
      println("0x0.st upload failed: ${e.message}")
    }

    // --- SERVICE 2: tmpfiles.org ---
    try {
      println("Attempting upload to tmpfiles.org...")
      val response = uploadMultipart("https://tmpfiles.org/api/v1/upload", "file", apkFile)
      println("SUCCESS! tmpfiles.org Response: $response")
      if (response.contains("\"url\":\"")) {
        val extractedUrl = response.substringAfter("\"url\":\"").substringBefore("\"")
        println("tmpfiles.org view link: $extractedUrl")
        val directDownloadUrl = extractedUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
        println("tmpfiles.org DIRECT download link: $directDownloadUrl")
        resultFile.appendText("tmpfiles.org (View): $extractedUrl\n")
        resultFile.appendText("tmpfiles.org (Direct Download): $directDownloadUrl\n")
      }
    } catch (e: Exception) {
      println("tmpfiles.org upload failed: ${e.message}")
    }
  }
}


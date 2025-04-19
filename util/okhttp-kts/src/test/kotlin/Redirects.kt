import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import de.binarynoise.util.okhttp.createDummyResponse
import de.binarynoise.util.okhttp.getLocation
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.intellij.lang.annotations.Language

private val MEDIA_TYPE_HTML = "text/html".toMediaType()

class Redirects {
    
    @Test
    fun header() {
        val redirect = "https://www.hotsplots.de/auth/login.php?res=wispr&uamip=192.168.44.1&uamport=80&challenge=87be91e76d64c5e4a37ee507bf5fe561"
        val response = createDummyResponse().header("Location", redirect).build()
        val location = response.getLocation()
        assertEquals(redirect, location)
    }
    
    @Test
    @Ignore("Moved directly to the respective site")
    fun LoginURL() {
        val redirect = "https://www.hotsplots.de/auth/login.php?res=wispr&uamip=192.168.44.1&uamport=80&challenge=87be91e76d64c5e4a37ee507bf5fe561"
        
        @Language("HTML")
        val html = """
            <html>
            <head></head>
            <body>
            <h2>Browser error!</h2>Browser does not support redirects!
            <!--
            <?xml version="1.0" encoding="UTF-8"?>
            <WISPAccessGatewayParam
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:noNamespaceSchemaLocation="http://www.wballiance.net/wispr_2_0.xsd">
            <Redirect>
            <MessageType>100</MessageType>
            <ResponseCode>0</ResponseCode>
            <VersionHigh>2.0</VersionHigh>
            <VersionLow>1.0</VersionLow>
            <AccessProcedure>1.0</AccessProcedure>
            <AccessLocation>CDATA[[isocc=,cc=,ac=,network=HOTSPLOTS,]]</AccessLocation>
            <LocationName>CDATA[[colibri-00c03ac98d58]]</LocationName>
            <LoginURL>$redirect</LoginURL>
            <AbortLoginURL>http://192.168.44.1:80/abort</AbortLoginURL>
            <EAPMsg>AQEABQE=</EAPMsg>
            </Redirect>
            </WISPAccessGatewayParam>
            -->
            </body>
            </html>
        """.trimIndent()
        val response = createDummyResponse().body(html.toResponseBody("text/html".toMediaType())).build()
        assertEquals(redirect, response.getLocation())
    }
    
    @Test
    fun meta() {
        val redirect = "https://www.hotsplots.de/auth/login.php?res=wispr&uamip=192.168.44.1&uamport=80&challenge=87be91e76d64c5e4a37ee507bf5fe561"
        
        @Language("HTML")
        val html = """
            <html>
            <head>
                <meta http-equiv="refresh" content="3; URL=$redirect">
            </head>
            <body></body>
            </html>
        """.trimIndent()
        
        val response = createDummyResponse().body(html.toResponseBody("text/html".toMediaType())).build()
        assertEquals(redirect, response.getLocation())
    }
}

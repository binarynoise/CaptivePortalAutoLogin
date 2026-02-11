package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.asIterable
import de.binarynoise.liberator.cast
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import kotlin.random.Random as random

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "VIAS Free WiFi",
    "arverio_freewifi",
    "DonauparkCamping",
    "Regio-S-Bahn WLAN",
    "WestfalenBahn",
    "/WLAN@RB\\s[0-9]+/",
)
@Experimental
object Unwired : PortalLiberator {
    
    // fyi graphql playground: https://wasabi-splashpage.wifi.unwired.at/api/graphql
    const val GRAPHQL_URL = "/api/graphql"
    
    val supportedDomains = listOf(
        "wasabi-splashpage.wifi.unwired.at",
        "wasabi.hotspot-local.unwired.at",
    )
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host in supportedDomains
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        // f'ing graphql?
        // bro at least the websocket isn't relevant to the login flow...
        val user_session_id = response.requestUrl.queryParameter("user_session_id")
        
        val response1 = client.postJson(
            response.requestUrl,
            GRAPHQL_URL,
            JSONObject(
                mapOf(
                    "operationName" to "splashpage",
                    "variables" to JSONObject(
                        mapOf(
                            "user_session_id" to user_session_id,
                            "initial" to true,
                            "language" to "de",
                        ),
                    ),
                    "query" to GRAPHQL_SPLASHPAGE,
                ),
            ).toString(),
        )
        val json1 = JSONObject(response1.readText())
        
        val pages = json1.getJSONObject("data").getJSONObject("splashpage").getJSONArray("pages").asIterable()
        
        val widgets = pages.flatMap { it.cast<JSONObject>().getJSONArray("widgets") }
        
        val connectWidget =
            widgets.find { it.cast<JSONObject>().getString("__typename") == "ConnectWidget" } as? JSONObject
                ?: error("unable to find ConnectWidget widget")
        
        val widgetId = connectWidget.getString("widget_id")!!
        
        client.postJson(
            response.requestUrl,
            GRAPHQL_URL,
            JSONObject(
                mapOf(
                    "operationName" to "client_connect",
                    "variables" to JSONObject(
                        mapOf(
                            "userAgentLang" to "de",
                            "userAgentCountry" to "de",
                            "input" to null,
                            "userSessionId" to user_session_id, // api schema says only this parameter is mandatory
                            "widget_id" to widgetId,
                        ),
                    ),
                    "query" to GRAPHQL_CLIENT_CONNECT,
                ),
            ).toString(),
        ) {
            header("x-request-id", generateXRequestId())
        }.checkSuccess()
    }
    
    fun generateXRequestId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
        return String(CharArray(21) { chars[random.nextInt(chars.length)] })
    }
    
    val GRAPHQL_SPLASHPAGE = $$"""
        query splashpage($user_session_id: ID!, $language: String!, $initial: Boolean) {
          splashpage(
            user_session_id: $user_session_id
            language: $language
            initial: $initial
          ) {
            error {
              ...Error
              __typename
            }
            user_session_id
            connected
            online
            policy_violation {
              ...PolicyViolationError
              __typename
            }
            splashpage {
              ...Splashpage
              __typename
            }
            user_session_info {
              ...UserSessionInfo
              __typename
            }
            __typename
          }
        }
        
        fragment Splashpage on Splashpage {
          splashpage_id
          customer_id
          title
          color_primary
          color_secondary
          background_image
          default_lang
          supported_lang
          logo_image
          destination_url
          matomo_tracking_id
          pages {
            ...Page
            __typename
          }
          __typename
        }
        
        fragment Page on Page {
          page_id
          label
          route
          position
          widgets {
            ...Widget
            __typename
          }
          __typename
        }
        
        fragment Widget on Widget {
          widget_id
          page_id
          position
          date_updated
          ... on SimpleTextWidget {
            is_ready
            ...SimpleTextWidget
            __typename
          }
          ... on ConnectWidget {
            button_text
            connected_text
            variant
            confirmation
            delay
            require_sms_auth
            email_mandatory
            terms_of_service
            store_terms
            enable_anchor
            anchor {
              ...Anchor
              __typename
            }
            __typename
          }
          ... on JourneyInfoWidget {
            json
            enable_anchor
            anchor {
              ...Anchor
              __typename
            }
            variant
            is_ready
            hold_text
            __typename
          }
          ... on StructuredTextWidget {
            is_ready
            categories {
              ...StructuredTextCategory
              __typename
            }
            __typename
          }
          ... on SupportFormWidget {
            custom_options {
              option_key
              text
              email
              __typename
            }
            __typename
          }
          ... on Wifi4EUWidget {
            self_test
            network_identifier
            __typename
          }
          ... on EmergencyRequestWidget {
            reasons {
              reason
              __typename
            }
            disclaimer
            status
            __typename
          }
          ... on MovingMapWidget {
            is_ready
            icon
            geo_points {
              icon_width
              icon_url
              lat
              long
              text
              __typename
            }
            json
            __typename
          }
          ... on CampaignWidget {
            variant
            campaign_identifier
            __typename
          }
          __typename
        }
        
        fragment Anchor on Anchor {
          slug
          label
          __typename
        }
        
        fragment SimpleTextWidget on SimpleTextWidget {
          content
          enable_anchor
          anchor {
            ...Anchor
            __typename
          }
          __typename
        }
        
        fragment StructuredTextCategory on StructuredTextCategory {
          label
          entries {
            ...StructuredTextEntry
            __typename
          }
          enable_anchor
          anchor {
            ...Anchor
            __typename
          }
          __typename
        }
        
        fragment StructuredTextEntry on StructuredTextEntry {
          title
          content
          POI_match {
            ...PoiMatch
            __typename
          }
          tag
          attribution_image_url
          __typename
        }
        
        fragment PoiMatch on PoiMatch {
          stop {
            name
            id
            ds100
            ibnr
            __typename
          }
          __typename
        }
        
        fragment Error on Error {
          error_code
          error_message
          __typename
        }
        
        fragment PolicyViolationError on Error {
          error_code
          error_message
          current_value_bytes
          max_value_bytes
          current_value_seconds
          max_value_seconds
          __typename
        }
        
        fragment UserSessionInfo on UserSessionInfo {
          client_mac
          ap_mac
          ap_name
          user_session_id
          sync_user_session_id
          time_start
          state
          network_user_policy {
            network_user_policy_id
            max_mbytes_down
            max_mbytes_up
            daily_max_mbytes_down
            daily_max_mbytes_up
            max_bandwidth_down
            max_bandwidth_up
            max_pause_time
            accounting_interval
            timeout_session
            daily_max_session_time
            timeout_idle
            __typename
          }
          mbytes_down
          mbytes_up
          session_time
          daily_mbytes_down
          daily_mbytes_up
          daily_session_time
          __typename
        }
        """.trimIndent()
    
    val GRAPHQL_CLIENT_CONNECT = $$"""
        mutation client_connect($userSessionId: ID!, $userAgentLang: String, $userAgentCountry: String, $input: ConnectInput, $widget_id: ID!, $code: String) {
          client_connect(
            user_session_id: $userSessionId
            user_agent_lang: $userAgentLang
            user_agent_country: $userAgentCountry
            input: $input
            widget_id: $widget_id
            code: $code
          ) {
            user_session_id
            time_start
            state
            error {
              ...PolicyViolationError
              __typename
            }
            __typename
          }
        }

        fragment PolicyViolationError on Error {
          error_code
          error_message
          current_value_bytes
          max_value_bytes
          current_value_seconds
          max_value_seconds
          __typename
        }
        """.trimIndent()
}

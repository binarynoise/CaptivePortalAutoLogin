const port = browser.runtime.connectNative("browser")
const realLog = console.log

const routeToApp = true
const blockWs = true;

function postMessage(event, details = undefined) {
    if (routeToApp) {
        try {
            port.postMessage({ event: event, details: details })
        } catch (e) {
            realLog(e)
            realLog(event, details)
        }
    } else {
        realLog(event, details)
    }
}

console.log = function (...data) {
    if (routeToApp) {
        postMessage("log", data);
    } else {
        realLog(...data);
    }
}

console.log("starting captivePortalAutoLoginTrafficCapture...")

/**
 * RequestFilter
 * @type {RequestFilter}
 */
const filter = {
    urls: ["<all_urls>"], // "image", "sub_frame", "stylesheet", "script", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "beacon", "xml_dtd", "font", "media", "websocket", "csp_report", "imageset", "web_manifest", "speculative", "other",
    types: ["sub_frame", "script", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "speculative", "other", "websocket",],
};

let decoder = new TextDecoder();

/**
 * @param {string} requestId
 */
function setUpResponseBodyCollector(requestId) {
    let filter = browser.webRequest.filterResponseData(requestId)

    /**
     * @type {string}
     */
    let content = ""

    filter.ondata = (event) => {
        // console.log(`filter.ondata received ${event.data.byteLength} bytes`);

        let decoded = decoder.decode(event.data, { stream: true });
        content += decoded;
        // console.log(`filter.ondata decoded ${decoded.length} bytes`);

        filter.write(event.data);
    }
    filter.onstop = (_) => {
        filter.close();

        postMessage("filter.onStop", { requestId: requestId, content: content })
        content = "";
        console.log("filter.onstop");
    };

    console.log("set up filter");
}

browser.webRequest.onBeforeRequest.addListener(details => {
    postMessage("onBeforeRequest", details)
    setUpResponseBodyCollector(details.requestId);
}, filter, ["requestBody", "blocking"])

browser.webRequest.onBeforeSendHeaders.addListener(details => postMessage("onBeforeSendHeaders", details), filter, ["requestHeaders"])
browser.webRequest.onSendHeaders.addListener(details => postMessage("onSendHeaders", details), filter, ["requestHeaders"])

browser.webRequest.onHeadersReceived.addListener(details => postMessage("onHeadersReceived", details), filter, ["responseHeaders"])
browser.webRequest.onResponseStarted.addListener(details => postMessage("onResponseStarted", details), filter, ["responseHeaders"])
browser.webRequest.onCompleted.addListener(details => postMessage("onCompleted", details), filter, ["responseHeaders"])

browser.webRequest.onAuthRequired.addListener(details => postMessage("onAuthRequired", details), filter)
browser.webRequest.onBeforeRedirect.addListener(details => postMessage("onBeforeRedirect", details), filter)

browser.webRequest.onErrorOccurred.addListener(details => postMessage("onErrorOccurred", details), filter)

// browser.cookies.onChanged.addListener((details) => postMessage("onCookiesChanged", details))

if (blockWs) {
    /**
     * @type RequestFilter
     */
    const wsFilter = {
        urls: ["<all_urls>"], types: ["websocket"],
    };

    browser.webRequest.onBeforeRequest.addListener((details) => {
        /**
         * @type {browser.webRequest.BlockingResponse}
         */
        const response = {
            cancel: true
        };
        console.log("blocking ws request", details);

        return response;
    }, wsFilter, ["blocking"]);
}

console.log("started captivePortalAutoLoginTrafficCapture")

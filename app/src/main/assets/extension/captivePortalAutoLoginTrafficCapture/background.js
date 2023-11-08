const port = browser.runtime.connectNative("browser")
const realLog = console.log

function postMessage(event, details = undefined) {
    try {
        port.postMessage({ event: event, details: details })
    } catch (e) {
        realLog(e)
        realLog(event, details)
    }
}

console.log = function (...data) {
    postMessage("log", data)
}

console.log("starting captivePortalAutoLoginTrafficCapture...")

/**
 * RequestFilter
 * @type {RequestFilter}
 */
const filter = {
    urls: ["<all_urls>"], // "image", "sub_frame", "stylesheet", "script", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "beacon", "xml_dtd", "font", "media", "websocket", "csp_report", "imageset", "web_manifest", "speculative", "other",
    types: ["sub_frame", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "speculative", "other",]
};

let decoder = new TextDecoder();

postMessage("debug", { "filter": filter })

browser.webRequest.onBeforeRequest.addListener(details => {
    postMessage("onBeforeRequest", details)
    
    let filter = browser.webRequest.filterResponseData(details.requestId)
    
    /**
     * @type {string[]}
     */
    let content = []
    
    filter.ondata = (event) => {
        console.log(`filter.ondata received ${event.data.byteLength} bytes`);
        
        let decoded = decoder.decode(event.data, { stream: true });
        content.push(decoded);
        console.log(`filter.ondata decoded ${decoded.length} bytes`);
        
        filter.write(event.data);
    }
    filter.onstop = (_) => {
        filter.close();
        
        postMessage("filter.onStop", { requestId: details.requestId, content: content })
        content = [];
        console.log("filter.onstop");
    };
    
    console.log("set up filter");
}, filter, ["requestBody", "blocking"])

browser.webRequest.onBeforeSendHeaders.addListener(details => postMessage("onBeforeSendHeaders", details), filter, ["requestHeaders"],)
browser.webRequest.onHeadersReceived.addListener(details => postMessage("onHeadersReceived", details), filter)
browser.webRequest.onResponseStarted.addListener(details => postMessage("onResponseStarted", details), filter, ["responseHeaders"])
browser.webRequest.onCompleted.addListener(details => postMessage("onCompleted", details), filter)
browser.cookies.onChanged.addListener((details) => postMessage("onCookiesChanged", details))

console.log("started captivePortalAutoLoginTrafficCapture")

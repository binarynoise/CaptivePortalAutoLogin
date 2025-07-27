const port = browser.runtime.connectNative("browser");
const realLog = console.log;

const config = {
    routeToApp: false,
    stringify: false,
    blockWs: true,
};

port.onMessage.addListener((message) => {
    // ignore message if string
    if (typeof message === "string") {
        return;
    }

    function applyConfig(name, message) {
        let value = message.config[name];
        if (value !== undefined && typeof value === "boolean") {
            console.log("applying config", { name: name, value: value });
            config[name] = value;
        }
    }

    switch (message["event"]) {
        case "config":
            applyConfig("routeToApp", message);
            applyConfig("stringify", message);
            applyConfig("blockWs", message);
            break;
        default:
            console.log("unknown event", message["event"]);
            break;
    }
});

/**
 * Posts a message to the app if the routeToApp flag is false or logs the message
 *
 * @param {string} event - The event name.
 * @param {any} [details=undefined] - The details of the message.
 */
function postMessage(event, details = undefined) {
    if (config.stringify) {
        details = JSON.stringify(details);
    }
    if (config.routeToApp) {
        try {
            port.postMessage({ event: event, details: details });
        } catch (e) {
            realLog(e);
            realLog(event, details);
        }
    } else {
        realLog(event, details);
    }
}

/**
 * Redirects logs to the app if routeToApp is set
 *
 * @param {...any} data - The data to be logged.
 */
console.log = function (...data) {
    if (config.routeToApp) {
        postMessage("log", data);
    } else {
        realLog(...data);
    }
};

console.log("starting captivePortalAutoLoginTrafficCapture...");

/**
 * RequestFilter
 * @type {RequestFilter}
 */
const filter = {
    urls: ["<all_urls>"], // "image", "sub_frame", "stylesheet", "script", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "beacon", "xml_dtd", "font", "media", "websocket", "csp_report", "imageset", "web_manifest", "speculative", "other",
    types: ["sub_frame", "script", "main_frame", "object", "object_subrequest", "xmlhttprequest", "xslt", "ping", "speculative", "websocket", "other",],
};

let decoder = new TextDecoder();

/**
 * collects the response body of a web request.
 *
 * @param {string} requestId
 */
function setUpResponseBodyCollector(requestId) {
    let filter = browser.webRequest.filterResponseData(requestId);

    /**
     * @type {string}
     */
    let content = "";

    filter.ondata = (event) => {
        // console.log(`filter.ondata received ${event.data.byteLength} bytes`);

        let decoded = decoder.decode(event.data, { stream: true });
        content += decoded;
        // console.log(`filter.ondata decoded ${decoded.length} bytes`);

        filter.write(event.data);
    };
    filter.onstop = (_) => {
        filter.close();

        postMessage("filter.onStop", { requestId: requestId, content: content });
        content = "";
        console.log("filter.onstop");
    };

    console.log("filter.onstart");
}

browser.webRequest.onBeforeRequest.addListener(details => {
    let raw = details.requestBody?.raw;
    if (raw) {
        // raw is UploadData[]
        for (const uploadDatum of raw) {
            /**
             * @type {ArrayBuffer}
             */
            const buffer = uploadDatum.bytes;
            if (!buffer) continue;
            uploadDatum.bytes = Array.from(new Uint8Array(buffer));
            if (!config.routeToApp) {
                // decode text as UTF-8 for debugging
                uploadDatum.text = new TextDecoder("utf-8").decode(buffer);
            }
        }
    }
    postMessage("onBeforeRequest", details);
    setUpResponseBodyCollector(details.requestId);
}, filter, ["blocking", "requestBody"]);

browser.webRequest.onBeforeSendHeaders.addListener(details => postMessage("onBeforeSendHeaders", details), filter, ["requestHeaders"]);
browser.webRequest.onSendHeaders.addListener(details => postMessage("onSendHeaders", details), filter, ["requestHeaders"]);

browser.webRequest.onHeadersReceived.addListener(details => postMessage("onHeadersReceived", details), filter, ["responseHeaders"]);
browser.webRequest.onResponseStarted.addListener(details => postMessage("onResponseStarted", details), filter, ["responseHeaders"]);
browser.webRequest.onCompleted.addListener(details => postMessage("onCompleted", details), filter, ["responseHeaders"]);

browser.webRequest.onAuthRequired.addListener(details => postMessage("onAuthRequired", details), filter);
browser.webRequest.onBeforeRedirect.addListener(details => postMessage("onBeforeRedirect", details), filter);

browser.webRequest.onErrorOccurred.addListener(details => postMessage("onErrorOccurred", details), filter);

// browser.cookies.onChanged.addListener((details) => postMessage("onCookiesChanged", details))

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
        cancel: config.blockWs
    };
    console.log("blocking ws request", details);

    return response;
}, wsFilter, ["blocking"]);

console.log("started captivePortalAutoLoginTrafficCapture");

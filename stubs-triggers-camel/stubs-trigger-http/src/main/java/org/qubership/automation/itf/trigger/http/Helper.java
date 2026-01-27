/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.trigger.http;

import static org.apache.http.entity.ContentType.MULTIPART_FORM_DATA;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.StringSource;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.util.config.ApplicationConfig;
import org.qubership.automation.itf.core.util.feign.http.HttpClientFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Helper {

    /*  There is a problem when one tests ITF running on the localhost from the localhost too.
     *   For example, sends REST/SOAP-requests from Postman to local ITF.
     *   In that case, both methods .getRemoteAddr() and .getRemoteHost() return 0:0:0:0:0:0:0:1
     *   Why? Please see an explanation here: https://stackoverflow
     * .com/questions/17964297/using-request-getremoteaddr-returns-00000001
     *
     *   The problem is:
     *       If we use this address to send extra request (after normal response) to the address of the  sender,
     *       endpointUrl is, for example, http://0:0:0:0:0:0:0:1:9010/NokiaFO/Notification,
     *       and Camel software (Camel version is 2.17.0) creates Endpoint incorrectly.
     *       Endpoint points to URL: http://NokiaFO/Notification - it's definitely incorrect.
     *       So, exchange fails with 'invalid Endpoint' exception
     *
     *   I suppose (after consulting Roman Barmin) to: replace "0:0:0:0:0:0:0:1" to "localhost".
     */

    /**
     * TODO Add JavaDoc.
     */
    public static void addClientCoordsToHeaders(Map<String, Object> headers, ServletRequest servletRequest) {
        if (Objects.nonNull(servletRequest)) {
            String ipAddress = getCorrectedAddress(servletRequest.getRemoteAddr());
            headers.put("client", ipAddress); // Do NOT delete for backward compatibility
            headers.put("remoteAddr", ipAddress);
            headers.put("remoteHost", getCorrectedAddress(servletRequest.getRemoteHost()));
            headers.put("remotePort", servletRequest.getRemotePort());
            headers.put("protocol", servletRequest.getProtocol());
        }
    }

    /**
     *  Re-fill headers from servletRequest in case there are headers with the same name.
     *  It's not an ideal solution, but Camel misses such headers...
     */
    public static void fixCoNamedHeaders(Map<String, Object> headers, ServletRequest servletRequest) {
        if (Objects.nonNull(servletRequest)) {
            Enumeration<String> names = ((HttpServletRequest)servletRequest).getHeaderNames();
            while (names.hasMoreElements()) {
                String curName = names.nextElement();
                Enumeration<String> requestHeaders = ((HttpServletRequest) servletRequest).getHeaders(curName);
                List<String> list = new ArrayList<>();
                while (requestHeaders.hasMoreElements()) {
                    list.add(requestHeaders.nextElement());
                }
                if (list.size() > 1) {
                    headers.replace(curName, list);
                }
            }
        }
    }

    /**
     * TODO Add JavaDoc.
     */
    public static org.apache.camel.Message composeBodyForSoapOutbound(org.apache.camel.Message camelMessage,
                                                                      Message itfMessage) {
        turnOffTransferEncodingChunkedHeader(camelMessage);
        camelMessage.setBody(itfMessage.getText());
        return camelMessage;
    }

    /**
     * Compose message body for SOAP stub response.
     * Also: Content-Type header is parsed to set CamelCharsetName message property.
     */
    public static org.apache.camel.Message composeBodyForSoapInbound(org.apache.camel.Message camelMessage,
                                                                     Message itfMessage,
                                                                     boolean isRawDataformat) {
        parseAndSetContentType(camelMessage, camelMessage.getHeader(Exchange.CONTENT_TYPE).toString());
        turnOffTransferEncodingChunkedHeader(camelMessage);
        if (!StringUtils.isBlank(itfMessage.getText())) {
            if (isRawDataformat) {
                camelMessage.setBody(itfMessage.getText());
            } else {
                StringSource stringSource = new StringSource(itfMessage.getText());
                List<StringSource> list = new ArrayList<>();
                list.add(stringSource);
                CxfPayload cxp = new CxfPayload(null, list, null);
                camelMessage.setBody(cxp);
            }
        }
        return camelMessage;
    }

    /**
     * Compose message body for REST stub response.
     * Also: Content-Type header is parsed to set CamelCharsetName message property.
     */
    public static org.apache.camel.Message composeBodyForRest(org.apache.camel.Message camelMessage,
                                                              Message itfMessage) {
        String contentTypeString = camelMessage.getHeader(Exchange.CONTENT_TYPE).toString();
        ContentType contentType = parseAndSetContentType(camelMessage, contentTypeString);
        Object contentDisposition = camelMessage.getHeader("Content-Disposition");
        if (contentType.getMimeType().equals(MULTIPART_FORM_DATA.getMimeType())) {
            return composeMultipartBody(camelMessage, itfMessage, contentType, (String) contentDisposition);
        } else if (contentDisposition != null && (contentDisposition.toString().startsWith("attachment")
                || contentDisposition.toString().startsWith("inline"))) {
            return composeAttachmentsBody(camelMessage, itfMessage);
        } else if (contentTypeString.startsWith("application/graphql")) {
            return graphqlToJson(camelMessage, itfMessage, contentTypeString);
        } else {
            /* Default composition */
            turnOffTransferEncodingChunkedHeader(camelMessage);
            camelMessage.setBody(itfMessage.getText());
            return camelMessage;
        }
    }

    private static ContentType parseAndSetContentType(org.apache.camel.Message camelMessage,
                                                      String contentTypeString) {
        ContentType contentType = ContentType.parse(contentTypeString);
        if (contentType.getCharset() != null) {
            camelMessage.getExchange().setProperty("CamelCharsetName", contentType.getCharset().toString());
        }
        return contentType;
    }

    public static void clearForbiddenHeaders(HttpComponent httpComponent,
                                             DefaultHeaderFilterStrategy itfHeaderFilterStrategy) {
        httpComponent.setHeaderFilterStrategy(itfHeaderFilterStrategy);
    }

    /*  Compose multipart/form-data message,
     *   containing (currently) only one part - parsed template content
     * */
    private static org.apache.camel.Message composeMultipartBody(
            org.apache.camel.Message camelMessage,
            Message itfMessage,
            ContentType contentType,
            String contentDisposition) {
        if (camelMessage.getHeader("filename") == null && camelMessage.getHeader("partname") == null) {
            camelMessage.setBody(itfMessage.getText());
            return camelMessage;
        }
        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        multipartEntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        String boundary = contentType.getParameter("boundary");
        if (!StringUtils.isBlank(boundary)) {
            multipartEntityBuilder.setBoundary(boundary);
        }
        String filename;
        String partname;
        CloseableHttpResponse response = null;
        try {
            filename = (String) (camelMessage.getHeader("filename"));
            partname = (String) (camelMessage.getHeader("partname"));
            if (StringUtils.isBlank(partname)) {
                partname = "file";
            }
            /* Source variants:
                1. Source file is located anywhere in the web - if <filename> value is URL
                2. Source file is located in the filesystem - if <filename> value is NOT valid URL but not empty
                3. No source file; template is the source - if <filename> value is empty or null
            */
            if (StringUtils.isBlank(filename)) {
                String partFilename = getPartFilename(contentDisposition);
                File tmpfile = File.createTempFile(partFilename, ".tmp");
                tmpfile.deleteOnExit();
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(tmpfile), JvmSettings.CHARSET)) {
                    writer.write(itfMessage.getText());
                }
                multipartEntityBuilder.addPart(partname, new FileBody(tmpfile, contentType, partFilename));
            } else {
                try {
                    URL url = new URL(filename);
                    multipartEntityBuilder.addPart(partname, new ByteArrayBody(
                            camelMessage.getExchange().getContext().getTypeConverter().convertTo(
                                    byte[].class, getViaClient(url)
                            ), getPartFilename(contentDisposition)));
                } catch (MalformedURLException ex) {
                    multipartEntityBuilder.addPart(partname, new FileBody(new File(filename)));
                }
            }
            camelMessage.setBody(multipartEntityBuilder.build());
            return camelMessage;
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException("File exception while composing multipart message", ex);
        }
    }

    private static void turnOffTransferEncodingChunkedHeader(org.apache.camel.Message camelMessage) {
        /*
            To avoid EOFException or infinite looping while copying the empty input stream into output stream.
            See #1: ~apache-site/tomcat/blob/.../org/apache/tomcat/util/net/NioEndpoint.java#fillReadBuffer)
            See #2: org/apache/camel/http/common/DefaultHttpBinding.java#doWriteDirectResponse and #checkChunked
            (camel-http4-common-2.20.4)
            I think this header is 'technical' (or ~inner) and should not be copied into monitoring data
         */
        /*
            Also:
                To avoid duplicated headers Transfer-Encoding: chunked, sent in the ITF stub response
         */
        camelMessage.setHeader(Exchange.HTTP_CHUNKED, false);
    }

    private static String getPartFilename(String contentDisposition) {
        if (StringUtils.isBlank(contentDisposition)) {
            return "file";
        }
        int i = contentDisposition.indexOf("filename=\"");
        if (i < 0) {
            return "file";
        }
        return contentDisposition.substring(i + 10, contentDisposition.indexOf("\"", i + 10));
    }

    /*  Compose message with binary body,
     *   containing (currently) only one part - from file attachment
     * */
    private static org.apache.camel.Message composeAttachmentsBody(
            org.apache.camel.Message camelMessage,
            Message itfMessage) {
        String filename = (String) (camelMessage.getHeader("filename"));
        InputStream inputStream = null;
        try {
            URL url = new URL(filename);
            if (url.getProtocol().equals("https")) {
                inputStream = getViaClient(url);
            }
            camelMessage.addAttachment("fileAttachment", new DataHandler(new URLDataSource(url)));
        } catch (MalformedURLException ex) {
            camelMessage.addAttachment("fileAttachment", new DataHandler(new FileDataSource(filename)));
        } catch (Exception ex) {
            throw new RuntimeException("HTTPClient exception while composing attachments message", ex);
        }
        try {
            byte[] data = camelMessage.getExchange().getContext().getTypeConverter().convertTo(byte[].class,
                    (inputStream == null) ? camelMessage.getAttachment("fileAttachment").getInputStream() :
                            inputStream);
            camelMessage.setBody(data);
            return camelMessage;
        } catch (Exception ex) {
            throw new RuntimeException("File exception while composing attachments message", ex);
        }
    }

    private static String graphql2json(String graphqlString) {
        return new Gson().toJson(new GraphglQuery(graphqlString));
    }

    private static org.apache.camel.Message graphqlToJson(
            org.apache.camel.Message camelMessage,
            Message itfMessage,
            String sourceContentTypeString) {
        String targetContentTypeString = sourceContentTypeString
                .replace("application/graphql", "application/json");
        camelMessage.setHeader(Exchange.CONTENT_TYPE, targetContentTypeString);
        itfMessage.getHeaders().put(Exchange.CONTENT_TYPE, targetContentTypeString);
        camelMessage.setBody(graphql2json(itfMessage.getText()));
        return camelMessage;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Null check implemented")
    private static InputStream getViaClient(URL url) throws IOException, URISyntaxException {
        String datasetServiceUrl = ApplicationConfig.env.getProperty("dataset.service.url");
        if (datasetServiceUrl != null && url.toString().startsWith(datasetServiceUrl)) {
            String datasetsRoute = ApplicationConfig.env.getProperty("feign.atp.datasets.route");
            UUID dataSetUuid = UUID.fromString(url.getFile().replace("/" + datasetsRoute + "/attachment/",
                    StringUtils.EMPTY));
            ResponseEntity<Resource> responseEntity = HttpClientFactory.getDatasetsAttachmentFeignClient()
                    .getAttachmentByParameterId(dataSetUuid);

            if (!responseEntity.hasBody()) {
                throw new IOException(String.format("Response body is null for '%s', http status %s.",
                        url, responseEntity.getStatusCode()));
            }
            return Objects.requireNonNull(responseEntity.getBody()).getInputStream();
        }
        CloseableHttpClient client = PreconfiguredHttpClientHolder.get();
        HttpGet request = new HttpGet(url.toURI());
        CloseableHttpResponse response = client.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= HttpStatus.SC_MULTIPLE_CHOICES) {
            String body = StringUtils.EMPTY;
            if (response.getEntity() != null) {
                body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            }
            throw new IOException(String.format("Response is not accepted for '%s': %s [%s], body: %s",
                    url, response.getStatusLine().getReasonPhrase(), statusCode, body));
        }
        if (response.getEntity() == null) {
            throw new IOException(String.format("Response body is null for '%s': %s [%s]",
                    url, response.getStatusLine().getReasonPhrase(), statusCode));
        }
        return response.getEntity().getContent();
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static String getCorrectedAddress(String addr) {
        if (addr == null) {
            return null;
        }
        return addr.equals("0:0:0:0:0:0:0:1") ? "localhost" : addr;
    }
}

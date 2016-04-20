package org.connectus;

import android.content.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.jakewharton.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import okhttp3.*;
import okio.ByteString;
import org.connectus.model.AttachmentGmailHttpResponse;

import java.io.IOException;
import java.util.regex.Pattern;

public class PicassoBuilder {

    public static final String ACCESS_TOKEN_QUERY_PARAM = "accessToken";
    public static final String MIME_TYPE_QUERY_PARAM = "mimeType";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_HEADER_VALUE_PREFIX = "Bearer ";

    private Context context;
    private EnvironmentHelper environmentHelper;

    public PicassoBuilder(Context context, EnvironmentHelper environmentHelper) {
        this.context = context;
        this.environmentHelper = environmentHelper;
    }

    static boolean matchAttachmentRequest(String url) {
        Pattern pattern = Pattern.compile("https://www.googleapis.com/gmail/v1/users/[^/]+/messages/[^/]+/attachments/[^/]\\\\?+.*");
        return pattern.matcher(url).matches();
    }

    public Picasso build() {
        OkHttpClient client = buildClient();
        boolean loggingEnabled = environmentHelper.isNotReleaseBuildType();
        return new Picasso.Builder(context).loggingEnabled(loggingEnabled).indicatorsEnabled(loggingEnabled).downloader(new OkHttp3Downloader(client)).build();
    }

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            Request initialRequest = chain.request();
            String initialUrl = initialRequest.url().url().toString();
            Request request = initialRequest;
            if (matchAttachmentRequest(initialUrl)) {
                request = rewriteRequest(request);
            }
            Response response = chain.proceed(request);
            if (matchAttachmentRequest(initialUrl)) {
                response = rewriteResponse(initialRequest, response);
            }
            return response;
        }).build();
    }

    private Request rewriteRequest(Request request) {
        String accessToken = request.url().queryParameter(ACCESS_TOKEN_QUERY_PARAM);
        HttpUrl httpUrl = request.url().newBuilder().removeAllQueryParameters(ACCESS_TOKEN_QUERY_PARAM).removeAllQueryParameters(MIME_TYPE_QUERY_PARAM).build();
        return request.newBuilder().url(httpUrl).addHeader(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_VALUE_PREFIX + accessToken).build();
    }

    private Response rewriteResponse(Request request, Response response) {
        String bodyAsString = extractJsonStream(response);
        AttachmentGmailHttpResponse attachmentGmailHttpResponse = extractAttachmentHttpResponse(bodyAsString);
        ByteString byteString = ByteString.decodeBase64(attachmentGmailHttpResponse.getData());

        String mimeType = request.url().queryParameter(MIME_TYPE_QUERY_PARAM);
        MediaType mediaType = MediaType.parse(mimeType);
        byte[] content = byteString.toByteArray();
        ResponseBody responseBody = ResponseBody.create(mediaType, content);
        return response.newBuilder().body(responseBody).build();
    }

    private AttachmentGmailHttpResponse extractAttachmentHttpResponse(String bodyAsString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(bodyAsString, AttachmentGmailHttpResponse.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private String extractJsonStream(Response response) {
        try {
            return response.body().string();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}

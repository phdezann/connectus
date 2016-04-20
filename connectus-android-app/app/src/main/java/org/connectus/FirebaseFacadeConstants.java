package org.connectus;

public class FirebaseFacadeConstants {

    public static String OAUTH_GOOGLE_PROVIDER = "google";
    public static int SERVER_PROCESSING_TIMEOUT_IN_SECONDS = 10;
    public static int LOGIN_TIMEOUT_IN_SECONDS = 5;
    public static String LOGIN_CODE_SUCCESS = "SUCCESS";
    public static String LOGIN_CODE_INVALID_GRANT = "INVALID_GRANT";
    public static String ANDROID_ID_PATH = "android_id";
    public static String AUTHORIZATION_CODE_PATH = "authorization_code";
    public static String CODE_PATH = "code";
    public static String MESSAGE_PATH = "message";
    public static String URL_PATH = "url";
    public static String ACCESS_TOKEN_PATH = "accessToken";
    public static String MIME_TYPE_PATH = "mimeType";

    public static String RESIDENT_ID_PROPERTY = "id";
    public static String RESIDENT_NAME_PROPERTY = "name";
    public static String RESIDENT_LABEL_NAME_PROPERTY = "labelName";

    public static String getRootUrl() {
        return BuildConfig.FIREBASE_ROOT_URL;
    }

    public static String getPublishedVersionName() {
        return getRootUrl() + "/config/versionName";
    }

    public static String getAuthorizationCodesUrl() {
        return getRootUrl() + "/authorization_codes";
    }

    public static String getAuthorizationIdUrl(String authorizationId) {
        return String.format("%s/%s", getAuthorizationCodesUrl(), authorizationId);
    }

    public static String getReportUrl(String authorizationId) {
        return String.format("%s/trade_log", getAuthorizationIdUrl(authorizationId));
    }

    public static String getRefreshTokenUrl(String encodedEmail) {
        return String.format("%s/users/%s/refresh_token", getRootUrl(), encodedEmail);
    }

    public static String getAdminMessagesUrl(String encodedEmail) {
        return String.format("%s/messages/%s/admin/inbox", getRootUrl(), encodedEmail);
    }

    public static String getResidentMessagesUrl(String encodedEmail, String residentId) {
        return String.format("%s/messages/%s/%s/inbox", getRootUrl(), encodedEmail, residentId);
    }

    public static String getResidentMessagesOfThreadUrl(String encodedEmail, String residentId, String threadId) {
        return String.format("%s/messages/%s/%s/threads/%s", getRootUrl(), encodedEmail, residentId, threadId);
    }

    public static String getResidentsUrl(String encodedEmail) {
        return String.format("%s/residents/%s", getRootUrl(), encodedEmail);
    }

    public static String getContactsUrl(String encodedEmail) {
        return String.format("%s/contacts/%s", getRootUrl(), encodedEmail);
    }

    public static String getOutboxUrl(String encodedEmail) {
        return String.format("%s/outbox/%s", getRootUrl(), encodedEmail);
    }

    public static String getAttachmentRequestUrl(String encodedEmail) {
        return String.format("%s/attachments/%s", getRootUrl(), encodedEmail);
    }
}
